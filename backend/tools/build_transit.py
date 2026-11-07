"""Construiește app/transit_data.json din traseele de tramvai CTP Arad din OpenStreetMap (© contribuitorii OSM, ODbL).

Utilizare:  python tools/build_transit.py routes_geom.json stop_names.json
- routes_geom.json: {relation_id: element} din Overpass (`relation(ID);out geom;`)
- stop_names.json:  elementele nodurilor-stație cu tag-uri (`relation(ID);node(r);out tags;`)

Orarele NU sunt publice în format deschis, așa că frecvențele de mai jos sunt ESTIMĂRI marcate ca atare în aplicație.
"""
import json
import math
import sys
from pathlib import Path

# frecvențe estimate (minute) și program: liniile urbane vs. linia suburbană spre Ghioroc
URBAN = {"headway_peak": 12, "headway_offpeak": 18, "headway_weekend": 25, "first": "05:00", "last": "23:00"}
SUBURBAN = {"headway_peak": 30, "headway_offpeak": 45, "headway_weekend": 60, "first": "05:00", "last": "22:00"}
COLORS = {"1": "#C2185B", "3": "#7B1FA2", "6": "#1565C0", "7": "#00838F", "11": "#2E7D32",
          "12": "#558B2F", "15": "#EF6C00", "16": "#6D4C41"}
SPEED_M_PER_MIN = 300


def dist(a, b):
    R = 6371000
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dl = math.radians(b[1] - a[1])
    x = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(x))


def chain(ways):
    """Leagă segmentele traseului în ordine, întorcându-le când e nevoie."""
    line = []
    for i, w in enumerate(ways):
        pts = [(p["lat"], p["lon"]) for p in w]
        if not line:
            line = pts
            continue
        if i == 1:
            # primul segment poate fi întors: alegem capătul lui cel mai apropiat de segmentul al doilea
            ends = min((dist(a, b), ra) for ra, a in ((False, line[-1]), (True, line[0])) for b in (pts[0], pts[-1]))
            if ends[1]:
                line.reverse()
        if dist(line[-1], pts[-1]) < dist(line[-1], pts[0]):
            pts.reverse()
        line += pts[1:]
    out = [line[0]]
    for p in line[1:]:
        if dist(out[-1], p) >= 25:
            out.append(p)
    return [[round(a, 6), round(b, 6)] for a, b in out]


def main(routes_path, names_path, out_path):
    routes = json.load(open(routes_path, encoding="utf-8"))
    names = {}
    for e in json.load(open(names_path, encoding="utf-8")):
        if e.get("tags", {}).get("name"):
            names[e["id"]] = e["tags"]["name"]
    lines = []
    for rid, rel in routes.items():
        t = rel["tags"]
        ref = t.get("ref", "").lower()
        if not ref:
            continue
        stops, seen = [], set()
        for m in rel["members"]:
            if m["type"] == "node" and m["role"].startswith("stop"):
                name = names.get(m["ref"])
                if not name or name in seen:
                    continue
                seen.add(name)
                stops.append({"id": f"s{m['ref']}", "name": name, "lat": round(m["lat"], 6), "lng": round(m["lon"], 6)})
        ways = [m["geometry"] for m in rel["members"] if m["type"] == "way" and m["role"] in ("", "forward", "backward") and "geometry" in m]
        if len(stops) < 2:
            continue
        # în afara orașului (spre Ghioroc) tramvaiul merge mai repede
        speed = 480 if "Ghioroc" in t.get("name", "") else SPEED_M_PER_MIN
        acc = 0.0
        for i, s in enumerate(stops):
            if i:
                acc += dist((stops[i - 1]["lat"], stops[i - 1]["lng"]), (s["lat"], s["lng"])) * 1.15
            s["min_from_start"] = round(acc / speed)
        shape = chain(ways) if ways else [[s["lat"], s["lng"]] for s in stops]
        first = (stops[0]["lat"], stops[0]["lng"])
        if dist(tuple(shape[-1]), first) < dist(tuple(shape[0]), first):
            shape.reverse()  # traseul pornește din prima stație
        sched = SUBURBAN if "Ghioroc" in t.get("name", "") else URBAN
        lines.append({"ref": t["ref"], "name": t.get("name", ""), "from": t.get("from") or stops[0]["name"],
                      "to": t.get("to") or stops[-1]["name"], "color": COLORS.get(ref, "#455A64"),
                      "osm_relation": int(rid), "stops": stops, "shape": shape, **sched})
    lines.sort(key=lambda l: (len(l["ref"]), l["ref"]))
    out = {"source": "Trasee și stații: © contribuitorii OpenStreetMap (ODbL). Orare: estimate.", "lines": lines}
    Path(out_path).write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    for l in lines:
        print(l["ref"], l["from"], "->", l["to"], len(l["stops"]), "stații", len(l["shape"]), "puncte",
              l["stops"][-1]["min_from_start"], "min")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "app/transit_data.json")
