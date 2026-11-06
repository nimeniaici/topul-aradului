"""Parole (PBKDF2 din biblioteca standard) și token-uri JWT."""
import hashlib
import hmac
import os
from datetime import datetime, timedelta, timezone

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from . import config
from .db import get_db
from .models import User

_ITERATIONS = 200_000
_bearer = HTTPBearer(auto_error=False)


def hash_password(password: str) -> str:
    salt = os.urandom(16)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, _ITERATIONS)
    return f"pbkdf2${salt.hex()}${dk.hex()}"


def hash_code(code: str) -> str:
    """Hash pentru codurile de resetare (scurte, cu viață scurtă și număr limitat de încercări)."""
    return hashlib.sha256(f"{config.SECRET_KEY}:{code}".encode()).hexdigest()


def verify_google_token(token: str) -> dict:
    """Verifică semnătura și audiența unui ID token Google. Aruncă ValueError dacă nu e valid."""
    from google.auth.transport import requests as google_requests
    from google.oauth2 import id_token

    info = id_token.verify_oauth2_token(token, google_requests.Request())
    if info.get("aud") not in config.GOOGLE_CLIENT_IDS:
        raise ValueError("Token-ul nu e emis pentru această aplicație.")
    return info


def verify_password(password: str, stored: str) -> bool:
    if not stored:  # cont doar cu Google
        return False
    try:
        _, salt_hex, dk_hex = stored.split("$")
    except ValueError:
        return False
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt_hex), _ITERATIONS)
    return hmac.compare_digest(dk.hex(), dk_hex)


def create_token(user_id: int) -> str:
    payload = {
        "sub": str(user_id),
        "exp": datetime.now(timezone.utc) + timedelta(days=config.TOKEN_DAYS),
    }
    return jwt.encode(payload, config.SECRET_KEY, algorithm="HS256")


def get_current_user(
    cred: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: Session = Depends(get_db),
) -> User:
    unauthorized = HTTPException(status.HTTP_401_UNAUTHORIZED, "Trebuie să fii logat.")
    if cred is None:
        raise unauthorized
    try:
        payload = jwt.decode(cred.credentials, config.SECRET_KEY, algorithms=["HS256"])
        user_id = int(payload["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        raise unauthorized
    user = db.get(User, user_id)
    if user is None:
        raise unauthorized
    return user
