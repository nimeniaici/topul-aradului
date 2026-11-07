"""Servicii locale în Arad: farmacii, spitale și clinici, cabinete medicale, școli și grădinițe, poliție și
pompieri, poștă, bănci și bancomate, instituții publice. Date: OpenStreetMap (ODbL), în app/servicii_data.json."""
import json
import unicodedata
from functools import lru_cache
from pathlib import Path

from fastapi import APIRouter, Query

from ..geo import distance_m

router = APIRouter(prefix="/servicii", tags=["servicii"])
TYPES = {
    "farmacie": "Farmacii",
    "spital": "Spitale și clinici",
    "medic": "Cabinete medicale",
    "scoala": "Școli și grădinițe",
    "politie": "Poliție și pompieri",
    "posta": "Poștă",
    "banca": "Bănci și bancomate",
    "public": "Instituții publice",
}


@lru_cache
def data() -> dict:
    f = Path(__file__).resolve().parent.parent / "servicii_data.json"
    return json.loads(f.read_text(encoding="utf-8")) if f.exists() else {"items": [], "source": ""}


def _n(s: str) -> str:
    s = unicodedata.normalize("NFKD", (s or "").lower())
    return "".join(c for c in s if not unicodedata.combining(c))


@router.get("")
def services(type: str | None = None, q: str | None = Query(None, max_length=60), lat: float | None = None,
             lng: float | None = None, limit: int = Query(60, ge=1, le=500)):
    rows = [x for x in data()["items"] if not type or x["type"] == type]
    if q:
        nq = _n(q)
        rows = [x for x in rows if nq in _n(x["name"] + " " + x.get("address", ""))]
    out = []
    for x in rows:
        d = round(distance_m(lat, lng, x["lat"], x["lng"])) if lat is not None and lng is not None else None
        out.append({**x, "distance_m": d})
    if lat is not None and lng is not None:
        out.sort(key=lambda x: x["distance_m"])
    counts = {}
    for x in data()["items"]:
        counts[x["type"]] = counts.get(x["type"], 0) + 1
    return {"items": out[:limit], "types": [{"code": c, "label": l, "count": counts.get(c, 0)} for c, l in TYPES.items()],
            "source": data().get("source", "")}


def nearest(kind: str, lat: float, lng: float, n: int = 3) -> list[dict]:
    """Pentru Iasmi: cele mai apropiate servicii de un tip."""
    rows = [x for x in data()["items"] if x["type"] == kind]
    rows.sort(key=lambda x: distance_m(lat, lng, x["lat"], x["lng"]))
    return [{**x, "distance_m": round(distance_m(lat, lng, x["lat"], x["lng"]))} for x in rows[:n]]
