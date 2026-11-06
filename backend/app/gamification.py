"""XP, niveluri, insigne și „Primarul” unui loc."""
from datetime import datetime, timedelta

from sqlalchemy import distinct, func, select
from sqlalchemy.orm import Session

from . import config
from .categories import FOOD
from .models import Badge, Checkin, Place, User, Vote, utcnow

XP_PER_LEVEL = 100
XP_CHECKIN = 10
XP_CHECKIN_NEW_PLACE = 15
XP_PHOTO_BONUS = 5
XP_VOTE = 2

# cod -> (titlu, descriere)
BADGES: dict[str, tuple[str, str]] = {
    "prim_pas": ("Primul pas", "Primul tău check-in."),
    "explorator": ("Explorator", "Ai fost în 5 locuri diferite."),
    "cafegiu": ("Cafegiu", "3 cafenele diferite în 7 zile."),
    "gurmand": ("Gurmand", "5 localuri diferite cu mâncare."),
    "turist": ("Turist acasă", "3 obiective, mănăstiri sau locuri în natură."),
    "pelerin": ("Pelerin", "Ai vizitat 2 mănăstiri din județ."),
    "fotograf": ("Fotograf", "5 check-in-uri cu poză."),
    "primar": ("Primar", "Ai devenit Primarul unui loc."),
    "judecator": ("Judecător", "Ai votat în 20 de dueluri."),
}

# Textul misiunii (ce ai de făcut ca să câștigi insigna)
MISSIONS: dict[str, str] = {
    "prim_pas": "Fă primul check-in",
    "explorator": "Vizitează 5 locuri diferite",
    "cafegiu": "3 cafenele diferite în 7 zile",
    "gurmand": "Mănâncă în 5 localuri diferite",
    "turist": "Vizitează 3 obiective sau parcuri",
    "pelerin": "Vizitează 2 mănăstiri din județ",
    "fotograf": "Fă 5 check-in-uri cu poză",
    "primar": "Devino Primarul unui loc",
    "judecator": "Votează în 20 de dueluri",
}

EPOCH = datetime(1970, 1, 1)


def level(xp: int) -> int:
    return xp // XP_PER_LEVEL + 1


def badge_info(code: str) -> tuple[str, str]:
    return BADGES.get(code, (code, ""))


def _distinct_places(db: Session, user_id: int, categories: set[str] | None = None,
                     since: datetime | None = None) -> int:
    q = select(func.count(distinct(Checkin.place_id))).join(Place).where(Checkin.user_id == user_id)
    if categories:
        q = q.where(Place.category.in_(list(categories)))
    if since:
        q = q.where(Checkin.created_at >= since)
    return db.scalar(q) or 0


def badge_progress(db: Session, user: User) -> dict[str, tuple[int, int]]:
    """cod insignă -> (progres actual, țintă)."""
    uid = user.id
    count = lambda q: db.scalar(q) or 0  # noqa: E731
    week_ago = utcnow() - timedelta(days=7)
    return {
        "prim_pas": (count(select(func.count()).select_from(Checkin).where(Checkin.user_id == uid)), 1),
        "explorator": (_distinct_places(db, uid), 5),
        "cafegiu": (_distinct_places(db, uid, {"cafenea"}, week_ago), 3),
        "gurmand": (_distinct_places(db, uid, FOOD), 5),
        "turist": (_distinct_places(db, uid, {"obiectiv", "natura", "manastire"}), 3),
        "pelerin": (_distinct_places(db, uid, {"manastire"}), 2),
        "fotograf": (count(select(func.count()).select_from(Checkin)
                           .where(Checkin.user_id == uid, Checkin.photo_path.is_not(None))), 5),
        "primar": (count(select(func.count()).select_from(Place).where(Place.mayor_id == uid)), 1),
        "judecator": (count(select(func.count()).select_from(Vote).where(Vote.user_id == uid)), 20),
    }


def _earned_conditions(db: Session, user: User) -> dict[str, bool]:
    return {code: value >= target for code, (value, target) in badge_progress(db, user).items()}


def award_badges(db: Session, user: User) -> list[Badge]:
    """Acordă insignele noi câștigate. Nu face commit."""
    owned = set(db.scalars(select(Badge.code).where(Badge.user_id == user.id)))
    new = []
    for code, ok in _earned_conditions(db, user).items():
        if ok and code not in owned:
            badge = Badge(user_id=user.id, code=code, earned_at=utcnow())
            db.add(badge)
            new.append(badge)
    db.flush()
    return new


def update_mayor(db: Session, place: Place) -> tuple[int | None, int | None] | None:
    """Recalculează Primarul. Returnează (vechiul_id, noul_id) dacă s-a schimbat, altfel None."""
    since = utcnow() - timedelta(days=config.MAYOR_WINDOW_DAYS)
    rows = db.execute(
        select(Checkin.user_id, func.count().label("n"), func.min(Checkin.created_at).label("first"))
        .where(Checkin.place_id == place.id, Checkin.created_at >= since)
        .group_by(Checkin.user_id)
    ).all()
    if not rows:
        return None
    top = max(r.n for r in rows)
    leaders = sorted((r for r in rows if r.n == top), key=lambda r: r.first)
    leader_ids = [r.user_id for r in leaders]
    if place.mayor_id in leader_ids:  # la egalitate, primarul actual rămâne
        return None
    old = place.mayor_id
    place.mayor_id = leader_ids[0]
    db.flush()
    return old, place.mayor_id
