"""Comunitate: chatul general al Aradului (mesaje, anunțuri locale, evenimente), grupuri create de utilizatori
(vecini, cartier, bloc, școală…) cu cod de invitație, și conversații private între prieteni."""
import secrets
from datetime import datetime, timedelta

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.orm import Session, joinedload

from ..db import SessionLocal, get_db
from ..live import hub
from ..models import ChatMember, ChatMessage, ChatRoom, Friendship, User, utcnow
from ..security import get_current_user
from ..serializers import photo_url
from .friends import are_friends, is_blocked

router = APIRouter(prefix="/community", tags=["comunitate"])
TAGS = {"vecini": "Vecini", "cartier": "Cartier", "bloc": "Bloc / scară", "scoala": "Școală / clasă",
        "sport": "Sport", "altele": "Altele"}
GENERAL_NAME = "Comunitatea Aradului"


def ensure_general() -> None:
    """La pornire: există mereu camera generală, deschisă tuturor."""
    with SessionLocal() as db:
        if not db.scalar(select(ChatRoom.id).where(ChatRoom.kind == "general")):
            db.add(ChatRoom(kind="general", name=GENERAL_NAME,
                            description="Anunțuri locale, evenimente și discuții pentru toți arădenii."))
            db.commit()


def _general(db: Session) -> ChatRoom:
    room = db.scalar(select(ChatRoom).where(ChatRoom.kind == "general"))
    if not room:
        ensure_general()
        room = db.scalar(select(ChatRoom).where(ChatRoom.kind == "general"))
    return room


def _is_member(db: Session, room: ChatRoom, uid: int) -> bool:
    if room.kind == "general":
        return True
    return db.scalar(select(ChatMember.id).where(ChatMember.room_id == room.id, ChatMember.user_id == uid)) is not None


def _room_or_404(db: Session, room_id: int, me: User) -> ChatRoom:
    room = db.get(ChatRoom, room_id)
    if not room or not _is_member(db, room, me.id):
        raise HTTPException(404, "Conversația nu există sau nu faci parte din ea.")
    return room


def _room_out(db: Session, room: ChatRoom, me: User) -> dict:
    last = db.scalar(select(ChatMessage).where(ChatMessage.room_id == room.id).order_by(ChatMessage.id.desc()).limit(1))
    members = db.scalar(select(func.count()).select_from(ChatMember).where(ChatMember.room_id == room.id)) or 0
    name = room.name
    other = None
    if room.kind == "direct":   # numele conversației private = celălalt prieten
        other_id = db.scalar(select(ChatMember.user_id).where(ChatMember.room_id == room.id, ChatMember.user_id != me.id))
        other = db.get(User, other_id) if other_id else None
        name = f"@{other.name}" if other else "Conversație"
    return {"id": room.id, "kind": room.kind, "tag": room.tag, "tag_label": TAGS.get(room.tag, ""), "name": name,
            "description": room.description, "members": members if room.kind != "general" else None,
            "invite_code": room.invite_code if room.owner_id == me.id or room.kind == "group" else None,
            "is_owner": room.owner_id == me.id,
            "last_text": (last.event_title or last.text)[:80] if last else None,
            "last_at": last.created_at.isoformat() if last else None,
            "avatar_url": photo_url(other.avatar_path) if other else None}


def _msg_out(m: ChatMessage, me: User) -> dict:
    return {"id": m.id, "kind": m.kind, "text": m.text, "user_id": m.user_id, "user_name": m.user.name,
            "avatar_url": photo_url(m.user.avatar_path), "avatar_color": m.user.avatar_color, "mine": m.user_id == me.id,
            "event_title": m.event_title, "event_at": m.event_at.isoformat() if m.event_at else None,
            "event_place": m.event_place, "created_at": m.created_at.isoformat()}


@router.get("/rooms")
def rooms(me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    mine = db.scalars(select(ChatRoom).join(ChatMember, ChatMember.room_id == ChatRoom.id)
                      .where(ChatMember.user_id == me.id)).all()
    out = [_room_out(db, _general(db), me)] + [_room_out(db, r, me) for r in mine if r.kind != "general"]
    return {"rooms": out, "tags": [{"code": c, "label": l} for c, l in TAGS.items()]}


class RoomIn(BaseModel):
    name: str = Field(min_length=3, max_length=60)
    tag: str = "altele"
    description: str = Field("", max_length=200)


@router.post("/rooms", status_code=201)
def create_room(body: RoomIn, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if body.tag not in TAGS:
        raise HTTPException(400, "Tip de grup necunoscut.")
    room = ChatRoom(kind="group", tag=body.tag, name=body.name.strip(), description=body.description.strip(),
                    owner_id=me.id, invite_code=secrets.token_hex(3).upper())
    db.add(room)
    db.flush()
    db.add(ChatMember(room_id=room.id, user_id=me.id))
    db.commit()
    return _room_out(db, room, me)


class JoinIn(BaseModel):
    code: str


@router.post("/rooms/join")
def join(body: JoinIn, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    room = db.scalar(select(ChatRoom).where(ChatRoom.invite_code == body.code.strip().upper(), ChatRoom.kind == "group"))
    if not room:
        raise HTTPException(404, "Cod de invitație greșit.")
    if not _is_member(db, room, me.id):
        db.add(ChatMember(room_id=room.id, user_id=me.id))
        db.commit()
    return _room_out(db, room, me)


class AddIn(BaseModel):
    username: str


@router.post("/rooms/{room_id}/members")
def add_member(room_id: int, body: AddIn, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Adaugi un prieten în grup (doar prieteni, ca să nu poată fi adăugat oricine fără voie)."""
    room = _room_or_404(db, room_id, me)
    if room.kind != "group":
        raise HTTPException(400, "Poți adăuga oameni doar în grupuri.")
    other = db.scalar(select(User).where(func.lower(User.name) == body.username.strip().lstrip("@").lower()))
    if not other or not are_friends(db, me.id, other.id):
        raise HTTPException(400, "Poți adăuga doar prieteni. Ceilalți intră cu codul de invitație.")
    if not _is_member(db, room, other.id):
        db.add(ChatMember(room_id=room.id, user_id=other.id))
        db.commit()
    return {"detail": f"@{other.name} a fost adăugat în grup."}


@router.post("/rooms/{room_id}/leave")
def leave(room_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    room = _room_or_404(db, room_id, me)
    if room.kind == "general":
        raise HTTPException(400, "Comunitatea generală nu se poate părăsi.")
    db.query(ChatMember).filter(ChatMember.room_id == room.id, ChatMember.user_id == me.id).delete()
    db.commit()
    return {"detail": "Ai ieșit din conversație."}


@router.post("/direct/{friend_id}")
def direct(friend_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Deschide (sau creează) conversația privată cu un prieten."""
    if not are_friends(db, me.id, friend_id):
        raise HTTPException(403, "Poți scrie în privat doar prietenilor.")
    my_rooms = select(ChatMember.room_id).where(ChatMember.user_id == me.id)
    room = db.scalar(select(ChatRoom).join(ChatMember, ChatMember.room_id == ChatRoom.id)
                     .where(ChatRoom.kind == "direct", ChatMember.user_id == friend_id, ChatRoom.id.in_(my_rooms)))
    if not room:
        room = ChatRoom(kind="direct", name="direct")
        db.add(room)
        db.flush()
        db.add_all([ChatMember(room_id=room.id, user_id=me.id), ChatMember(room_id=room.id, user_id=friend_id)])
        db.commit()
    return _room_out(db, room, me)


@router.get("/rooms/{room_id}/messages")
def messages(room_id: int, after_id: int = 0, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    room = _room_or_404(db, room_id, me)
    q = (select(ChatMessage).options(joinedload(ChatMessage.user)).where(ChatMessage.room_id == room.id, ChatMessage.id > after_id)
         .order_by(ChatMessage.id.desc()).limit(100))
    rows = list(reversed(db.scalars(q).all()))
    # mesajele celor pe care i-am blocat nu le mai văd
    blocked = set(db.scalars(select(Friendship.addressee_id).where(Friendship.requester_id == me.id, Friendship.status == "blocked")))
    return [_msg_out(m, me) for m in rows if m.user_id not in blocked]


class MessageIn(BaseModel):
    text: str = Field(min_length=1, max_length=1000)
    kind: str = "text"                      # text | anunt | eveniment
    event_title: str | None = Field(None, max_length=100)
    event_at: datetime | None = None
    event_place: str | None = Field(None, max_length=120)
    event_lat: float | None = None
    event_lng: float | None = None


@router.post("/rooms/{room_id}/messages", status_code=201)
def send(room_id: int, body: MessageIn, background: BackgroundTasks, me: User = Depends(get_current_user),
         db: Session = Depends(get_db)):
    room = _room_or_404(db, room_id, me)
    if body.kind not in ("text", "anunt", "eveniment"):
        raise HTTPException(400, "Tip de mesaj necunoscut.")
    if body.kind == "eveniment" and not (body.event_title and body.event_at):
        raise HTTPException(400, "Un eveniment are nevoie de titlu și dată.")
    if room.kind == "direct":
        other = db.scalar(select(ChatMember.user_id).where(ChatMember.room_id == room.id, ChatMember.user_id != me.id))
        if other and (is_blocked(db, me.id, other) or not are_friends(db, me.id, other)):
            raise HTTPException(403, "Nu mai poți scrie în această conversație.")
    recent = db.scalar(select(func.count()).select_from(ChatMessage).where(
        ChatMessage.user_id == me.id, ChatMessage.created_at >= utcnow() - timedelta(seconds=10)))
    if recent >= 5:
        raise HTTPException(429, "Scrii prea repede. Mai așteaptă câteva secunde.")
    m = ChatMessage(room_id=room.id, user_id=me.id, kind=body.kind, text=body.text.strip(),
                    event_title=body.event_title, event_at=body.event_at, event_place=body.event_place,
                    event_lat=body.event_lat, event_lng=body.event_lng)
    db.add(m)
    db.commit()
    db.refresh(m)
    # anunțăm doar că a apărut un mesaj nou în camera X (fără conținut); aplicația îl cere apoi, dacă are acces
    background.add_task(hub.broadcast, {"type": "chat", "room_id": room.id})
    return _msg_out(m, me)


@router.get("/events")
def events(days: int = Query(30, ge=1, le=365), db: Session = Depends(get_db)):
    """Evenimentele anunțate în comunitatea generală (pentru calendar și pentru stratul de pe hartă)."""
    now = utcnow()
    rows = db.scalars(select(ChatMessage).options(joinedload(ChatMessage.user)).join(ChatRoom, ChatRoom.id == ChatMessage.room_id)
                      .where(ChatRoom.kind == "general", ChatMessage.kind == "eveniment",
                             ChatMessage.event_at >= now - timedelta(hours=6), ChatMessage.event_at <= now + timedelta(days=days))
                      .order_by(ChatMessage.event_at)).all()
    out = []
    for m in rows:
        lat, lng = m.event_lat, m.event_lng
        if lat is None and m.event_place:
            # evenimentul spune doar „unde” în cuvinte: îl punem pe hartă dacă locul e unul cunoscut
            hit = _place_by_name(db, m.event_place)
            if hit:
                lat, lng = hit.lat, hit.lng
        out.append({"id": m.id, "title": m.event_title, "text": m.text, "at": m.event_at.isoformat(), "place": m.event_place,
                    "lat": lat, "lng": lng, "by": m.user.name})
    return out


def _place_by_name(db: Session, text: str):
    from ..models import Place
    import unicodedata

    def n(s: str) -> str:
        s = unicodedata.normalize("NFKD", (s or "").lower())
        return "".join(c for c in s if not unicodedata.combining(c) and (c.isalnum() or c == " "))
    t = n(text)
    if len(t) < 3:
        return None
    best = None
    for p in db.scalars(select(Place)):
        pn = n(p.name)
        if pn and (pn in t or t in pn) and (not best or len(pn) > len(n(best.name))):
            best = p
    return best
