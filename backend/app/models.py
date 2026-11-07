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


class Feedback(Base):
    """Probleme ale aplicației (bug-uri, idei) raportate din Setări."""
    __tablename__ = "feedback"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True, index=True)
    kind: Mapped[str] = mapped_column(String(20), default="bug")
    message: Mapped[str] = mapped_column(Text)
    app_version: Mapped[str] = mapped_column(String(40), default="")
    device: Mapped[str] = mapped_column(String(120), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, index=True)


class TransitReport(Base):
    """Semnalare de la cetățeni despre transportul public: aglomerat, întârziere, tramvai defect."""
    __tablename__ = "transit_reports"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    line: Mapped[str] = mapped_column(String(10), index=True)
    kind: Mapped[str] = mapped_column(String(20))
    stop_id: Mapped[str | None] = mapped_column(String(40), nullable=True)
    stop_name: Mapped[str | None] = mapped_column(String(120), nullable=True)
    note: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, index=True)


class TransitTicket(Base):
    """Bilet digital DEMONSTRATIV (fără plată reală)."""
    __tablename__ = "transit_tickets"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    type: Mapped[str] = mapped_column(String(20))
    code: Mapped[str] = mapped_column(String(20))
    valid_until: Mapped[datetime] = mapped_column(DateTime)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


# ---------------- prieteni ----------------
class Friendship(Base):
    """Relația dintre doi utilizatori: cerere (pending), prieteni (accepted) sau blocat (blocked).
    requester = cine a trimis cererea (sau cine a blocat, la blocked)."""
    __tablename__ = "friendships"
    __table_args__ = (UniqueConstraint("requester_id", "addressee_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    requester_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    addressee_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    status: Mapped[str] = mapped_column(String(10), default="pending")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class SharedLocation(Base):
    """Ultima poziție a unui utilizator care a ales să se arate prietenilor pe hartă (opțional, oprit implicit)."""
    __tablename__ = "shared_locations"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), primary_key=True)
    sharing: Mapped[bool] = mapped_column(Boolean, default=False)
    lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    lng: Mapped[float | None] = mapped_column(Float, nullable=True)
    updated_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


# ---------------- comunitate (chat) ----------------
class ChatRoom(Base):
    """general = comunitatea Aradului (toată lumea); group = vecini, bloc, școală…; direct = între doi prieteni."""
    __tablename__ = "chat_rooms"

    id: Mapped[int] = mapped_column(primary_key=True)
    kind: Mapped[str] = mapped_column(String(10), index=True)
    tag: Mapped[str] = mapped_column(String(20), default="")          # vecini, cartier, bloc, scoala, altele
    name: Mapped[str] = mapped_column(String(60))
    description: Mapped[str] = mapped_column(String(200), default="")
    owner_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)
    invite_code: Mapped[str | None] = mapped_column(String(12), nullable=True, unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class ChatMember(Base):
    __tablename__ = "chat_members"
    __table_args__ = (UniqueConstraint("room_id", "user_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    room_id: Mapped[int] = mapped_column(ForeignKey("chat_rooms.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    joined_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class ChatMessage(Base):
    """Mesaj; kind = text, anunt sau eveniment (cu titlu, dată și loc)."""
    __tablename__ = "chat_messages"

    id: Mapped[int] = mapped_column(primary_key=True)
    room_id: Mapped[int] = mapped_column(ForeignKey("chat_rooms.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    kind: Mapped[str] = mapped_column(String(12), default="text")
    text: Mapped[str] = mapped_column(Text)
    event_title: Mapped[str | None] = mapped_column(String(100), nullable=True)
    event_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    event_place: Mapped[str | None] = mapped_column(String(120), nullable=True)
    event_lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    event_lng: Mapped[float | None] = mapped_column(Float, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, index=True)

    user: Mapped[User] = relationship()


# ---------------- recompense ----------------
class TransitCheckin(Base):
    """Check-in într-o stație CTP (pentru recompensele de transport)."""
    __tablename__ = "transit_checkins"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    stop_id: Mapped[str] = mapped_column(String(20))
    stop_name: Mapped[str] = mapped_column(String(80))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, index=True)


class RewardVoucher(Base):
    """Voucher DEMONSTRATIV deblocat dintr-o recompensă (transport gratuit, reducere la un local)."""
    __tablename__ = "reward_vouchers"
    __table_args__ = (UniqueConstraint("user_id", "reward", "place_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    reward: Mapped[str] = mapped_column(String(30))
    place_id: Mapped[int | None] = mapped_column(ForeignKey("places.id"), nullable=True)
    code: Mapped[str] = mapped_column(String(20))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


# ---------------- sondaje ----------------
class Poll(Base):
    """Sondaj: al Primăriei (official) sau al unui cetățean, în comunitatea generală."""
    __tablename__ = "polls"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True, index=True)
    question: Mapped[str] = mapped_column(String(200))
    official: Mapped[bool] = mapped_column(Boolean, default=False)
    closes_at: Mapped[datetime] = mapped_column(DateTime)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, index=True)

    user: Mapped["User | None"] = relationship()


class PollOption(Base):
    __tablename__ = "poll_options"

    id: Mapped[int] = mapped_column(primary_key=True)
    poll_id: Mapped[int] = mapped_column(ForeignKey("polls.id"), index=True)
    text: Mapped[str] = mapped_column(String(100))
    position: Mapped[int] = mapped_column(Integer, default=0)


class PollVote(Base):
    __tablename__ = "poll_votes"
    __table_args__ = (UniqueConstraint("poll_id", "user_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    poll_id: Mapped[int] = mapped_column(ForeignKey("polls.id"), index=True)
    option_id: Mapped[int] = mapped_column(ForeignKey("poll_options.id"))
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class ReportConfirmation(Base):
    """Alt cetățean confirmă aceeași problemă, în același loc (o sesizare e „confirmată” de la 3 oameni)."""
    __tablename__ = "report_confirmations"
    __table_args__ = (UniqueConstraint("report_id", "user_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    report_id: Mapped[int] = mapped_column(ForeignKey("reports.id"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
