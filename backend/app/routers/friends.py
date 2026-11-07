"""Prieteni: cereri, acceptare, ștergere, blocare; poziția pe hartă (doar dacă o pornești) și locurile cucerite.

Intimitate: poziția se trimite doar dacă utilizatorul pornește „Arată-mă prietenilor”, o văd doar prietenii acceptați,
iar după 30 de minute fără actualizare nu mai apare. La oprire, poziția se șterge."""
from datetime import timedelta

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import and_, delete, func, or_, select
from sqlalchemy.orm import Session

from ..db import get_db
from ..gamification import level
from ..live import hub
from ..models import Friendship, Place, SharedLocation, User, utcnow
from ..security import get_current_user
from ..serializers import photo_url

router = APIRouter(tags=["prieteni"])
LOCATION_TTL = timedelta(minutes=30)


def _pair(db: Session, a: int, b: int) -> Friendship | None:
    return db.scalar(select(Friendship).where(or_(
        and_(Friendship.requester_id == a, Friendship.addressee_id == b),
        and_(Friendship.requester_id == b, Friendship.addressee_id == a))))


def are_friends(db: Session, a: int, b: int) -> bool:
    f = _pair(db, a, b)
    return bool(f and f.status == "accepted")


def is_blocked(db: Session, a: int, b: int) -> bool:
    f = _pair(db, a, b)
    return bool(f and f.status == "blocked")


def _user_card(db: Session, u: User) -> dict:
    mayor_count = db.scalar(select(func.count()).select_from(Place).where(Place.mayor_id == u.id)) or 0
    return {"id": u.id, "name": u.name, "level": level(u.xp), "xp": u.xp, "avatar_emoji": u.avatar_emoji,
            "avatar_color": u.avatar_color, "avatar_url": photo_url(u.avatar_path), "thrones": mayor_count}


@router.get("/users/search")
def search(q: str = Query(min_length=2, max_length=20), me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    q = q.strip().lstrip("@").lower()
    rows = db.scalars(select(User).where(func.lower(User.name).contains(q), User.id != me.id).limit(15)).all()
    return [_user_card(db, u) for u in rows if not is_blocked(db, me.id, u.id)]


@router.get("/friends")
def my_friends(me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.scalars(select(Friendship).where(or_(Friendship.requester_id == me.id, Friendship.addressee_id == me.id))).all()
    out = {"friends": [], "incoming": [], "outgoing": [], "blocked": []}
    for f in rows:
        other_id = f.addressee_id if f.requester_id == me.id else f.requester_id
        other = db.get(User, other_id)
        if not other:
            continue
        card = _user_card(db, other)
        if f.status == "accepted":
            out["friends"].append(card)
        elif f.status == "pending":
            out["incoming" if f.addressee_id == me.id else "outgoing"].append(card)
        elif f.status == "blocked" and f.requester_id == me.id:
            out["blocked"].append(card)   # cei blocați de altcineva nu află asta
    loc = db.get(SharedLocation, me.id)
    out["sharing"] = bool(loc and loc.sharing)
    return out


class RequestIn(BaseModel):
    username: str


@router.post("/friends/request", status_code=201)
def request(body: RequestIn, background: BackgroundTasks, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    other = db.scalar(select(User).where(func.lower(User.name) == body.username.strip().lstrip("@").lower()))
    if not other or other.id == me.id:
        raise HTTPException(404, "Nu am găsit acest username.")
    f = _pair(db, me.id, other.id)
    if f:
        if f.status == "blocked":
            raise HTTPException(404, "Nu am găsit acest username.")   # nu dezvăluim blocarea
        if f.status == "accepted":
            return {"detail": f"Ești deja prieten cu @{other.name}."}
        if f.addressee_id == me.id:   # el îmi trimisese deja o cerere: o accept
            f.status = "accepted"
            db.commit()
            return {"detail": f"Acum ești prieten cu @{other.name}!"}
        return {"detail": "Cererea a fost deja trimisă."}
    db.add(Friendship(requester_id=me.id, addressee_id=other.id))
    db.commit()
    background.add_task(hub.broadcast, {"type": "friend_request", "user_id": other.id, "user_name": me.name})
    return {"detail": f"Cerere trimisă către @{other.name}."}


def _get_pair_or_404(db: Session, me: User, other_id: int) -> Friendship:
    f = _pair(db, me.id, other_id)
    if not f:
        raise HTTPException(404, "Nu există o relație cu acest utilizator.")
    return f


@router.post("/friends/{other_id}/accept")
def accept(other_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    f = _get_pair_or_404(db, me, other_id)
    if f.status != "pending" or f.addressee_id != me.id:
        raise HTTPException(400, "Nu ai o cerere de la acest utilizator.")
    f.status = "accepted"
    db.commit()
    return {"detail": "Cerere acceptată."}


@router.delete("/friends/{other_id}")
def remove(other_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Șterge prietenul, refuză o cerere sau retrage cererea trimisă."""
    f = _get_pair_or_404(db, me, other_id)
    if f.status == "blocked":
        raise HTTPException(400, "Folosește „Deblochează”.")
    db.delete(f)
    db.commit()
    return {"detail": "Gata."}


@router.post("/friends/{other_id}/block")
def block(other_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if other_id == me.id or not db.get(User, other_id):
        raise HTTPException(404, "Utilizator inexistent.")
    f = _pair(db, me.id, other_id)
    if f:
        db.delete(f)
        db.flush()
    db.add(Friendship(requester_id=me.id, addressee_id=other_id, status="blocked"))
    db.commit()
    return {"detail": "Utilizator blocat: nu-ți mai poate scrie și nu-ți mai vede poziția."}


@router.post("/friends/{other_id}/unblock")
def unblock(other_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    f = _pair(db, me.id, other_id)
    if not f or f.status != "blocked" or f.requester_id != me.id:
        raise HTTPException(400, "Nu ai blocat acest utilizator.")
    db.delete(f)
    db.commit()
    return {"detail": "Deblocat."}


@router.get("/friends/{other_id}/profile")
def friend_profile(other_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if not are_friends(db, me.id, other_id):
        raise HTTPException(403, "Vezi profilul doar la prieteni.")
    other = db.get(User, other_id)
    places = db.scalars(select(Place).where(Place.mayor_id == other_id).order_by(Place.name)).all()
    return {**_user_card(db, other),
            "conquered": [{"id": p.id, "name": p.name, "category": p.category, "lat": p.lat, "lng": p.lng} for p in places]}


# ---------------- poziția pe hartă (opțional) ----------------
class SharingIn(BaseModel):
    sharing: bool


@router.put("/me/sharing")
def set_sharing(body: SharingIn, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    loc = db.get(SharedLocation, me.id) or SharedLocation(user_id=me.id)
    loc.sharing = body.sharing
    if not body.sharing:   # la oprire, ștergem poziția
        loc.lat = loc.lng = loc.updated_at = None
    db.merge(loc)
    db.commit()
    return {"sharing": body.sharing}


class LocationIn(BaseModel):
    lat: float
    lng: float


@router.put("/me/location", status_code=204)
def update_location(body: LocationIn, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    loc = db.get(SharedLocation, me.id)
    if not loc or not loc.sharing:
        return   # nu salvăm nimic dacă utilizatorul nu a pornit partajarea
    loc.lat, loc.lng, loc.updated_at = round(body.lat, 5), round(body.lng, 5), utcnow()
    db.commit()


@router.get("/friends/locations")
def friend_locations(me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.scalars(select(Friendship).where(Friendship.status == "accepted",
                                               or_(Friendship.requester_id == me.id, Friendship.addressee_id == me.id))).all()
    ids = [f.addressee_id if f.requester_id == me.id else f.requester_id for f in rows]
    if not ids:
        return []
    since = utcnow() - LOCATION_TTL
    locs = db.scalars(select(SharedLocation).where(SharedLocation.user_id.in_(ids), SharedLocation.sharing.is_(True),
                                                  SharedLocation.updated_at >= since)).all()
    out = []
    for l in locs:
        u = db.get(User, l.user_id)
        out.append({**_user_card(db, u), "lat": l.lat, "lng": l.lng, "updated_at": l.updated_at.isoformat()})
    return out


def delete_user_social(db: Session, uid: int) -> None:
    """Folosit la ștergerea contului."""
    db.execute(delete(Friendship).where(or_(Friendship.requester_id == uid, Friendship.addressee_id == uid)))
    db.execute(delete(SharedLocation).where(SharedLocation.user_id == uid))


@router.get("/users/{other_id}/public")
def public_profile(other_id: int, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Profilul public al unui arădean (din clasament): doar date de joc, fără email sau poziție."""
    from ..models import Badge, Checkin
    from ..serializers import badge_out
    other = db.get(User, other_id)
    if not other or is_blocked(db, me.id, other_id):
        raise HTTPException(404, "Utilizatorul nu există.")
    rank = (db.scalar(select(func.count()).select_from(User).where(
        or_(User.xp > other.xp, and_(User.xp == other.xp, User.id < other.id)))) or 0) + 1
    checkins = db.scalar(select(func.count()).select_from(Checkin).where(Checkin.user_id == other_id)) or 0
    visited = db.scalar(select(func.count(func.distinct(Checkin.place_id))).where(Checkin.user_id == other_id)) or 0
    badges = db.scalars(select(Badge).where(Badge.user_id == other_id).order_by(Badge.earned_at)).all()
    places = db.scalars(select(Place).where(Place.mayor_id == other_id).order_by(Place.name)).all()
    f = _pair(db, me.id, other_id)
    if other_id == me.id:
        status = "me"
    elif not f:
        status = "none"
    elif f.status == "accepted":
        status = "friends"
    elif f.status == "pending":
        status = "outgoing" if f.requester_id == me.id else "incoming"
    else:
        status = "blocked"
    return {**_user_card(db, other), "rank": rank, "checkins": checkins, "places_visited": visited,
            "badges": [badge_out(b).model_dump(mode="json") for b in badges],
            "conquered": [{"id": p.id, "name": p.name, "category": p.category, "lat": p.lat, "lng": p.lng} for p in places],
            "friendship": status, "member_since": other.created_at.isoformat() if getattr(other, "created_at", None) else None}
