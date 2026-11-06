"""Clasamente, cu paginare (offset + limit): aplicația cere următoarea pagină abia când ajungi la finalul listei."""
from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

from ..serializers import photo_url
from ..db import get_db
from ..gamification import level
from ..models import Place, User
from ..schemas import PlaceOut, UserRankOut
from ..serializers import place_out

router = APIRouter(prefix="/leaderboard", tags=["leaderboard"])


@router.get("/places", response_model=list[PlaceOut])
def top_places(category: str | None = None, limit: int = Query(20, ge=1, le=100),
               offset: int = Query(0, ge=0), db: Session = Depends(get_db)):
    # id la final: ordinea e stabilă, deci paginile nu se suprapun și nu sar locuri
    stmt = (select(Place).options(joinedload(Place.mayor))
            .order_by(Place.rating.desc(), Place.checkin_count.desc(), Place.id))
    if category:
        stmt = stmt.where(Place.category == category)
    return [place_out(p) for p in db.scalars(stmt.offset(offset).limit(limit)).all()]


@router.get("/users", response_model=list[UserRankOut])
def top_users(limit: int = Query(20, ge=1, le=100), offset: int = Query(0, ge=0), db: Session = Depends(get_db)):
    users = db.scalars(select(User).order_by(User.xp.desc(), User.id).offset(offset).limit(limit)).all()
    return [UserRankOut(id=u.id, name=u.name, xp=u.xp, level=level(u.xp), rank=offset + i + 1,
                        avatar_emoji=u.avatar_emoji, avatar_color=u.avatar_color,
                        avatar_url=photo_url(u.avatar_path))
            for i, u in enumerate(users)]
