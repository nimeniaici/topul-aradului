import secrets
from datetime import timedelta

from sqlalchemy.exc import IntegrityError
from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from .. import usernames
from .. import config, mailer
from ..db import get_db
from ..gamification import level
from ..models import ResetCode, User, utcnow
from ..schemas import ForgotIn, GoogleIn, LoginIn, MessageOut, RegisterIn, ResetIn, TokenOut, UserOut
from ..serializers import photo_url
from ..security import create_token, hash_code, hash_password, verify_google_token, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])

MAX_CODE_ATTEMPTS = 5
MAX_CODES_PER_WINDOW = 3


def token_out(user: User) -> TokenOut:
    return TokenOut(
        access_token=create_token(user.id),
        user=UserOut(id=user.id, name=user.name, xp=user.xp, level=level(user.xp),
                     avatar_emoji=user.avatar_emoji, avatar_color=user.avatar_color,
                     avatar_url=photo_url(user.avatar_path)),
    )


def _by_email(db: Session, email: str) -> User | None:
    return db.scalar(select(User).where(User.email == email.strip().lower()))


@router.post("/register", response_model=TokenOut, status_code=201)
def register(body: RegisterIn, db: Session = Depends(get_db)):
    email = body.email.strip().lower()
    if _by_email(db, email):
        raise HTTPException(409, "Există deja un cont cu acest email.")
    username = usernames.validate_new(db, body.name)
    user = User(name=username, email=email, password_hash=hash_password(body.password))
    db.add(user)
    try:
        db.commit()
    except IntegrityError:  # două înregistrări cu același username în aceeași secundă
        db.rollback()
        raise HTTPException(409, "Username-ul sau emailul tocmai a fost luat. Încearcă altul.")
    return token_out(user)


@router.get("/username-available")
def username_available(u: str, db: Session = Depends(get_db)):
    """Pentru verificarea în timp ce scrii: e liber username-ul?"""
    name = usernames.normalize(u)
    if (why := usernames.problem(name)):
        return {"username": name, "available": False, "reason": why}
    if usernames.is_taken(db, name):
        return {"username": name, "available": False, "reason": "E deja luat.",
                "suggestion": usernames.generate(db, name)}
    return {"username": name, "available": True, "reason": None}


@router.post("/login", response_model=TokenOut)
def login(body: LoginIn, db: Session = Depends(get_db)):
    user = _by_email(db, body.email)
    if user and not user.password_hash and user.google_sub:
        raise HTTPException(401, "Contul acesta folosește „Continuă cu Google”.")
    if not user or not verify_password(body.password, user.password_hash):
        raise HTTPException(401, "Email sau parolă greșită.")
    return token_out(user)


@router.post("/google", response_model=TokenOut)
def google_login(body: GoogleIn, db: Session = Depends(get_db)):
    """Primește ID token-ul de la Google (din aplicația Android), creează sau leagă contul."""
    if not config.GOOGLE_CLIENT_IDS:
        raise HTTPException(503, "Login-ul cu Google nu e configurat pe server.")
    try:
        info = verify_google_token(body.id_token)
    except ValueError:
        raise HTTPException(401, "Autentificarea Google a eșuat. Încearcă din nou.")
    email = (info.get("email") or "").strip().lower()
    if not email or not info.get("email_verified"):
        raise HTTPException(401, "Contul Google nu are un email verificat.")

    user = db.scalar(select(User).where(User.google_sub == info["sub"])) or _by_email(db, email)
    if user is None:
        base = info.get("given_name") or info.get("name") or email.split("@")[0]
        user = User(name=usernames.generate(db, base), email=email, password_hash="",
                    google_sub=info["sub"])
        db.add(user)
    elif not user.google_sub:
        user.google_sub = info["sub"]  # cont cu email existent -> îl legăm de Google
    db.commit()
    return token_out(user)


@router.post("/forgot-password", response_model=MessageOut, status_code=202)
def forgot_password(body: ForgotIn, background: BackgroundTasks, db: Session = Depends(get_db)):
    """Trimite un cod de 6 cifre pe email. Răspunsul e același și dacă emailul nu există."""
    generic = MessageOut(detail="Dacă există un cont cu acest email, ți-am trimis un cod de 6 cifre.")
    user = _by_email(db, body.email)
    if user is None:
        return generic
    window = utcnow() - timedelta(minutes=config.RESET_CODE_MINUTES)
    recent = db.scalar(
        select(func.count()).select_from(ResetCode)
        .where(ResetCode.user_id == user.id, ResetCode.created_at >= window)
    ) or 0
    if recent >= MAX_CODES_PER_WINDOW:
        return generic
    code = f"{secrets.randbelow(10 ** 6):06d}"
    db.add(ResetCode(user_id=user.id, code_hash=hash_code(code),
                     expires_at=utcnow() + timedelta(minutes=config.RESET_CODE_MINUTES)))
    db.commit()
    background.add_task(mailer.send_reset_code, user.email, user.name, code)
    return generic


@router.post("/reset-password", response_model=TokenOut)
def reset_password(body: ResetIn, db: Session = Depends(get_db)):
    invalid = HTTPException(400, "Cod greșit sau expirat.")
    user = _by_email(db, body.email)
    if user is None:
        raise invalid
    rc = db.scalar(
        select(ResetCode)
        .where(ResetCode.user_id == user.id, ResetCode.used.is_(False), ResetCode.expires_at > utcnow())
        .order_by(ResetCode.created_at.desc()).limit(1)
    )
    if rc is None or rc.attempts >= MAX_CODE_ATTEMPTS:
        raise invalid
    if not secrets.compare_digest(rc.code_hash, hash_code(body.code)):
        rc.attempts += 1
        db.commit()
        raise invalid
    rc.used = True
    user.password_hash = hash_password(body.new_password)
    db.commit()
    return token_out(user)
