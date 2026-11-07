"""Turism în județul Arad: 325 de obiective, locuri de agrement și cazări + 30 de trasee montane,
din datele CNIPT Arad (visitarad.ro), prelucrate pentru Hack4Arad (app/turism_data.json)."""
import json
import unicodedata
from functools import lru_cache
from pathlib import Path

from fastapi import APIRouter, HTTPException, Query

from ..geo import distance_m

router = APIRouter(prefix="/turism", tags=["turism"])
TYPES = {"obiective": "Obiective turistice", "agrement": "Timp liber și agrement", "cazare": "Cazare"}


@lru_cache
def data() -> dict:
    return json.loads((Path(__file__).resolve().parent.parent / "turism_data.json").read_text(encoding="utf-8"))


def _n(s: str) -> str:
    s = unicodedata.normalize("NFKD", s.lower())
    return "".join(c for c in s if not unicodedata.combining(c))


def _short(loc: dict, lat: float | None, lng: float | None) -> dict:
    out = {k: loc[k] for k in ("id", "name", "type", "category", "locality", "address", "lat", "lng")}
    out["photo"] = loc["photos"][0] if loc["photos"] else None
    out["summary"] = (loc["description"][:160].rsplit(" ", 1)[0] + "…") if len(loc["description"]) > 160 else loc["description"]
    out["distance_m"] = round(distance_m(lat, lng, loc["lat"], loc["lng"])) if lat is not None and lng is not None else None
    return out


@router.get("")
def locations(type: str | None = Query(None), q: str | None = Query(None, max_length=60),
              only_arad: bool = False, lat: float | None = None, lng: float | None = None,
              limit: int = Query(400, ge=1, le=400)):
    rows = data()["locations"]
    if type:
        label = TYPES.get(type, type)
        rows = [r for r in rows if r["type"] == label]
    if only_arad:
        rows = [r for r in rows if r["locality"] == "Arad"]
    if q:
        nq = _n(q)
        rows = [r for r in rows if nq in _n(r["name"] + " " + r["category"] + " " + r["locality"])]
    out = [_short(r, lat, lng) for r in rows]
    if lat is not None and lng is not None:
        out.sort(key=lambda r: r["distance_m"])
    return {"locations": out[:limit], "types": [{"code": c, "label": l} for c, l in TYPES.items()],
            "source": data()["source"]}


@router.get("/trasee")
def trails():
    return {"trails": data()["trails"], "source": data()["source"]}


@router.get("/{loc_id}")
def location(loc_id: int):
    loc = next((r for r in data()["locations"] if r["id"] == loc_id), None)
    if not loc:
        raise HTTPException(404, "Locația nu există.")
    return {**loc, "source": data()["source"]}
