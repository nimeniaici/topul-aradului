import random
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, or_, and_, select
from sqlalchemy.orm import Session, joinedload

from .. import config, elo
from ..categories import label
from ..db import get_db
from ..gamification import XP_VOTE, award_badges
from ..models import Place, User, Vote, utcnow
from ..schemas import CategoryOut, DuelOut, VoteIn, VoteOut
from ..security import get_current_user
from ..serializers import badge_out, place_out

router = APIRouter(prefix="/duels", tags=["duels"])


def _eligible_categories(db: Session) -> dict[str, int]:
    rows = db.execute(select(Place.category, func.count()).group_by(Place.category)).all()
    return {c: n for c, n in rows if n >= 2}


@router.get("/categories", response_model=list[CategoryOut])
def duel_categories(db: Session = Depends(get_db)):
    return [CategoryOut(code=c, label=label(c), count=n) for c, n in _eligible_categories(db).items()]


@router.get("/next", response_model=DuelOut)
def next_duel(category: str | None = None, db: Session = Depends(get_db)):
    eligible = _eligible_categories(db)
    if not eligible:
        raise HTTPException(404, "Nu sunt destule locuri pentru dueluri.")
    if category is None:
        category = random.choice(list(eligible))
    elif category not in eligible:
        raise HTTPException(404, "Categoria nu are cel puțin 2 locuri.")
    places = db.scalars(select(Place).options(joinedload(Place.mayor)).where(Place.category == category)).all()
    a, b = random.sample(places, 2)
    return DuelOut(category=category, category_label=label(category), a=place_out(a), b=place_out(b))


@router.post("/vote", response_model=VoteOut)
def vote(body: VoteIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if body.winner_id == body.loser_id:
        raise HTTPException(400, "Alege două locuri diferite.")
    winner = db.get(Place, body.winner_id)
    loser = db.get(Place, body.loser_id)
    if not winner or not loser:
        raise HTTPException(404, "Locul nu există.")
    if winner.category != loser.category:
        raise HTTPException(400, "Locurile trebuie să fie din aceeași categorie.")

    since = utcnow() - timedelta(minutes=config.VOTE_COOLDOWN_MIN)
    pair = or_(
        and_(Vote.winner_id == winner.id, Vote.loser_id == loser.id),
        and_(Vote.winner_id == loser.id, Vote.loser_id == winner.id),
    )
    if db.scalar(select(Vote.id).where(Vote.user_id == user.id, pair, Vote.created_at >= since).limit(1)):
        raise HTTPException(429, "Ai votat deja acest duel recent.")

    winner.rating, loser.rating = elo.update(winner.rating, loser.rating, config.ELO_K)
    winner.duels += 1
    winner.wins += 1
    loser.duels += 1
    db.add(Vote(user_id=user.id, winner_id=winner.id, loser_id=loser.id, category=winner.category))
    user.xp += XP_VOTE
    db.flush()
    new_badges = award_badges(db, user)
    db.commit()
    return VoteOut(winner=place_out(winner), loser=place_out(loser), xp_gained=XP_VOTE,
                   new_badges=[badge_out(b) for b in new_badges])
