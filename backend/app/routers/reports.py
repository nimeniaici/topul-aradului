"""Sesizări de la cetățeni (gropi, iluminat, gunoi...): cu poză și poziția problemei, vizibile pe hartă."""
import secrets
from datetime import datetime, timedelta

from fastapi import APIRouter, BackgroundTasks, Depends, File, Form, Header, HTTPException, Query, UploadFile
from pydantic import BaseModel, Field
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session, joinedload

from .. import config
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
XP_RESOLVED = 20  # bonus când Primăria rezolvă problema semnalată de tine

STATUSES = {"nou": "Nouă", "in_lucru": "În lucru", "rezolvat": "Rezolvată", "respins": "Respinsă"}


class ReportOut(BaseModel):
    id: int
    category: str
    category_label: str
    description: str
    lat: float
    lng: float
    photo_url: str | None
    status: str
    status_label: str = ""
    user_name: str
    created_at: datetime
    admin_note: str | None = None
    updated_at: datetime | None = None


class ReportCreated(BaseModel):
    report: ReportOut
    xp_gained: int
    total_xp: int
    level: int


def _out(r: Report) -> ReportOut:
    return ReportOut(id=r.id, category=r.category, category_label=REPORT_CATEGORIES.get(r.category, r.category),
                     description=r.description, lat=r.lat, lng=r.lng, photo_url=photo_url(r.photo_path),
                     status=r.status, status_label=STATUSES.get(r.status, r.status), user_name=r.user.name,
                     created_at=r.created_at, admin_note=r.admin_note, updated_at=r.updated_at)


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


# ---------------- Panoul Primăriei ----------------
admin_router = APIRouter(prefix="/admin/reports", tags=["primarie"])


def require_primarie(x_admin_token: str = Header("")) -> None:
    keys = [k for k in (config.PRIMARIE_TOKEN, config.ADMIN_TOKEN) if k]
    if not keys:
        raise HTTPException(404, "Not Found")
    if not any(secrets.compare_digest(x_admin_token, k) for k in keys):
        raise HTTPException(403, "Cheie greșită.")


class StatusIn(BaseModel):
    status: str
    note: str | None = Field(None, max_length=500)


class AdminReportOut(ReportOut):
    user_id: int


@admin_router.get("", response_model=list[AdminReportOut], dependencies=[Depends(require_primarie)])
def admin_list(status: str | None = None, days: int = Query(90, ge=1, le=3650), db: Session = Depends(get_db)):
    q = (select(Report).options(joinedload(Report.user))
         .where(Report.created_at >= utcnow() - timedelta(days=days)).order_by(Report.created_at.desc()).limit(1000))
    if status:
        q = q.where(Report.status == status)
    return [AdminReportOut(**_out(r).model_dump(), user_id=r.user_id) for r in db.scalars(q)]


@admin_router.get("/stats", dependencies=[Depends(require_primarie)])
def admin_stats(db: Session = Depends(get_db)):
    by_status = dict(db.execute(select(Report.status, func.count()).group_by(Report.status)).all())
    by_cat = dict(db.execute(select(Report.category, func.count()).group_by(Report.category)).all())
    week = db.scalar(select(func.count()).select_from(Report).where(Report.created_at >= utcnow() - timedelta(days=7)))
    return {
        "total": sum(by_status.values()),
        "last_7_days": week or 0,
        "by_status": {s: by_status.get(s, 0) for s in STATUSES},
        "by_category": {REPORT_CATEGORIES.get(c, c): n for c, n in by_cat.items()},
    }


@admin_router.patch("/{report_id}", response_model=AdminReportOut, dependencies=[Depends(require_primarie)])
def admin_set_status(report_id: int, body: StatusIn, background: BackgroundTasks, db: Session = Depends(get_db)):
    if body.status not in STATUSES:
        raise HTTPException(400, "Status necunoscut.")
    report = db.get(Report, report_id, options=[joinedload(Report.user)])
    if report is None:
        raise HTTPException(404, "Sesizarea nu există.")
    # bonusul se dă o singură dată: doar cine trece efectiv sesizarea în „rezolvat” (UPDATE atomic)
    newly_resolved = False
    if body.status == "rezolvat":
        newly_resolved = db.execute(
            update(Report).where(Report.id == report_id, Report.status != "rezolvat").values(status="rezolvat")
        ).rowcount == 1
        if newly_resolved:
            db.execute(update(User).where(User.id == report.user_id).values(xp=User.xp + XP_RESOLVED))
    report.status = body.status
    if body.note is not None:
        report.admin_note = body.note.strip() or None
    report.updated_at = utcnow()
    db.commit()
    db.refresh(report)
    out = _out(report)
    background.add_task(hub.broadcast, {
        "type": "report_status", "report_id": report.id, "status": report.status,
        "status_label": out.status_label, "user_id": report.user_id, "user_name": report.user.name,
        "place_name": out.category_label, "lat": report.lat, "lng": report.lng,
        "xp_gained": XP_RESOLVED if newly_resolved else 0, "note": report.admin_note,
    })
    return AdminReportOut(**out.model_dump(), user_id=report.user_id)
