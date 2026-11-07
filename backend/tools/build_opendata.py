"""Generează fișierele de date deschise folosite de backend, din seturile publicate pentru Hack4Arad:
  - app/ctp_data.json     – rețeaua CTP Arad (29 linii, variante, 299 stații, trasee)
                            sursa: https://baditaflorin.github.io/arad-transport-open-data/ (Telelink / CTP Arad)
  - app/gunoi_data.json   – calendarul de ridicare a deșeurilor pe străzi
                            sursa: https://baditaflorin.github.io/calendar-ridicare-gunoi-arad/ (date publice RETIM)
  - app/turism_data.json  – locațiile turistice ale județului
                            sursa: https://baditaflorin.github.io/hack4arad-turism-arad/ (CNIPT Arad, visitarad.ro)

Rulare:  python tools/build_opendata.py <folder cu fișierele descărcate>
(folderul conține tr_*.json, g_catalog.json, gp/<id>.json, tur_locations.json, tur_trails.json)
"""
import ast
import glob
import json
import os
import sys
from datetime import date

SRC = sys.argv[1]
OUT = os.path.join(os.path.dirname(__file__), "..", "app")
FROM_DATE = "2026-11-01"   # păstrăm doar ridicările viitoare


def load(name):
    with open(os.path.join(SRC, name), encoding="utf-8") as f:
        return json.load(f)


def decode_polyline(s: str) -> list[list[float]]:
    pts, i, lat, lng = [], 0, 0, 0
    while i < len(s):
        for which in (0, 1):
            shift = result = 0
            while True:
                b = ord(s[i]) - 63
                i += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            d = ~(result >> 1) if result & 1 else result >> 1
            if which == 0:
                lat += d
            else:
                lng += d
        pts.append([round(lat / 1e5, 5), round(lng / 1e5, 5)])
    return pts


def thin(pts, min_deg=0.00012):
    """Scoate punctele prea apropiate (~10 m) ca să fie mai ușor de trimis pe telefon."""
    out = []
    for p in pts:
        if not out or abs(p[0] - out[-1][0]) + abs(p[1] - out[-1][1]) > min_deg:
            out.append(p)
    if pts and out[-1] != pts[-1]:
        out.append(pts[-1])
    return out


def build_ctp():
    routes, stops, patterns = load("tr_routes.json"), load("tr_stops.json"), load("tr_patterns.json")
    stop_by_id = {s["id"]: s for s in stops}
    out_routes = []
    for r in routes:
        pats = [p for p in patterns if p["routeId"] == r["id"]]
        color = "#" + r["color"] if r.get("color") else ("#C2185B" if r["mode"] == "tram" else "#455A64")
        ps = []
        for p in sorted(pats, key=lambda p: -p["stopCount"]):
            ids = p["stopIds"]
            if not ids:
                continue
            ps.append({"id": p["patternId"], "index": p["index"], "from": stop_by_id.get(ids[0], {}).get("name", ""),
                       "to": stop_by_id.get(ids[-1], {}).get("name", ""), "stops": [str(i) for i in ids],
                       "shape": thin(decode_polyline(p.get("encodedPolyline") or ""))})
        if not ps:
            continue
        out_routes.append({"id": r["id"], "ref": r["shortName"], "name": r["longName"], "mode": r["mode"],
                           "color": color, "patterns": ps})
    out_stops = {}
    ref_by_route = {r["id"]: r["ref"] for r in out_routes}
    for s in stops:
        refs = sorted({ref_by_route[i] for i in s.get("routeIds", []) if i in ref_by_route})
        out_stops[str(s["id"])] = {"id": str(s["id"]), "name": s["name"], "lat": s["latitude"], "lng": s["longitude"],
                                   "lines": refs, "wheelchair": s.get("wheelchairBoarding", 0) == 1}
    data = {"source": "CTP Arad / Telelink, prin arad-transport-open-data (Hack4Arad 2026)",
            "snapshot": load("tr_manifest.json").get("fetchedAt", ""), "routes": out_routes, "stops": out_stops}
    write("ctp_data.json", data)
    print("ctp:", len(out_routes), "linii,", sum(len(r["patterns"]) for r in out_routes), "variante,", len(out_stops), "stații")


def build_gunoi():
    cat = load("g_catalog.json")
    places, events = [], {}
    for p in cat["places"]:
        places.append({"id": p["id"], "cartier": p["cartier"], "street": p["street_raw"],
                       "aliases": sorted(set(p.get("aliases", [])))})
        f = os.path.join(SRC, "gp", f"{p['id']}.json")
        if not os.path.exists(f):
            continue
        with open(f, encoding="utf-8") as fh:
            evs = json.load(fh).get("events", [])
        by_type: dict[str, list[str]] = {}
        for e in evs:
            if e["date"] >= FROM_DATE:
                by_type.setdefault(e["waste_type"], []).append(e["date"])
        events[str(p["id"])] = {t: sorted(set(d)) for t, d in by_type.items()}
    write("gunoi_data.json", {"source": "RETIM Arad (retim.ro), prin calendar-ridicare-gunoi-arad (Hack4Arad 2026)",
                              "generated_at": cat.get("generated_at", ""), "places": places, "events": events})
    print("gunoi:", len(places), "străzi")


def build_turism():
    def photos(v):
        if isinstance(v, list):
            return v
        try:
            return ast.literal_eval(v) if v else []
        except Exception:
            return []

    locs = []
    for x in load("tur_locations.json"):
        try:
            lat, lng = float(x["latitude"]), float(x["longitude"])
        except (TypeError, ValueError):
            continue
        locs.append({"id": int(x["id"]), "name": x["name"].strip(), "type": x["type"], "category": x["category"],
                     "locality": x.get("locality") or "", "address": x.get("address") or "", "lat": lat, "lng": lng,
                     "description": (x.get("description") or "").strip(), "website": x.get("website") or "",
                     "facebook": x.get("facebook") or "", "photos": photos(x.get("photos"))[:4],
                     "source_url": x.get("source_url") or ""})
    trails = []
    for t in load("tur_trails.json"):
        trails.append({"id": int(t["id"]), "name": t["name"].strip(), "category": t.get("category", ""),
                       "description": (t.get("description") or "").strip(), "map_image_url": t.get("map_image_url") or "",
                       "photos": photos(t.get("photos"))[:3], "source_url": t.get("source_url") or ""})
    write("turism_data.json", {"source": "Centrul Național de Informare și Promovare Turistică al Județului Arad (CNIPT Arad), "
                                         "visitarad.ro; prelucrare pentru Hack4Arad, 2026",
                               "locations": locs, "trails": trails})
    print("turism:", len(locs), "locații,", len(trails), "trasee")


def write(name, data):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    build_ctp()
    build_gunoi()
    build_turism()
    print("gata", date.today())
