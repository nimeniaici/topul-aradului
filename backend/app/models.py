"""Tabelele bazei de date."""
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def utcnow() -> datetime:
    """UTC fără timezone (SQLite nu păstrează tz; așa comparațiile sunt consistente)."""
    return datetime.now(timezone.utc).replace(tzinfo=None)


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(40))
    email: Mapped[str] = mapped_column(String(120), unique=True, index=True)
    # gol ("") pentru conturile create doar cu Google
    password_hash: Mapped[str] = mapped_column(String(200))
    google_sub: Mapped[str | None] = mapped_column(String(64), nullable=True, index=True)
    avatar_emoji: Mapped[str | None] = mapped_column(String(16), nullable=True)
    avatar_color: Mapped[str | None] = mapped_column(String(16), nullable=True)
    # poza de profil (fișier în UPLOAD_DIR); dacă lipsește, se folosește avatarul cu emoji/culoare
    avatar_path: Mapped[str | None] = mapped_column(String(80), nullable=True)
    xp: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    badges: Mapped[list["Badge"]] = relationship(back_populates="user", cascade="all, delete-orphan")


class Place(Base):
    __tablename__ = "places"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(120))
    category: Mapped[str] = mapped_column(String(30), index=True)
    description: Mapped[str] = mapped_column(Text, default="")
    address: Mapped[str] = mapped_column(String(200), default="")
    lat: Mapped[float] = mapped_column(Float)
    lng: Mapped[float] = mapped_column(Float)
    photo_url: Mapped[str | None] = mapped_column(String(300), nullable=True)
    # scena 3D (Gaussian splat: .ply / .splat / .ksplat) pentru „Turul 3D”
    splat_url: Mapped[str | None] = mapped_column(String(300), nullable=True)
    # coordonatele au fost verificate pe hartă de echipă?
    verified: Mapped[bool] = mapped_column(Boolean, default=False)

    rating: Mapped[float] = mapped_column(Float, default=1000.0)
    duels: Mapped[int] = mapped_column(Integer, default=0)
    wins: Mapped[int] = mapped_column(Integer, default=0)
    checkin_count: Mapped[int] = mapped_column(Integer, default=0)

    mayor_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)
    mayor: Mapped[User | None] = relationship(foreign_keys=[mayor_id])


class Checkin(Base):
    __tablename__ = "checkins"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    place_id: Mapped[int] = mapped_column(ForeignKey("places.id"), index=True)
    # coordonatele LOCULUI (nu ale utilizatorului: poziția lui exactă nu se păstrează)
    lat: Mapped[float] = mapped_column(Float)
    lng: Mapped[float] = mapped_column(Float)
    photo_path: Mapped[str | None] = mapped_column(String(300), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, index=True)

    user: Mapped[User] = relationship()
    place: Mapped[Place] = relationship()


class Vote(Base):
    __tablename__ = "votes"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    winner_id: Mapped[int] = mapped_column(ForeignKey("places.id"))
    loser_id: Mapped[int] = mapped_column(ForeignKey("places.id"))
    category: Mapped[str] = mapped_column(String(30))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Report(Base):
    """Sesizare de la cetățeni: groapă, iluminat stricat, gunoi etc. (poziția = locul problemei)."""
    __tablename__ = "reports"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    category: Mapped[str] = mapped_column(String(20))
    description: Mapped[str] = mapped_column(Text, default="")
    lat: Mapped[float] = mapped_column(Float)
    lng: Mapped[float] = mapped_column(Float)
    photo_path: Mapped[str | None] = mapped_column(String(300), nullable=True)
    status: Mapped[str] = mapped_column(String(20), default="nou")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, index=True)
    # răspunsul Primăriei (ex. „Echipa ajunge marți”) și când s-a schimbat ultima dată statusul
    admin_note: Mapped[str | None] = mapped_column(Text, nullable=True)
    updated_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    user: Mapped[User] = relationship()


class ResetCode(Base):
    """Cod de 6 cifre trimis pe email pentru resetarea parolei (salvăm doar hash-ul)."""
    __tablename__ = "reset_codes"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    code_hash: Mapped[str] = mapped_column(String(64))
    expires_at: Mapped[datetime] = mapped_column(DateTime)
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    used: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Badge(Base):
    __tablename__ = "badges"
    __table_args__ = (UniqueConstraint("user_id", "code"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    code: Mapped[str] = mapped_column(String(30))
    earned_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    user: Mapped[User] = relationship(back_populates="badges")
