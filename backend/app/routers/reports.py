"""Sesizări de la cetățeni (gropi, iluminat, gunoi...): cu poză și poziția problemei, vizibile pe hartă."""
from datetime import datetime, timedelta

from fastapi import APIRouter, BackgroundTasks, Depends, File, Form, HTTPException, Query, UploadFile
from pydantic import BaseModel
from sqlalchemy import select, update
from sqlalchemy.orm import Session, joinedload

from ..db import get_db
from ..gamification import level
from ..live import hub
from ..models import Report, User, utcnow
from ..security import get_current_user
from ..serializers import photo_url
from .checkins import _save_photo

router = APIRouter(prefix="/reports", tags=["reports"])

REPORT_CATEGORIES = {
    "groapa": "Groapă în drum",
    "trotuar": "Trotuar stricat",
    "iluminat": "Iluminat stricat",
    "gunoi": "Gunoi / mizerie",
    "semnalizare": "Semn sau semafor",
    "altele": "Altceva",
}
XP_REPORT = 10


class ReportOut(BaseModel):
    id: int
    category: str
    category_label: str
    description: str
    lat: float
    lng: float
    photo_url: str | None
    status: str
    user_name: str
    created_at: datetime


class ReportCreated(BaseModel):
    report: ReportOut
    xp_gained: int
    total_xp: int
    level: int


def _out(r: Report) -> ReportOut:
    return ReportOut(id=r.id, category=r.category, category_label=REPORT_CATEGORIES.get(r.category, r.category),
                     description=r.description, lat=r.lat, lng=r.lng, photo_url=photo_url(r.photo_path),
                     status=r.status, user_name=r.user.name, created_at=r.created_at)


@router.get("/categories")
def categories():
    return [{"code": c, "label": l} for c, l in REPORT_CATEGORIES.items()]


@router.post("", response_model=ReportCreated, status_code=201)
async def create_report(
    background: BackgroundTasks,
    category: str = Form(...),
    lat: float = Form(...),
    lng: float = Form(...),
    description: str = Form("", max_length=500),
    is_mock: bool = Form(False),
    photo: UploadFile | None = File(None),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if category not in REPORT_CATEGORIES:
        raise HTTPException(400, "Categorie necunoscută.")
    if is_mock:
        raise HTTPException(403, "Am detectat o locație simulată.")
    if not (45.9 < lat < 46.7 and 20.9 < lng < 22.7):
        raise HTTPException(400, "Sesizările se pot face doar în județul Arad.")
    recent = db.scalar(select(Report.id).where(Report.user_id == user.id,
                                              Report.created_at >= utcnow() - timedelta(seconds=30)).limit(1))
    if recent:
        raise HTTPException(429, "Ai trimis o sesizare chiar acum. Mai așteaptă puțin.")
    path = await _save_photo(photo) if photo and photo.filename else None
    report = Report(user_id=user.id, category=category, description=description.strip(), lat=lat, lng=lng, photo_path=path)
    db.add(report)
    db.flush()
    db.execute(update(User).where(User.id == user.id).values(xp=User.xp + XP_REPORT))
    db.commit()
    db.refresh(user)
    db.refresh(report)
    out = _out(report)
    background.add_task(hub.broadcast, {"type": "report", "lat": lat, "lng": lng, "place_name": out.category_label,
                                        "user_name": user.name})
    return ReportCreated(report=out, xp_gained=XP_REPORT, total_xp=user.xp, level=level(user.xp))


@router.get("", response_model=list[ReportOut])
def list_reports(days: int = Query(30, ge=1, le=365), db: Session = Depends(get_db)):
    since = utcnow() - timedelta(days=days)
    rows = db.scalars(select(Report).options(joinedload(Report.user))
                      .where(Report.created_at >= since).order_by(Report.created_at.desc()).limit(300)).all()
    return [_out(r) for r in rows]
