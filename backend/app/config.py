"""Setări citite din variabile de mediu (sau din fișierul backend/.env)."""
import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")


def _bool(name: str, default: bool) -> bool:
    return os.getenv(name, str(default)).strip().lower() in ("1", "true", "yes", "da")


def _db_url() -> str:
    url = os.getenv("DATABASE_URL", f"sqlite:///{BASE_DIR / 'topul.db'}")
    # Railway/Render dau URL-uri "postgres://..." -> SQLAlchemy vrea driverul explicit
    if url.startswith("postgres://"):
        url = "postgresql+psycopg://" + url[len("postgres://"):]
    elif url.startswith("postgresql://"):
        url = "postgresql+psycopg://" + url[len("postgresql://"):]
    return url


DATABASE_URL = _db_url()
SECRET_KEY = os.getenv("SECRET_KEY", "dev-doar-local-schimba-ma-inainte-de-deploy-hack4arad")
TOKEN_DAYS = int(os.getenv("TOKEN_DAYS", "30"))

# Online (Postgres) nu pornim cu cheia de test: oricine ar putea crea token-uri valide.
if not DATABASE_URL.startswith("sqlite") and SECRET_KEY.startswith("dev-doar-local"):
    raise RuntimeError("Setează variabila SECRET_KEY înainte de a rula serverul online.")

# Check-in
CHECKIN_RADIUS_M = float(os.getenv("CHECKIN_RADIUS_M", "100"))
CHECKIN_COOLDOWN_MIN = int(os.getenv("CHECKIN_COOLDOWN_MIN", "30"))
# Pune true DOAR pentru demo (ex. în sală, departe de locuri)
SKIP_DISTANCE_CHECK = _bool("SKIP_DISTANCE_CHECK", False)
# Conturi de test care pot face check-in de oriunde (emailuri separate prin virgulă)
TESTER_EMAILS = {e.strip().lower() for e in os.getenv("TESTER_EMAILS", "").split(",") if e.strip()}

# Primar = cele mai multe check-in-uri într-un loc în ultimele N zile
MAYOR_WINDOW_DAYS = int(os.getenv("MAYOR_WINDOW_DAYS", "30"))

# Dueluri
ELO_K = float(os.getenv("ELO_K", "32"))
VOTE_COOLDOWN_MIN = int(os.getenv("VOTE_COOLDOWN_MIN", "10"))

UPLOAD_DIR = Path(os.getenv("UPLOAD_DIR", str(BASE_DIR / "uploads")))
MAX_PHOTO_MB = int(os.getenv("MAX_PHOTO_MB", "8"))

SEED_ON_STARTUP = _bool("SEED_ON_STARTUP", True)

# Login cu Google: ID-urile clientului OAuth (cel de tip „Web application”), separate prin virgulă
GOOGLE_CLIENT_IDS = [c.strip() for c in os.getenv("GOOGLE_CLIENT_IDS", "").split(",") if c.strip()]

# Email pentru resetarea parolei. Fără SMTP_HOST, codul e doar afișat în consola serverului.
SMTP_HOST = os.getenv("SMTP_HOST", "")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "")
SMTP_FROM = os.getenv("SMTP_FROM", SMTP_USER or "Topul Aradului <no-reply@topularadului.ro>")
RESET_CODE_MINUTES = int(os.getenv("RESET_CODE_MINUTES", "15"))

# Cheie pentru operațiile de administrare (ex. urcarea scenelor 3D). Fără ea, rutele /admin sunt dezactivate.
ADMIN_TOKEN = os.getenv("ADMIN_TOKEN", "")
MAX_SPLAT_MB = int(os.getenv("MAX_SPLAT_MB", "300"))
