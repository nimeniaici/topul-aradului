"""Username-uri unice: litere mici, cifre, „.” și „_”, 3–20 caractere (ex.: diana.arad, mihai_23).
Numele afișat peste tot în joc (Primar, clasament, hartă live) e username-ul."""
import re
import unicodedata

from fastapi import HTTPException
from sqlalchemy import func, select, text
from sqlalchemy.orm import Session

from .models import User

MIN_LEN, MAX_LEN = 3, 20
_VALID = re.compile(r"^[a-z0-9](?:[a-z0-9._]*[a-z0-9])?$")
RESERVED = {"admin", "administrator", "primar", "primaria", "topul", "topularadului", "moderator", "support"}


def normalize(raw: str) -> str:
    return (raw or "").strip().lstrip("@").lower()


def problem(name: str) -> str | None:
    """Motivul pentru care username-ul nu e valid (în română), sau None dacă e bun."""
    if not MIN_LEN <= len(name) <= MAX_LEN:
        return f"Username-ul trebuie să aibă între {MIN_LEN} și {MAX_LEN} caractere."
    if not _VALID.match(name) or ".." in name:
        return "Folosește doar litere mici fără diacritice, cifre, „.” sau „_” (nu la început sau la final)."
    if name in RESERVED:
        return "Username-ul acesta e rezervat."
    return None


def is_taken(db: Session, name: str, except_id: int | None = None) -> bool:
    q = select(User.id).where(func.lower(User.name) == name)
    if except_id is not None:
        q = q.where(User.id != except_id)
    return db.scalar(q.limit(1)) is not None


def validate_new(db: Session, raw: str, except_id: int | None = None) -> str:
    """Verifică un username ales de utilizator; aruncă 400 / 409 cu mesaj clar."""
    name = normalize(raw)
    if (why := problem(name)):
        raise HTTPException(400, why)
    if is_taken(db, name, except_id):
        raise HTTPException(409, f"Username-ul „{name}” e deja luat. Încearcă altul.")
    return name


def slugify(text_: str) -> str:
    s = unicodedata.normalize("NFKD", text_ or "").encode("ascii", "ignore").decode().lower()
    s = re.sub(r"[^a-z0-9._]+", ".", s)
    s = re.sub(r"\.{2,}", ".", s).strip("._")[:MAX_LEN].strip("._")
    return s if len(s) >= MIN_LEN else (s + "arad")[:MAX_LEN] if s else "aradean"


def generate(db: Session, base_text: str, taken: set[str] | None = None) -> str:
    """Un username liber pornind de la un nume („Diana Pop” → diana.pop, diana.pop2, ...)."""
    base = slugify(base_text)
    if base in RESERVED:
        base = (base + ".ar")[:MAX_LEN]
    candidate, n = base, 1
    while (taken is not None and candidate in taken) or is_taken(db, candidate):
        n += 1
        suffix = str(n)
        candidate = base[: MAX_LEN - len(suffix)].rstrip("._") + suffix
    return candidate


def migrate(engine) -> None:
    """La pornire: conturile vechi (nume liber, ex. „Diana”) primesc un username unic; apoi index unic."""
    from .db import SessionLocal

    with SessionLocal() as db:
        users = db.scalars(select(User).order_by(User.id)).all()
        seen: set[str] = set()
        changed = False
        for u in users:
            current = normalize(u.name)
            if problem(current) is None and current not in seen:
                if u.name != current:
                    u.name, changed = current, True
            else:
                u.name = generate_offline(u.name or u.email.split("@")[0], seen)
                changed = True
            seen.add(u.name)
        if changed:
            db.commit()
    with engine.begin() as conn:
        conn.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ux_users_name ON users (name)"))


def generate_offline(base_text: str, seen: set[str]) -> str:
    base = slugify(base_text)
    if base in RESERVED:
        base = (base + ".ar")[:MAX_LEN]
    candidate, n = base, 1
    while candidate in seen:
        n += 1
        candidate = base[: MAX_LEN - len(str(n))].rstrip("._") + str(n)
    return candidate
