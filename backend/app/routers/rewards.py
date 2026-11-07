"""Recompense pentru misiuni (DEMONSTRATIVE – arată cum ar funcționa un parteneriat cu CTP și cu localurile):
  - check-in în stațiile CTP  -> abonament gratuit (săptămânal, apoi lunar)
  - vizite cu poză LIVE la același restaurant / cafenea -> 10% reducere acolo
Poza se face doar cu camera din aplicație (nu din galerie), ca dovadă că ai fost acolo."""
import secrets
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from .. import config
from ..db import get_db
from ..geo import distance_m
from ..models import Checkin, Place, RewardVoucher, TransitCheckin, User, utcnow
from ..security import get_current_user

router = APIRouter(tags=["recompense"])
STOP_RADIUS_M = 80          # trebuie să fii chiar în stație
STOP_COOLDOWN_MIN = 30      # aceeași stație o dată la 30 de minute
XP_STOP_CHECKIN = 3
FOOD_PLACES = {"restaurant", "cafenea", "fastfood", "cofetarie", "gelaterie", "terasa", "bar"}

TRANSIT_REWARDS = [
    # cod, prag, titlu, descriere
    ("ctp_saptamana", 50, "O săptămână de transport gratuit", "50 de check-in-uri în stațiile CTP → abonament săptămânal gratuit"),
    ("ctp_luna", 150, "O lună de transport gratuit", "150 de check-in-uri în stațiile CTP → abonament lunar gratuit"),
]
FOOD_GOAL = 10
FOOD_REWARD = ("local_10", "10% reducere la localul preferat",
               f"{FOOD_GOAL} vizite cu poză live la același restaurant sau cafenea → 10% reducere acolo")


def _voucher(db: Session, user_id: int, reward: str, place_id: int | None = None) -> RewardVoucher:
    v = db.scalar(select(RewardVoucher).where(RewardVoucher.user_id == user_id, RewardVoucher.reward == reward,
                                              RewardVoucher.place_id.is_(None) if place_id is None else RewardVoucher.place_id == place_id))
    if not v:
        v = RewardVoucher(user_id=user_id, reward=reward, place_id=place_id, code="CC-" + secrets.token_hex(4).upper())
        db.add(v)
        db.commit()
        db.refresh(v)
    return v


class StopCheckinIn(BaseModel):
    stop_id: str
    lat: float
    lng: float
    is_mock: bool = False


@router.post("/transit/checkin", status_code=201)
def stop_checkin(body: StopCheckinIn, me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    from .transit import _stop_index
    stop = _stop_index().get(body.stop_id)
    if not stop:
        raise HTTPException(404, "Stația nu există.")
    if body.is_mock:
        raise HTTPException(403, "Am detectat o locație simulată.")
    d = distance_m(body.lat, body.lng, stop["lat"], stop["lng"])
    if not config.SKIP_DISTANCE_CHECK and d > STOP_RADIUS_M:
        raise HTTPException(400, f"Trebuie să fii în stație (ești la {round(d)} m de {stop['name']}).")
    recent = db.scalar(select(TransitCheckin.id).where(
        TransitCheckin.user_id == me.id, TransitCheckin.stop_id == body.stop_id,
        TransitCheckin.created_at >= utcnow() - timedelta(minutes=STOP_COOLDOWN_MIN)).limit(1))
    if recent:
        raise HTTPException(429, "Ai dat deja check-in în stația asta. Revino peste o jumătate de oră.")
    db.add(TransitCheckin(user_id=me.id, stop_id=body.stop_id, stop_name=stop["name"][:80]))
    db.execute(update(User).where(User.id == me.id).values(xp=User.xp + XP_STOP_CHECKIN))
    db.commit()
    total = db.scalar(select(func.count()).select_from(TransitCheckin).where(TransitCheckin.user_id == me.id)) or 0
    nxt = next(((g, t) for _, g, t, _ in TRANSIT_REWARDS if total < g), None)
    msg = f"Check-in în stația {stop['name']}! +{XP_STOP_CHECKIN} XP · {total} stații în total."
    if nxt:
        msg += f" Mai ai {nxt[0] - total} până la „{nxt[1]}”."
    return {"detail": msg, "total": total}


@router.get("/me/rewards")
def my_rewards(me: User = Depends(get_current_user), db: Session = Depends(get_db)):
    stops = db.scalar(select(func.count()).select_from(TransitCheckin).where(TransitCheckin.user_id == me.id)) or 0
    out = []
    for code, goal, title, desc in TRANSIT_REWARDS:
        unlocked = stops >= goal
        v = _voucher(db, me.id, code) if unlocked else None
        out.append({"code": code, "kind": "transport", "title": title, "description": desc, "progress": min(stops, goal),
                    "goal": goal, "unlocked": unlocked, "voucher": v.code if v else None, "place_id": None,
                    "place_name": None, "demo": True})
    # localurile: vizitele CU POZĂ, pe fiecare local în parte
    rows = db.execute(select(Place.id, Place.name, func.count(Checkin.id))
                      .join(Checkin, Checkin.place_id == Place.id)
                      .where(Checkin.user_id == me.id, Checkin.photo_path.is_not(None), Place.category.in_(FOOD_PLACES))
                      .group_by(Place.id, Place.name).order_by(func.count(Checkin.id).desc()).limit(5)).all()
    code, title, desc = FOOD_REWARD
    if not rows:
        out.append({"code": code, "kind": "local", "title": title, "description": desc, "progress": 0, "goal": FOOD_GOAL,
                    "unlocked": False, "voucher": None, "place_id": None, "place_name": None, "demo": True})
    for pid, name, n in rows:
        unlocked = n >= FOOD_GOAL
        v = _voucher(db, me.id, code, pid) if unlocked else None
        out.append({"code": code, "kind": "local", "title": f"10% reducere la {name}", "description": desc,
                    "progress": min(n, FOOD_GOAL), "goal": FOOD_GOAL, "unlocked": unlocked, "voucher": v.code if v else None,
                    "place_id": pid, "place_name": name, "demo": True})
    return {"rewards": out, "stop_checkins": stops,
            "note": "Recompense demonstrative: arată cum ar funcționa un parteneriat cu CTP Arad și cu localurile. "
                    "Pozele de dovadă se fac doar cu camera, pe loc."}
