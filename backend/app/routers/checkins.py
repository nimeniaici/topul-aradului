import uuid
from datetime import timedelta
from pathlib import Path

from fastapi import APIRouter, BackgroundTasks, Depends, File, Form, HTTPException, Query, UploadFile
from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

from .. import config
from ..db import get_db
from ..gamification import (XP_CHECKIN, XP_CHECKIN_NEW_PLACE, XP_PHOTO_BONUS, award_badges, level,
                            update_mayor)
from ..geo import distance_m
from ..live import hub
from ..models import Checkin, Place, User, utcnow
from ..schemas import CheckinResult, HeatPoint, MyCheckinOut
from ..security import get_current_user
from ..serializers import badge_out, photo_url

router = APIRouter(prefix="/checkins", tags=["checkins"])

_ALLOWED_EXT = {".jpg", ".jpeg", ".png", ".webp"}


async def _save_photo(photo: UploadFile) -> str:
    if not (photo.content_type or "").startswith("image/"):
        raise HTTPException(400, "Fișierul trebuie să fie o imagine.")
    data = await photo.read()
    if len(data) > config.MAX_PHOTO_MB * 1024 * 1024:
        raise HTTPException(413, f"Poza e prea mare (max {config.MAX_PHOTO_MB} MB).")
    ext = Path(photo.filename or "").suffix.lower()
    if ext not in _ALLOWED_EXT:
        ext = ".jpg"
    name = f"{uuid.uuid4().hex}{ext}"
    config.UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    (config.UPLOAD_DIR / name).write_bytes(data)
    return name


@router.post("", response_model=CheckinResult, status_code=201)
async def create_checkin(
    background: BackgroundTasks,
    place_id: int = Form(...),
    lat: float = Form(...),
    lng: float = Form(...),
    is_mock: bool = Form(False, description="trimis de telefon dacă GPS-ul e simulat"),
    photo: UploadFile | None = File(None),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    place = db.get(Place, place_id)
    if not place:
        raise HTTPException(404, "Locul nu există.")
    if is_mock:
        raise HTTPException(403, "Am detectat o locație simulată. Check-in refuzat.")

    dist = distance_m(lat, lng, place.lat, place.lng)
    if dist > config.CHECKIN_RADIUS_M and not config.SKIP_DISTANCE_CHECK:
        raise HTTPException(
            400, f"Ești la {dist:.0f} m de {place.name}. Trebuie să fii la cel mult "
                 f"{config.CHECKIN_RADIUS_M:.0f} m.")

    last = db.scalar(
        select(Checkin).where(Checkin.user_id == user.id, Checkin.place_id == place.id)
        .order_by(Checkin.created_at.desc()).limit(1)
    )
    if last and utcnow() - last.created_at < timedelta(minutes=config.CHECKIN_COOLDOWN_MIN):
        raise HTTPException(429, f"Ai făcut deja check-in aici. Mai încearcă peste "
                                 f"{config.CHECKIN_COOLDOWN_MIN} de minute.")

    photo_path = await _save_photo(photo) if photo and photo.filename else None

    xp = XP_CHECKIN if last else XP_CHECKIN_NEW_PLACE
    if photo_path:
        xp += XP_PHOTO_BONUS

    checkin = Checkin(user_id=user.id, place_id=place.id, lat=lat, lng=lng, photo_path=photo_path)
    db.add(checkin)
    place.checkin_count += 1
    user.xp += xp
    db.flush()

    mayor_change = update_mayor(db, place)
    new_badges = award_badges(db, user)
    db.commit()

    background.add_task(hub.broadcast, {
        "type": "checkin",
        "lat": place.lat, "lng": place.lng,
        "place_id": place.id, "place_name": place.name,
        "user_name": user.name,
        "photo_url": photo_url(photo_path),
    })
    if mayor_change:
        old_id, new_id = mayor_change
        background.add_task(hub.broadcast, {
            "type": "mayor",
            "place_id": place.id, "place_name": place.name,
            "old_mayor_id": old_id, "new_mayor_id": new_id, "new_mayor_name": user.name,
        })

    return CheckinResult(
        checkin_id=checkin.id,
        place_id=place.id,
        xp_gained=xp,
        total_xp=user.xp,
        level=level(user.xp),
        new_badges=[badge_out(b) for b in new_badges],
        became_mayor=bool(mayor_change and mayor_change[1] == user.id),
        distance_m=round(dist, 1),
    )


@router.get("/recent", response_model=list[HeatPoint])
def recent(minutes: int = Query(60, ge=1, le=7 * 24 * 60), db: Session = Depends(get_db)):
    """Check-in-urile recente, pentru heatmap."""
    since = utcnow() - timedelta(minutes=minutes)
    rows = db.scalars(select(Checkin).where(Checkin.created_at >= since)).all()
    # punem punctul pe loc (nu pe GPS-ul exact al omului) - mai curat și mai privat
    places = {p.id: p for p in db.scalars(select(Place).where(Place.id.in_(list({c.place_id for c in rows}))))}
    return [HeatPoint(lat=places[c.place_id].lat, lng=places[c.place_id].lng,
                      place_id=c.place_id, created_at=c.created_at) for c in rows]


@router.get("/mine", response_model=list[MyCheckinOut])
def mine(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.scalars(
        select(Checkin).options(joinedload(Checkin.place))
        .where(Checkin.user_id == user.id).order_by(Checkin.created_at.desc()).limit(100)
    ).all()
    return [MyCheckinOut(id=c.id, place_id=c.place_id, place_name=c.place.name,
                         photo_url=photo_url(c.photo_path), created_at=c.created_at) for c in rows]
