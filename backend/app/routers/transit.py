"""Transport CTP Arad: linii de tramvai cu stații (trasee reale din OpenStreetMap), următorul tramvai
din stație (ESTIMAT după frecvențe – CTP nu publică orarele într-un format deschis), semnalări live de
aglomerație/întârziere de la cetățeni și bilete digitale DEMONSTRATIVE (fără plată).
Datele: app/transit_data.json (generat de tools/build_transit.py)."""
import json

import secrets
from datetime import datetime, timedelta
from functools import lru_cache
from pathlib import Path
from zoneinfo import ZoneInfo

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from ..db import get_db
from ..geo import distance_m
from ..live import hub
from ..models import TransitReport, TransitTicket, User, utcnow
from ..security import get_current_user

router = APIRouter(prefix="/transit", tags=["transport"])
TZ = ZoneInfo("Europe/Bucharest")
SPEED_M_PER_MIN = 300          # ~18 km/h viteză comercială medie a tramvaiului (estimare)
XP_TRANSIT_REPORT = 5
REPORT_KINDS = {"aglomerat": "Foarte aglomerat", "intarziere": "Întârziere", "defect": "Tramvai defect / oprit",
                "curat": "Murdar / neîngrijit", "altele": "Altceva"}
TICKETS = {  # tarifele CTP Arad, urban, valabile de la 1 august 2026 (ctparad.ro/tarife-transport-public-urban)
    "calatorie": {"label": "Bilet o călătorie", "price": 1.0, "minutes": 1440},
    "saptamanal": {"label": "Abonament săptămânal", "price": 5.0, "minutes": 7 * 1440},
    "2saptamani": {"label": "Abonament 2 săptămâni", "price": 10.0, "minutes": 14 * 1440},
    "lunar": {"label": "Abonament lunar", "price": 20.0, "minutes": 30 * 1440},
}
DISPATCH_PHONE = "0749 115 178"


@lru_cache
def data() -> dict:
    return json.loads((Path(__file__).resolve().parent.parent / "transit_data.json").read_text(encoding="utf-8"))


def _stop_index() -> dict[str, dict]:
    idx: dict[str, dict] = {}
    for line in data()["lines"]:
        for s in line["stops"]:
            st = idx.setdefault(s["id"], {**s, "lines": []})
            if line["ref"] not in st["lines"]:
                st["lines"].append(line["ref"])
    return idx


def _headway(line: dict, now: datetime) -> int:
    h = now.hour
    weekend = now.weekday() >= 5
    if weekend:
        return line["headway_weekend"]
    return line["headway_peak"] if (6 <= h < 9 or 14 <= h < 18) else line["headway_offpeak"]


def _next_departures(line: dict, stop_id: str, now: datetime, count: int = 3) -> list[int]:
    """Minute până la următoarele tramvaie în stație (estimare: plecări din capăt la interval fix)."""
    offsets = {s["id"]: s["min_from_start"] for s in line["stops"]}
    if stop_id not in offsets:
        return []
    first_h, first_m = map(int, line["first"].split(":"))
    last_h, last_m = map(int, line["last"].split(":"))
    start = now.replace(hour=first_h, minute=first_m, second=0, microsecond=0)
    end = now.replace(hour=last_h, minute=last_m, second=0, microsecond=0)
    out, t = [], start
    while t <= end and len(out) < count:
        arrive = t + timedelta(minutes=offsets[stop_id])
        if arrive >= now:
            out.append(int((arrive - now).total_seconds() // 60))
        t += timedelta(minutes=_headway(line, t))
    return out


def _recent_reports(db: Session, minutes: int = 45) -> list[TransitReport]:
    return db.scalars(select(TransitReport).where(TransitReport.created_at >= utcnow() - timedelta(minutes=minutes))
                      .order_by(TransitReport.created_at.desc())).all()


@router.get("/lines")
def lines():
    """Liniile de tramvai, cu traseul (pentru hartă) și stațiile."""
    return [{k: v for k, v in line.items()} for line in data()["lines"]]


@router.get("/info")
def info():
    return {"tickets": [{"code": c, **t} for c, t in TICKETS.items()], "dispatch_phone": DISPATCH_PHONE,
            "source": data().get("source", ""), "estimated": True,
            "note": "Orele sunt estimate după frecvența obișnuită a liniilor; CTP Arad nu publică orarele într-un format deschis. "
                    "Pentru orarul exact, vezi afișajul din stație sau sună la dispeceratul CTP."}


@router.get("/stops/near")
def near(lat: float, lng: float, limit: int = Query(5, ge=1, le=20)):
    stops = sorted(_stop_index().values(), key=lambda s: distance_m(lat, lng, s["lat"], s["lng"]))[:limit]
    return [{**s, "distance_m": round(distance_m(lat, lng, s["lat"], s["lng"]))} for s in stops]


@router.get("/stops/{stop_id}/next")
def next_trams(stop_id: str, db: Session = Depends(get_db)):
    stop = _stop_index().get(stop_id)
    if not stop:
        raise HTTPException(404, "Stația nu există.")
    now = datetime.now(TZ).replace(tzinfo=None)
    reports = _recent_reports(db)
    out = []
    for line in data()["lines"]:
        mins = _next_departures(line, stop_id, now)
        if not mins:
            continue
        rs = [r for r in reports if r.line == line["ref"]]
        out.append({
            "line": line["ref"], "color": line["color"], "towards": line["to"], "minutes": mins,
            "crowded": sum(1 for r in rs if r.kind == "aglomerat"),
            "delayed": sum(1 for r in rs if r.kind in ("intarziere", "defect")),
        })
    out.sort(key=lambda x: x["minutes"][0])
    return {"stop": stop, "departures": out, "estimated": True}


class TransitReportIn(BaseModel):
    line: str = Field(max_length=10)
    kind: str
    stop_id: str | None = Field(None, max_length=40)
    note: str = Field("", max_length=300)


@router.post("/reports", status_code=201)
def report(body: TransitReportIn, background: BackgroundTasks, user: User = Depends(get_current_user),
           db: Session = Depends(get_db)):
    if body.kind not in REPORT_KINDS:
        raise HTTPException(400, "Tip necunoscut.")
    if body.line not in {l["ref"] for l in data()["lines"]}:
        raise HTTPException(400, "Linie necunoscută.")
    recent = db.scalar(select(TransitReport.id).where(TransitReport.user_id == user.id,
                                                     TransitReport.created_at >= utcnow() - timedelta(minutes=2)).limit(1))
    if recent:
        raise HTTPException(429, "Ai semnalat ceva chiar acum. Mai așteaptă puțin.")
    stop = _stop_index().get(body.stop_id or "")
    r = TransitReport(user_id=user.id, line=body.line, kind=body.kind, stop_id=body.stop_id,
                      stop_name=stop["name"] if stop else None, note=body.note.strip())
    db.add(r)
    db.execute(update(User).where(User.id == user.id).values(xp=User.xp + XP_TRANSIT_REPORT))
    db.commit()
    label = REPORT_KINDS[body.kind]
    background.add_task(hub.broadcast, {"type": "transit", "place_name": f"Linia {body.line}: {label.lower()}"
                                        + (f" ({stop['name']})" if stop else ""), "user_name": user.name})
    return {"detail": f"Mulțumim! Am semnalat „{label}” pe linia {body.line}. +{XP_TRANSIT_REPORT} XP"}


@router.get("/reports")
def recent(minutes: int = Query(60, ge=5, le=1440), db: Session = Depends(get_db)):
    return [{"line": r.line, "kind": r.kind, "label": REPORT_KINDS.get(r.kind, r.kind), "stop_name": r.stop_name,
             "note": r.note, "created_at": r.created_at.isoformat()} for r in _recent_reports(db, minutes)]


# ---------- bilete DEMO (fără plată reală) ----------
class TicketIn(BaseModel):
    type: str


def _ticket_out(t: TransitTicket) -> dict:
    info = TICKETS.get(t.type, {"label": t.type, "price": 0})
    return {"id": t.id, "type": t.type, "label": info["label"], "price": info["price"], "code": t.code,
            "valid_from": t.created_at.isoformat(), "valid_until": t.valid_until.isoformat(),
            "active": t.valid_until > utcnow(), "demo": True}


@router.post("/tickets", status_code=201)
def buy_ticket(body: TicketIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Bilet DEMONSTRATIV: arată cum ar arăta biletul digital; nu se plătește nimic și nu e valabil în tramvai."""
    info = TICKETS.get(body.type)
    if not info:
        raise HTTPException(400, "Tip de bilet necunoscut.")
    t = TransitTicket(user_id=user.id, type=body.type, code="CC-" + secrets.token_hex(5).upper(),
                      valid_until=utcnow() + timedelta(minutes=info["minutes"]))
    db.add(t)
    db.commit()
    db.refresh(t)
    return _ticket_out(t)


@router.get("/tickets/mine")
def my_tickets(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    rows = db.scalars(select(TransitTicket).where(TransitTicket.user_id == user.id)
                      .order_by(TransitTicket.created_at.desc()).limit(20)).all()
    return [_ticket_out(t) for t in rows]
