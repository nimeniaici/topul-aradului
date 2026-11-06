from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session, joinedload

from ..categories import CATEGORIES
from ..db import get_db
from ..geo import distance_m
from ..models import Checkin, Place
from ..schemas import CategoryOut, PhotoOut, PlaceDetailOut, PlaceOut
from ..serializers import photo_url, place_out
from .tour3d import splat_size_mb

router = APIRouter(prefix="/places", tags=["places"])


@router.get("/categories", response_model=list[CategoryOut])
def categories(db: Session = Depends(get_db)):
    counts = dict(db.execute(select(Place.category, func.count()).group_by(Place.category)).all())
    return [CategoryOut(code=c, label=l, count=counts.get(c, 0)) for c, l in CATEGORIES.items()
            if counts.get(c, 0) > 0]


@router.get("", response_model=list[PlaceOut])
def list_places(
    category: str | None = None,
    q: str | None = Query(None, description="caută după nume"),
    lat: float | None = None,
    lng: float | None = None,
    radius_km: float | None = Query(None, gt=0, description="doar locurile pe o rază (necesită lat/lng)"),
    db: Session = Depends(get_db),
):
    stmt = select(Place).options(joinedload(Place.mayor))
    if category:
        stmt = stmt.where(Place.category == category)
    if q:
        stmt = stmt.where(Place.name.ilike(f"%{q}%"))
    places = db.scalars(stmt).all()

    if lat is None or lng is None:
        return [place_out(p) for p in sorted(places, key=lambda p: -p.rating)]

    with_dist = [(p, distance_m(lat, lng, p.lat, p.lng)) for p in places]
    if radius_km:
        with_dist = [(p, d) for p, d in with_dist if d <= radius_km * 1000]
    with_dist.sort(key=lambda x: x[1])
    return [place_out(p, d) for p, d in with_dist]


@router.get("/{place_id}", response_model=PlaceDetailOut)
def place_detail(place_id: int, db: Session = Depends(get_db)):
    place = db.get(Place, place_id, options=[joinedload(Place.mayor)])
    if not place:
        raise HTTPException(404, "Locul nu există.")
    photos = db.scalars(
        select(Checkin).options(joinedload(Checkin.user))
        .where(Checkin.place_id == place_id, Checkin.photo_path.is_not(None))
        .order_by(Checkin.created_at.desc()).limit(12)
    ).all()
    better = db.scalar(
        select(func.count()).select_from(Place)
        .where(Place.category == place.category, Place.rating > place.rating)
    ) or 0
    return PlaceDetailOut(
        **place_out(place).model_dump(),
        recent_photos=[PhotoOut(photo_url=photo_url(c.photo_path), user_name=c.user.name,
                                created_at=c.created_at) for c in photos],
        rank_in_category=better + 1,
        splat_mb=splat_size_mb(place.splat_url),
    )
