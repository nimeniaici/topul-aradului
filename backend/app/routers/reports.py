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
from ..models import ReportConfirmation, Report, User, utcnow
from ..security import get_current_user
from ..serializers import photo_url
from .checkins import _save_photo

router = APIRouter(prefix="/reports", tags=["reports"])

REPORT_CATEGORIES = {
    "groapa": "Groapă în drum",
    "furtuna": "Copac căzut / pagube de furtună",
    "inundatie": "Apă băltită / inundație",
    "canal": "Capac de canal lipsă",
    "trotuar": "Trotuar stricat",
    "iluminat": "Iluminat stricat",
    "semnalizare": "Semn sau semafor",
    "vandalism": "Vandalism / mobilier urban distrus",
    "gunoi": "Gunoi / mizerie",
    "parcare": "Mașină parcată ilegal",
    "animale": "Animale fără stăpân",
    "altele": "Altceva",
    # ALERTE URGENTE (din „Alerte urgente”): pericol major, au prioritate maximă la Primărie
    "inundatie_majora": "Inundație / revărsarea Mureșului",
    "cutremur": "Cutremur – clădire avariată",
    "gaze": "Scurgere / miros puternic de gaze",
    "explozie": "Explozie / incendiu",
    "prabusire": "Clădire sau structură prăbușită",
    "cablu": "Cablu electric căzut",
    "alunecare": "Surpare de teren / drum prăbușit",
}
# cele care pot pune oameni în pericol apar primele la Primărie
EMERGENCY = {"inundatie_majora", "cutremur", "gaze", "explozie", "prabusire", "cablu", "alunecare"}
URGENT = {"furtuna", "inundatie", "canal", "semnalizare"} | EMERGENCY


def priority(category: str) -> int:
    """0 = alertă majoră (pericol pentru vieți), 1 = urgent, 2 = normal."""
    return 0 if category in EMERGENCY else 1 if category in URGENT else 2
XP_REPORT = 10
XP_CONFIRM = 5
CONFIRMS_NEEDED = 3        # autorul + încă 2 oameni => sesizarea e confirmată și ajunge „aprobată” la Primărie
SAME_SPOT_M = 60           # aceeași problemă = aceeași categorie, la max. 60 m, în ultimele 30 de zile
CONFIRM_RADIUS_M = 150     # ca să confirmi, trebuie să fii lângă problemă
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
    urgent: bool = False
    emergency: bool = False
    priority: int = 2
    confirmations: int = 1
    confirms_needed: int = CONFIRMS_NEEDED
    confirmed: bool = False
    user_name: str
    created_at: datetime
    admin_note: str | None = None
    updated_at: datetime | None = None


class ReportCreated(BaseModel):
    report: ReportOut
    xp_gained: int
    total_xp: int
    level: int
    confirmed_existing: bool = False   # n-am creat una nouă: am confirmat sesizarea existentă de acolo


def _confirm_count(r: Report) -> int:
    from sqlalchemy.orm import object_session
    db = object_session(r)
    n = db.scalar(select(func.count()).select_from(ReportConfirmation).where(ReportConfirmation.report_id == r.id)) if db else 0
    return 1 + (n or 0)


def _out(r: Report) -> ReportOut:
    n = _confirm_count(r)
    return ReportOut(confirmations=n, confirmed=r.category in EMERGENCY or n >= CONFIRMS_NEEDED,id=r.id, category=r.category, category_label=REPORT_CATEGORIES.get(r.category, r.category),
                     description=r.description, lat=r.lat, lng=r.lng, photo_url=photo_url(r.photo_path),
                     status=r.status, status_label=STATUSES.get(r.status, r.status), urgent=r.category in URGENT,
                     emergency=r.category in EMERGENCY, priority=priority(r.category), user_name=r.user.name,
                     created_at=r.created_at, admin_note=r.admin_note, updated_at=r.updated_at)


@router.get("/categories")
def categories():
    return [{"code": c, "label": l, "urgent": c in URGENT, "emergency": c in EMERGENCY} for c, l in REPORT_CATEGORIES.items()]


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
    if recent and category not in EMERGENCY:   # o alertă majoră nu o blocăm niciodată
        raise HTTPException(429, "Ai trimis o sesizare chiar acum. Mai așteaptă puțin.")
    # aceeași problemă, semnalată deja de altcineva chiar aici -> o confirmăm în loc să facem o sesizare nouă
    if category not in EMERGENCY:
        existing = _same_problem(db, category, lat, lng, exclude_user=user.id)
        if existing:
            return _do_confirm(db, existing, user, background)
    path = await _save_photo(photo) if photo and photo.filename else None
    report = Report(user_id=user.id, category=category, description=description.strip(), lat=lat, lng=lng, photo_path=path)
    db.add(report)
    db.flush()
    db.execute(update(User).where(User.id == user.id).values(xp=User.xp + XP_REPORT))
    db.commit()
    db.refresh(user)
    db.refresh(report)
    out = _out(report)
    background.add_task(hub.broadcast, {"type": "alert" if out.emergency else "report", "lat": lat, "lng": lng,
                                        "place_name": out.category_label, "user_name": user.name})
    return ReportCreated(report=out, xp_gained=XP_REPORT, total_xp=user.xp, level=level(user.xp))


def _same_problem(db: Session, category: str, lat: float, lng: float, exclude_user: int) -> Report | None:
    from ..geo import distance_m
    rows = db.scalars(select(Report).where(Report.category == category, Report.status.in_(("nou", "in_lucru")),
                                           Report.created_at >= utcnow() - timedelta(days=30))).all()
    near = [r for r in rows if distance_m(lat, lng, r.lat, r.lng) <= SAME_SPOT_M]
    near.sort(key=lambda r: distance_m(lat, lng, r.lat, r.lng))
    return near[0] if near else None


def _do_confirm(db: Session, report: Report, user: User, background: BackgroundTasks) -> "ReportCreated":
    if report.user_id == user.id:
        raise HTTPException(400, "Ai semnalat deja problema asta. Ai nevoie de alți 2 arădeni care s-o confirme.")
    already = db.scalar(select(ReportConfirmation.id).where(ReportConfirmation.report_id == report.id,
                                                           ReportConfirmation.user_id == user.id))
    if already:
        raise HTTPException(409, "Ai confirmat deja această problemă.")
    db.add(ReportConfirmation(report_id=report.id, user_id=user.id))
    db.execute(update(User).where(User.id == user.id).values(xp=User.xp + XP_CONFIRM))
    db.commit()
    db.refresh(user)
    out = _out(report)
    if out.confirmations == CONFIRMS_NEEDED:   # tocmai a devenit confirmată
        background.add_task(hub.broadcast, {"type": "report", "lat": report.lat, "lng": report.lng,
                                            "place_name": f"Confirmată de {CONFIRMS_NEEDED} arădeni: {out.category_label}",
                                            "user_name": user.name})
    return ReportCreated(report=out, xp_gained=XP_CONFIRM, total_xp=user.xp, level=level(user.xp), confirmed_existing=True)


class ConfirmIn(BaseModel):
    lat: float
    lng: float
    is_mock: bool = False


@router.post("/{report_id}/confirm", response_model=ReportCreated)
def confirm(report_id: int, body: ConfirmIn, background: BackgroundTasks, user: User = Depends(get_current_user),
            db: Session = Depends(get_db)):
    """„Și eu văd problema asta”: confirmi o sesizare de pe hartă, dacă ești lângă ea."""
    from ..geo import distance_m
    r = db.get(Report, report_id)
    if not r or r.status not in ("nou", "in_lucru"):
        raise HTTPException(404, "Sesizarea nu mai e deschisă.")
    if body.is_mock:
        raise HTTPException(403, "Am detectat o locație simulată.")
    d = distance_m(body.lat, body.lng, r.lat, r.lng)
    if not config.SKIP_DISTANCE_CHECK and d > CONFIRM_RADIUS_M:
        raise HTTPException(400, f"Ca să confirmi, trebuie să fii lângă problemă (ești la {round(d)} m).")
    return _do_confirm(db, r, user, background)


@router.get("/mine", response_model=list[ReportOut])
def my_reports(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Sesizările mele, cu statusul și răspunsul Primăriei."""
    rows = db.scalars(select(Report).options(joinedload(Report.user)).where(Report.user_id == user.id)
                      .order_by(Report.created_at.desc()).limit(200)).all()
    return [_out(r) for r in rows]


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
    rows = [AdminReportOut(**_out(r).model_dump(), user_id=r.user_id) for r in db.scalars(q)]
    open_first = {"nou": 0, "in_lucru": 1}
    rows.sort(key=lambda r: (open_first.get(r.status, 2), not r.confirmed, r.priority if r.status in open_first else 2))
    return rows


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
