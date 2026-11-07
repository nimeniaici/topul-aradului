"""Transport CTP Arad: toată rețeaua (8 linii de tramvai + 21 de autobuz, 299 de stații, trasee reale),
sosiri din orarul CTP cu întârzierile în timp real și pozițiile live ale vehiculelor (API-ul public Telelink
folosit de arad-transport.telelink.city), semnalări de la cetățeni și bilete digitale DEMONSTRATIVE (fără plată).
Date statice: app/ctp_data.json (generat de tools/build_opendata.py din arad-transport-open-data)."""
import json
import os
import secrets
import threading
import time
import urllib.request
from datetime import datetime, timedelta, timezone
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
XP_TRANSIT_REPORT = 5
REPORT_KINDS = {"aglomerat": "Foarte aglomerat", "intarziere": "Întârziere", "defect": "Vehicul defect / oprit",
                "curat": "Murdar / neîngrijit", "altele": "Altceva"}
TICKETS = {  # tarifele CTP Arad, urban, valabile de la 1 august 2026 (ctparad.ro/tarife-transport-public-urban)
    "calatorie": {"label": "Bilet o călătorie", "price": 1.0, "minutes": 1440},
    "saptamanal": {"label": "Abonament săptămânal", "price": 5.0, "minutes": 7 * 1440},
    "2saptamani": {"label": "Abonament 2 săptămâni", "price": 10.0, "minutes": 14 * 1440},
    "lunar": {"label": "Abonament lunar", "price": 20.0, "minutes": 30 * 1440},
}
DISPATCH_PHONE = "0749 115 178"
TELELINK = "https://www.telelink.city/api/v1/e39aa927-11c1-435f-92b9-57804f7bc1b4/transport/planner"
LIVE = os.getenv("CTP_LIVE", "1") != "0"      # testele îl opresc, ca să nu depindă de internet
STALE_S = 180                                    # vehiculele neactualizate de 3 minute nu le mai arătăm


@lru_cache
def data() -> dict:
    return json.loads((Path(__file__).resolve().parent.parent / "ctp_data.json").read_text(encoding="utf-8"))


@lru_cache
def _routes_by_id() -> dict[int, dict]:
    return {r["id"]: r for r in data()["routes"]}


def _stop_index() -> dict[str, dict]:
    return data()["stops"]


def _pattern(route: dict | None, index) -> dict | None:
    if not route:
        return None
    return next((p for p in route["patterns"] if p["index"] == index), route["patterns"][0])


# ---------- cache mic pentru API-ul Telelink (toți utilizatorii împart aceleași răspunsuri) ----------
_cache: dict[str, tuple[float, object]] = {}
_lock = threading.Lock()


def _fetch(path: str, ttl: int):
    if not LIVE:
        return None
    now = time.time()
    with _lock:
        hit = _cache.get(path)
        if hit and now - hit[0] < ttl:
            return hit[1]
    try:
        req = urllib.request.Request(TELELINK + path, headers={"User-Agent": "CityConnectArad/1.5 (Hack4Arad)"})
        with urllib.request.urlopen(req, timeout=6) as r:
            val = json.loads(r.read().decode("utf-8"))
    except Exception:
        return hit[1] if hit else None    # dacă pică, folosim ultimul răspuns bun
    with _lock:
        _cache[path] = (now, val)
    return val


def _parse(ts: str | None) -> datetime | None:
    try:
        return datetime.fromisoformat(ts.replace("Z", "+00:00")) if ts else None
    except ValueError:
        return None


def _recent_reports(db: Session, minutes: int = 45) -> list[TransitReport]:
    return db.scalars(select(TransitReport).where(TransitReport.created_at >= utcnow() - timedelta(minutes=minutes))
                      .order_by(TransitReport.created_at.desc())).all()


def _line_out(r: dict) -> dict:
    main = r["patterns"][0]
    stops = _stop_index()
    return {"id": r["id"], "ref": r["ref"], "name": r["name"], "mode": r["mode"], "color": r["color"],
            "from": main["from"], "to": main["to"],
            "stops": [{"id": s, "name": stops[s]["name"], "lat": stops[s]["lat"], "lng": stops[s]["lng"], "min_from_start": 0}
                      for s in main["stops"] if s in stops],
            "shape": main["shape"], "shapes": [p["shape"] for p in r["patterns"]],
            # câmpuri păstrate pentru versiunile vechi ale aplicației
            "headway_peak": 0, "headway_offpeak": 0, "headway_weekend": 0, "first": "", "last": ""}


@router.get("/lines")
def lines(mode: str | None = Query(None, pattern="^(tram|bus)$")):
    """Toate liniile CTP (tramvai + autobuz), cu traseele pentru hartă și stațiile."""
    rs = [r for r in data()["routes"] if not mode or r["mode"] == mode]
    rs.sort(key=lambda r: (r["mode"] != "tram", len(r["ref"]) > 3, r["ref"].zfill(4)))
    return [_line_out(r) for r in rs]


@router.get("/info")
def info():
    return {"tickets": [{"code": c, **t} for c, t in TICKETS.items()], "dispatch_phone": DISPATCH_PHONE,
            "source": data().get("source", ""), "estimated": False,
            "note": "Orele vin din orarul CTP Arad, cu întârzierile în timp real acolo unde vehiculul transmite poziția. "
                    "Date: CTP Arad / Telelink, prin proiectul arad-transport-open-data."}


@router.get("/stops/near")
def near(lat: float, lng: float, limit: int = Query(5, ge=1, le=20)):
    stops = sorted(_stop_index().values(), key=lambda s: distance_m(lat, lng, s["lat"], s["lng"]))[:limit]
    return [{**s, "distance_m": round(distance_m(lat, lng, s["lat"], s["lng"]))} for s in stops]


@router.get("/stops/{stop_id}/next")
def next_trams(stop_id: str, db: Session = Depends(get_db)):
    """Următoarele plecări din stație, din orarul CTP + întârzierea în timp real (Telelink)."""
    stop = _stop_index().get(stop_id)
    if not stop:
        raise HTTPException(404, "Stația nu există.")
    raw = _fetch(f"/stops/{stop_id}/times", ttl=25)
    now = datetime.now(timezone.utc)
    reports = _recent_reports(db)
    out = []
    for group in raw or []:
        ref = group.get("route") or {}
        route = _routes_by_id().get(ref.get("routeId"))
        if not route:
            continue
        pat = _pattern(route, ref.get("index"))
        mins, live = [], False
        for t in group.get("times") or []:
            at = _parse(t.get("scheduledArrival"))
            if not at:
                continue
            at += timedelta(seconds=t.get("arrivalDelay") or 0)
            m = int((at - now).total_seconds() // 60)
            if -1 <= m <= 180:
                mins.append(max(0, m))
                live = live or bool(t.get("realtime"))
        if not mins:
            continue
        rs = [r for r in reports if r.line == route["ref"]]
        out.append({"line": route["ref"], "mode": route["mode"], "color": route["color"],
                    "towards": pat["to"] if pat else route["name"], "minutes": sorted(mins)[:3], "live": live,
                    "crowded": sum(1 for r in rs if r.kind == "aglomerat"),
                    "delayed": sum(1 for r in rs if r.kind in ("intarziere", "defect"))})
    out.sort(key=lambda x: x["minutes"][0])
    return {"stop": stop, "departures": out, "estimated": False, "available": raw is not None}


@router.get("/vehicles")
def vehicles(mode: str | None = Query(None, pattern="^(tram|bus)$")):
    """Pozițiile live ale tramvaielor și autobuzelor CTP (actualizate la ~15 secunde)."""
    raw = _fetch("/vehicles", ttl=15) or []
    now = datetime.now(timezone.utc)
    out = []
    for v in raw:
        route = _routes_by_id().get(v.get("routeId"))
        upd = _parse(v.get("lastUpdated"))
        if not route or not upd or (now - upd).total_seconds() > STALE_S or (mode and route["mode"] != mode):
            continue
        if v.get("latitude") is None or v.get("longitude") is None:
            continue
        pat = _pattern(route, v.get("patternIndex"))
        nxt = _stop_index().get(str(v.get("stopId") or ""))
        out.append({"id": str(v.get("vehicleId") or v.get("label") or ""), "line": route["ref"], "mode": route["mode"],
                    "color": route["color"], "lat": v["latitude"], "lng": v["longitude"], "heading": v.get("heading"),
                    "towards": pat["to"] if pat else "", "next_stop": nxt["name"] if nxt else None,
                    "delay_min": round((v.get("delaySeconds") or 0) / 60), "updated": upd.isoformat()})
    return {"vehicles": out, "available": LIVE and bool(raw), "source": "CTP Arad / Telelink (timp real)"}


class TransitReportIn(BaseModel):
    line: str = Field(max_length=20)
    kind: str
    stop_id: str | None = Field(None, max_length=40)
    note: str = Field("", max_length=300)


@router.post("/reports", status_code=201)
def report(body: TransitReportIn, background: BackgroundTasks, user: User = Depends(get_current_user),
           db: Session = Depends(get_db)):
    if body.kind not in REPORT_KINDS:
        raise HTTPException(400, "Tip necunoscut.")
    if body.line not in {r["ref"] for r in data()["routes"]}:
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
