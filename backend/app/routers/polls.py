"""Sondaje: Primăria întreabă orașul (sondaje oficiale), iar arădenii pot face și ei sondaje în comunitate.
Un vot pe sondaj (se poate schimba cât e deschis); rezultatele se văd după ce votezi sau după închidere."""
from datetime import timedelta

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy import delete, func, select, update
from sqlalchemy.orm import Session

from ..db import get_db
from ..live import hub
from ..models import Poll, PollOption, PollVote, User, utcnow
from ..security import get_current_user
from .reports import require_primarie

router = APIRouter(prefix="/polls", tags=["sondaje"])
admin_router = APIRouter(prefix="/admin/polls", tags=["primarie"])
XP_VOTE = 2
XP_CREATE = 5
MAX_PER_DAY = 3


def _out(db: Session, p: Poll, me: User | None) -> dict:
    opts = db.scalars(select(PollOption).where(PollOption.poll_id == p.id).order_by(PollOption.position)).all()
    counts = dict(db.execute(select(PollVote.option_id, func.count()).where(PollVote.poll_id == p.id)
                             .group_by(PollVote.option_id)).all())
    total = sum(counts.values())
    mine = db.scalar(select(PollVote.option_id).where(PollVote.poll_id == p.id, PollVote.user_id == me.id)) if me else None
    closed = p.closes_at <= utcnow()
    show = mine is not None or closed
    return {"id": p.id, "question": p.question, "official": p.official,
            "author": "Primăria Arad" if p.official else (p.user.name if p.user else "arădean"),
            "mine": bool(me and p.user_id == me.id), "closes_at": p.closes_at.isoformat(), "closed": closed,
            "total_votes": total, "my_vote": mine, "results_visible": show,
            "options": [{"id": o.id, "text": o.text, "votes": counts.get(o.id, 0) if show else None,
                         "percent": round(100 * counts.get(o.id, 0) / total) if show and total else (0 if show else None)}
                        for o in opts]}


@router.get("")
def list_polls(include_closed: bool = Query(True), me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    q = select(Poll).order_by(Poll.official.desc(), Poll.created_at.desc()).limit(50)
    if not include_closed:
        q = q.where(Poll.closes_at > utcnow())
    rows = db.scalars(q).all()
    # deschise primele, apoi cele închise recent
    rows = sorted(rows, key=lambda p: (p.closes_at <= utcnow(), not p.official, -p.id))
    return [_out(db, p, me) for p in rows]


class PollIn(BaseModel):
    question: str = Field(min_length=5, max_length=200)
    options: list[str] = Field(min_length=2, max_length=6)
    days: int = Field(7, ge=1, le=30)


def _create(db: Session, body: PollIn, user_id: int | None, official: bool) -> Poll:
    opts = [o.strip() for o in body.options if o.strip()]
    if len(opts) < 2 or len(set(o.lower() for o in opts)) != len(opts):
        raise HTTPException(400, "Pune cel puțin două variante diferite.")
    if any(len(o) > 100 for o in opts):
        raise HTTPException(400, "O variantă are cel mult 100 de caractere.")
    p = Poll(user_id=user_id, question=body.question.strip(), official=official,
             closes_at=utcnow() + timedelta(days=body.days))
    db.add(p)
    db.flush()
    db.add_all([PollOption(poll_id=p.id, text=t, position=i) for i, t in enumerate(opts)])
    return p


@router.post("", status_code=201)
def create(body: PollIn, background: BackgroundTasks, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    today = db.scalar(select(func.count()).select_from(Poll).where(
        Poll.user_id == me.id, Poll.created_at >= utcnow() - timedelta(days=1))) or 0
    if today >= MAX_PER_DAY:
        raise HTTPException(429, f"Poți face cel mult {MAX_PER_DAY} sondaje pe zi.")
    p = _create(db, body, me.id, False)
    db.execute(update(User).where(User.id == me.id).values(xp=User.xp + XP_CREATE))
    db.commit()
    background.add_task(hub.broadcast, {"type": "poll", "place_name": f"Sondaj nou: {p.question[:60]}", "user_name": me.name})
    return _out(db, p, me)


class VoteIn(BaseModel):
    option_id: int


@router.post("/{poll_id}/vote")
def vote(poll_id: int, body: VoteIn, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    p = db.get(Poll, poll_id)
    if not p:
        raise HTTPException(404, "Sondajul nu există.")
    if p.closes_at <= utcnow():
        raise HTTPException(400, "Sondajul s-a închis.")
    opt = db.get(PollOption, body.option_id)
    if not opt or opt.poll_id != p.id:
        raise HTTPException(400, "Variantă necunoscută.")
    v = db.scalar(select(PollVote).where(PollVote.poll_id == p.id, PollVote.user_id == me.id))
    if v:
        v.option_id = opt.id          # își poate schimba votul cât e deschis
    else:
        db.add(PollVote(poll_id=p.id, option_id=opt.id, user_id=me.id))
        db.execute(update(User).where(User.id == me.id).values(xp=User.xp + XP_VOTE))
    db.commit()
    return _out(db, p, me)


@router.delete("/{poll_id}", status_code=204)
def remove(poll_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    p = db.get(Poll, poll_id)
    if not p or p.user_id != me.id:
        raise HTTPException(404, "Sondajul nu există.")
    db.execute(delete(PollVote).where(PollVote.poll_id == p.id))
    db.execute(delete(PollOption).where(PollOption.poll_id == p.id))
    db.delete(p)
    db.commit()


# ---------- sondaje oficiale (Primăria, cu cheia din panou) ----------
@admin_router.post("", status_code=201, dependencies=[Depends(require_primarie)])
def create_official(body: PollIn, background: BackgroundTasks, db: Session = Depends(get_db)):
    p = _create(db, body, None, True)
    db.commit()
    background.add_task(hub.broadcast, {"type": "poll", "place_name": f"Primăria întreabă: {p.question[:60]}",
                                        "user_name": "Primăria Arad"})
    return _out(db, p, None)


@admin_router.get("", dependencies=[Depends(require_primarie)])
def list_official(db: Session = Depends(get_db)):
    return [_out(db, p, None) | {"options_results": [
        {"text": o.text, "votes": db.scalar(select(func.count()).select_from(PollVote).where(PollVote.option_id == o.id))}
        for o in db.scalars(select(PollOption).where(PollOption.poll_id == p.id).order_by(PollOption.position))]}
        for p in db.scalars(select(Poll).order_by(Poll.created_at.desc()).limit(100))]
