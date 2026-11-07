"""Calendarul de ridicare a deșeurilor în Arad: cauți strada → afli când vine mașina pentru fiecare tip
(menajer, bio, hârtie, plastic/metal, campanii pentru voluminoase, textile, periculoase).
Date: RETIM Arad, prin calendar-ridicare-gunoi-arad (app/gunoi_data.json, generat de tools/build_opendata.py)."""
import json
import unicodedata
from datetime import date, datetime, timedelta
from functools import lru_cache
from pathlib import Path
from zoneinfo import ZoneInfo

from fastapi import APIRouter, HTTPException, Query

router = APIRouter(prefix="/gunoi", tags=["gunoi"])
TZ = ZoneInfo("Europe/Bucharest")
TYPES = {  # cod -> (denumire, ce pui, culoarea pubelei/sacului)
    "residual": ("Menajer (rezidual)", "Resturi care nu se reciclează", "#546E7A"),
    "bio": ("Biodegradabil", "Resturi de mâncare, coji, iarbă, frunze", "#6D4C41"),
    "paper": ("Hârtie și carton", "Sacul albastru: ziare, cutii, carton pliat", "#1E88E5"),
    "plastic_metal": ("Plastic și metal", "Sacul galben: PET-uri, doze, cutii de lapte", "#FBC02D"),
    "bulky": ("Voluminoase (campanie)", "Mobilă, saltele, obiecte mari", "#8E24AA"),
    "textile": ("Textile (campanie)", "Haine, încălțăminte, materiale", "#D81B60"),
    "hazardous": ("Periculoase (campanie)", "Baterii, vopsele, uleiuri, becuri", "#E53935"),
}
WEEKDAYS = ["luni", "marți", "miercuri", "joi", "vineri", "sâmbătă", "duminică"]


def norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s.lower())
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = " " + "".join(c if c.isalnum() else " " for c in s) + " "
    for w in (" strada ", " str ", " calea ", " bulevardul ", " bd ", " piata ", " aleea "):
        s = s.replace(w, " ")
    return " ".join(s.split())


@lru_cache
def data() -> dict:
    d = json.loads((Path(__file__).resolve().parent.parent / "gunoi_data.json").read_text(encoding="utf-8"))
    for p in d["places"]:
        p["_keys"] = {norm(p["street"]), *(norm(a) for a in p["aliases"])}
    d["_by_id"] = {p["id"]: p for p in d["places"]}
    return d


def search_places(q: str, limit: int = 15) -> list[dict]:
    nq = norm(q)
    if len(nq) < 2:
        return []
    scored = []
    for p in data()["places"]:
        street = norm(p["street"])
        if street == nq:
            score = 0
        elif street.startswith(nq):
            score = 1
        elif any(nq in k for k in p["_keys"]):
            score = 2
        elif all(w in " ".join(p["_keys"]) for w in nq.split()):
            score = 3
        else:
            continue
        scored.append((score, len(street), p))
    scored.sort(key=lambda x: (x[0], x[1]))
    return [_place_out(p) for _, _, p in scored[:limit]]


def _place_out(p: dict) -> dict:
    return {"id": p["id"], "street": p["street"], "cartier": p["cartier"]}


def _when(d: date, today: date) -> str:
    diff = (d - today).days
    if diff == 0:
        return "azi"
    if diff == 1:
        return "mâine"
    return f"{WEEKDAYS[d.weekday()]}, {d.day:02d}.{d.month:02d}"


def schedule(place_id: int, today: date | None = None) -> dict:
    p = data()["_by_id"].get(place_id)
    if not p:
        raise HTTPException(404, "Strada nu există în calendar.")
    today = today or datetime.now(TZ).date()
    evs = data()["events"].get(str(place_id), {})
    fractions, upcoming = [], []
    for code, (label, hint, color) in TYPES.items():
        dates = [date.fromisoformat(x) for x in evs.get(code, []) if date.fromisoformat(x) >= today]
        if not dates:
            continue
        nxt = dates[0]
        weekly = [x for x in dates if x <= today + timedelta(days=28)]
        fractions.append({"type": code, "label": label, "hint": hint, "color": color, "next": nxt.isoformat(),
                          "next_label": _when(nxt, today), "in_days": (nxt - today).days,
                          "dates": [x.isoformat() for x in dates[:6]],
                          "weekday": WEEKDAYS[nxt.weekday()] if len({x.weekday() for x in weekly}) == 1 and len(weekly) >= 3 else None})
        for x in dates:
            if x <= today + timedelta(days=14):
                upcoming.append({"date": x.isoformat(), "label": _when(x, today), "type": code, "type_label": label, "color": color})
    fractions.sort(key=lambda f: f["in_days"])
    upcoming.sort(key=lambda u: u["date"])
    return {"place": _place_out(p), "fractions": fractions, "upcoming": upcoming,
            "valid_until": "2026-12-31", "source": data()["source"],
            "note": "Mașina trece de obicei de la ora 7:00. Scoate pubela cu o seară înainte."}


@router.get("/search")
def search(q: str = Query(..., min_length=2, max_length=60)):
    return search_places(q)


@router.get("/cartiere")
def cartiere():
    out: dict[str, int] = {}
    for p in data()["places"]:
        out[p["cartier"]] = out.get(p["cartier"], 0) + 1
    return [{"name": k, "streets": v} for k, v in sorted(out.items())]


@router.get("/{place_id}")
def place(place_id: int):
    return schedule(place_id)


def answer_for(text: str) -> str | None:
    """Pentru Iasmi: „când vine gunoiul pe strada X?” → răspuns scurt, sau None dacă nu găsim strada."""
    nt = norm(text)
    best = None
    for p in data()["places"]:
        st = norm(p["street"])
        if len(st) >= 4 and f" {st} " in f" {nt} " and (not best or len(st) > len(norm(best["street"]))):
            best = p
    if not best:
        return None
    s = schedule(best["id"])
    if not s["fractions"]:
        return None
    parts = [f"{f['label'].split(' (')[0].lower()} {f['next_label']}" for f in s["fractions"][:4]]
    return (f"Pe {best['street']} ({best['cartier']}) următoarele ridicări sunt: " + "; ".join(parts) +
            ". Mașina trece de la ora 7, deci scoate pubela cu o seară înainte. Calendarul complet e în Acasă → Gunoi.")
