"""Transformă obiectele din baza de date în răspunsuri JSON."""
from .categories import label
from .gamification import EPOCH, badge_info
from .models import Badge, Place
from .schemas import BadgeOut, PlaceOut


def photo_url(path: str | None) -> str | None:
    return f"/uploads/{path}" if path else None


def place_out(p: Place, distance: float | None = None) -> PlaceOut:
    return PlaceOut(
        id=p.id,
        name=p.name,
        category=p.category,
        category_label=label(p.category),
        description=p.description,
        address=p.address,
        lat=p.lat,
        lng=p.lng,
        photo_url=p.photo_url,
        splat_url=p.splat_url,
        rating=round(p.rating, 1),
        duels=p.duels,
        wins=p.wins,
        checkin_count=p.checkin_count,
        mayor_id=p.mayor_id,
        mayor_name=p.mayor.name if p.mayor else None,
        verified=p.verified,
        distance_m=round(distance, 1) if distance is not None else None,
    )


def badge_out(b: Badge) -> BadgeOut:
    title, desc = badge_info(b.code)
    return BadgeOut(code=b.code, title=title, description=desc, earned_at=b.earned_at)


def badge_placeholder(code: str) -> BadgeOut:
    title, desc = badge_info(code)
    return BadgeOut(code=code, title=title, description=desc, earned_at=EPOCH)
