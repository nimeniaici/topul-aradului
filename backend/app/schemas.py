"""Ce primește și ce trimite API-ul (JSON)."""
from datetime import datetime

from pydantic import BaseModel, Field


# ---------- Auth ----------
class RegisterIn(BaseModel):
    name: str = Field(min_length=2, max_length=40)
    email: str = Field(pattern=r"^[^@\s]+@[^@\s]+\.[^@\s]+$", max_length=120)
    password: str = Field(min_length=6, max_length=100)


class LoginIn(BaseModel):
    email: str
    password: str


class GoogleIn(BaseModel):
    id_token: str = Field(min_length=20)


class ForgotIn(BaseModel):
    email: str


class ResetIn(BaseModel):
    email: str
    code: str = Field(pattern=r"^\d{6}$")
    new_password: str = Field(min_length=6, max_length=100)


class MessageOut(BaseModel):
    detail: str


# emoji-urile și culorile din care își alege utilizatorul avatarul
AVATAR_EMOJIS = ["😎", "🦊", "🐻", "🐼", "🦁", "🐯", "🐸", "🐙", "🦄", "🐝", "🍕", "☕",
                 "🚲", "🎸", "⚽", "🎮", "👑", "🌟", "🔥", "🌊", "🍷", "🏰", "🦉", "🚀"]
AVATAR_COLORS = ["violet", "pink", "mint", "blue", "gold", "green"]


class UpdateMeIn(BaseModel):
    name: str | None = Field(None, min_length=2, max_length=40)
    avatar_emoji: str | None = None
    avatar_color: str | None = None


class UserOut(BaseModel):
    id: int
    name: str
    xp: int
    level: int
    avatar_emoji: str | None = None
    avatar_color: str | None = None


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut


# ---------- Locuri ----------
class CategoryOut(BaseModel):
    code: str
    label: str
    count: int


class PlaceOut(BaseModel):
    id: int
    name: str
    category: str
    category_label: str
    description: str
    address: str
    lat: float
    lng: float
    photo_url: str | None
    rating: float
    duels: int
    wins: int
    checkin_count: int
    mayor_id: int | None
    mayor_name: str | None
    verified: bool
    distance_m: float | None = None


class PhotoOut(BaseModel):
    photo_url: str
    user_name: str
    created_at: datetime


class PlaceDetailOut(PlaceOut):
    recent_photos: list[PhotoOut]
    rank_in_category: int


# ---------- Check-in ----------
class BadgeOut(BaseModel):
    code: str
    title: str
    description: str
    earned_at: datetime


class CheckinResult(BaseModel):
    checkin_id: int
    place_id: int
    xp_gained: int
    total_xp: int
    level: int
    new_badges: list[BadgeOut]
    became_mayor: bool
    distance_m: float


class HeatPoint(BaseModel):
    lat: float
    lng: float
    place_id: int
    created_at: datetime


class MyCheckinOut(BaseModel):
    id: int
    place_id: int
    place_name: str
    photo_url: str | None
    created_at: datetime


# ---------- Dueluri ----------
class DuelOut(BaseModel):
    category: str
    category_label: str
    a: PlaceOut
    b: PlaceOut


class VoteIn(BaseModel):
    winner_id: int
    loser_id: int


class VoteOut(BaseModel):
    winner: PlaceOut
    loser: PlaceOut
    xp_gained: int
    new_badges: list[BadgeOut]


# ---------- Utilizatori ----------
class UserRankOut(BaseModel):
    id: int
    name: str
    xp: int
    level: int
    rank: int
    avatar_emoji: str | None = None
    avatar_color: str | None = None


class MissionOut(BaseModel):
    code: str
    title: str        # ce ai de făcut
    reward: str       # numele insignei câștigate
    progress: int
    target: int


class ProfileOut(BaseModel):
    id: int
    name: str
    email: str
    avatar_emoji: str | None
    avatar_color: str | None
    has_password: bool
    google_linked: bool
    xp: int
    level: int
    xp_into_level: int
    xp_per_level: int
    checkins: int
    places_visited: int
    votes: int
    badges: list[BadgeOut]
    all_badges: list[BadgeOut]  # toate insignele posibile (earned_at = epoch dacă nu e câștigată)
    mayor_of: list[PlaceOut]
    missions: list[MissionOut]  # insignele neobținute, cea mai aproape de final prima
