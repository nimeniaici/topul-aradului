from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy import delete, distinct, func, select, update
from sqlalchemy.orm import Session, joinedload

from sqlalchemy.exc import IntegrityError
import uuid
from fastapi import File, UploadFile
from pydantic import BaseModel, Field
from ..serializers import photo_url
from .. import usernames
from .. import config
from ..db import get_db
from ..gamification import BADGES, MISSIONS, XP_PER_LEVEL, badge_progress, level, update_mayor
from ..models import Badge, ChatMember, ChatMessage, ChatRoom, Checkin, Feedback, Place, Report, ResetCode, TransitReport, TransitTicket, User, Vote
from ..schemas import AVATAR_COLORS, AVATAR_EMOJIS, MissionOut, ProfileOut, UpdateMeIn
from .friends import delete_user_social
from ..security import get_current_user, hash_password, verify_password
from ..serializers import badge_out, badge_placeholder, place_out

router = APIRouter(prefix="/users", tags=["users"])


def _profile(db: Session, user: User) -> ProfileOut:
    checkins = db.scalar(select(func.count()).select_from(Checkin).where(Checkin.user_id == user.id)) or 0
    visited = db.scalar(select(func.count(distinct(Checkin.place_id))).where(Checkin.user_id == user.id)) or 0
    votes = db.scalar(select(func.count()).select_from(Vote).where(Vote.user_id == user.id)) or 0
    badges = db.scalars(select(Badge).where(Badge.user_id == user.id).order_by(Badge.earned_at)).all()
    owned = {b.code: b for b in badges}
    mayor_of = db.scalars(select(Place).options(joinedload(Place.mayor)).where(Place.mayor_id == user.id)).all()
    return ProfileOut(
        id=user.id,
        name=user.name,
        email=user.email,
        avatar_emoji=user.avatar_emoji,
        avatar_color=user.avatar_color,
        avatar_url=photo_url(user.avatar_path),
        has_password=bool(user.password_hash),
        google_linked=bool(user.google_sub),
        xp=user.xp,
        level=level(user.xp),
        xp_into_level=user.xp % XP_PER_LEVEL,
        xp_per_level=XP_PER_LEVEL,
        checkins=checkins,
        places_visited=visited,
        votes=votes,
        badges=[badge_out(b) for b in badges],
        all_badges=[badge_out(owned[c]) if c in owned else badge_placeholder(c) for c in BADGES],
        mayor_of=[place_out(p) for p in mayor_of],
        missions=_missions(db, user, owned),
    )


@router.get("/me", response_model=ProfileOut)
def me(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _profile(db, user)


@router.patch("/me", response_model=ProfileOut)
def update_me(body: UpdateMeIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if body.name is not None and usernames.normalize(body.name) != user.name:
        user.name = usernames.validate_new(db, body.name, except_id=user.id)
    if body.avatar_emoji is not None:
        if body.avatar_emoji not in AVATAR_EMOJIS:
            raise HTTPException(400, "Emoji indisponibil pentru avatar.")
        user.avatar_emoji = body.avatar_emoji
    if body.avatar_color is not None:
        if body.avatar_color not in AVATAR_COLORS:
            raise HTTPException(400, "Culoare indisponibilă pentru avatar.")
        user.avatar_color = body.avatar_color
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "Username-ul tocmai a fost luat de altcineva. Încearcă altul.")
    return _profile(db, user)


_AVATAR_MAX = 2 * 1024 * 1024
_IMAGE_MAGIC = {bytes([0xFF, 0xD8, 0xFF]): ".jpg", bytes([0x89]) + b"PNG": ".png", b"RIFF": ".webp"}


@router.post("/me/avatar", response_model=ProfileOut)
async def upload_avatar(photo: UploadFile = File(...), user: User = Depends(get_current_user),
                        db: Session = Depends(get_db)):
    """Poza de profil (aplicația o micșorează înainte, la ~512 px). Înlocuiește poza veche."""
    data = await photo.read(_AVATAR_MAX + 1)
    if len(data) > _AVATAR_MAX:
        raise HTTPException(413, "Poza e prea mare (max 2 MB).")
    ext = next((e for magic, e in _IMAGE_MAGIC.items() if data.startswith(magic)), None)
    if ext is None or (ext == ".webp" and data[8:12] != b"WEBP"):
        raise HTTPException(400, "Fișierul trebuie să fie o poză JPG, PNG sau WebP.")
    name = f"av_{uuid.uuid4().hex}{ext}"
    config.UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    (config.UPLOAD_DIR / name).write_bytes(data)
    old, user.avatar_path = user.avatar_path, name
    db.commit()
    if old:
        (config.UPLOAD_DIR / old).unlink(missing_ok=True)
    return _profile(db, user)


@router.delete("/me/avatar", response_model=ProfileOut)
def delete_avatar(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    old, user.avatar_path = user.avatar_path, None
    db.commit()
    if old:
        (config.UPLOAD_DIR / old).unlink(missing_ok=True)
    return _profile(db, user)


class PasswordIn(BaseModel):
    old_password: str = ""
    new_password: str = Field(min_length=6, max_length=100)


@router.post("/me/password")
def change_password(body: PasswordIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Schimbă parola. Conturile doar-Google (fără parolă) își pot seta una fără parola veche."""
    if user.password_hash and not verify_password(body.old_password, user.password_hash):
        raise HTTPException(400, "Parola actuală nu e corectă.")
    user.password_hash = hash_password(body.new_password)
    db.commit()
    return {"detail": "Parola a fost schimbată."}


@router.get("/me/stats")
def my_stats(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Scorurile mele pentru Setări: locul în clasament și sesizările trimise / rezolvate."""
    rank = (db.scalar(select(func.count()).select_from(User).where(User.xp > user.xp)) or 0) + 1
    total = db.scalar(select(func.count()).select_from(User)) or 0
    reports = db.scalar(select(func.count()).select_from(Report).where(Report.user_id == user.id)) or 0
    solved = db.scalar(select(func.count()).select_from(Report).where(Report.user_id == user.id,
                                                                      Report.status == "rezolvat")) or 0
    return {"rank": rank, "total_users": total, "reports": reports, "reports_resolved": solved,
            "member_since": user.created_at.date().isoformat()}


@router.delete("/me", status_code=204)
def delete_me(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Șterge definitiv contul și toate datele lui (check-in-uri, poze, voturi, insigne)."""
    uid = user.id
    photos = db.scalars(select(Checkin.photo_path).where(Checkin.user_id == uid, Checkin.photo_path.is_not(None))).all()
    photos += [user.avatar_path] if user.avatar_path else []
    photos += db.scalars(select(Report.photo_path).where(Report.user_id == uid, Report.photo_path.is_not(None))).all()
    visited_ids = set(db.scalars(select(Checkin.place_id).where(Checkin.user_id == uid)))
    mayor_places = db.scalars(select(Place).where(Place.mayor_id == uid)).all()

    for p in mayor_places:
        p.mayor_id = None
    db.execute(delete(Checkin).where(Checkin.user_id == uid))
    db.execute(delete(Vote).where(Vote.user_id == uid))
    db.execute(delete(Badge).where(Badge.user_id == uid))
    db.execute(delete(ResetCode).where(ResetCode.user_id == uid))
    db.execute(delete(Report).where(Report.user_id == uid))
    db.execute(delete(Feedback).where(Feedback.user_id == uid))
    db.execute(delete(TransitReport).where(TransitReport.user_id == uid))
    db.execute(delete(TransitTicket).where(TransitTicket.user_id == uid))
    db.execute(delete(ChatMessage).where(ChatMessage.user_id == uid))
    db.execute(delete(ChatMember).where(ChatMember.user_id == uid))
    db.execute(update(ChatRoom).where(ChatRoom.owner_id == uid).values(owner_id=None))
    delete_user_social(db, uid)
    db.flush()

    # recalculăm contoarele și primarii locurilor atinse
    for place in db.scalars(select(Place).where(Place.id.in_(list(visited_ids | {p.id for p in mayor_places})))):
        place.checkin_count = db.scalar(select(func.count()).select_from(Checkin).where(Checkin.place_id == place.id)) or 0
        update_mayor(db, place)

    db.delete(user)
    db.commit()
    for name in photos:
        (config.UPLOAD_DIR / name).unlink(missing_ok=True)
    return Response(status_code=204)


def _missions(db: Session, user: User, owned: dict) -> list[MissionOut]:
    out = []
    for code, (value, target) in badge_progress(db, user).items():
        if code in owned:
            continue
        out.append(MissionOut(code=code, title=MISSIONS[code], reward=BADGES[code][0],
                              progress=min(value, target), target=target))
    out.sort(key=lambda m: -m.progress / m.target)
    return out
