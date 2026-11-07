"""Planificator simplu de rute cu transportul CTP Arad (pentru Iasmi): caută o linie directă între
stațiile de lângă tine și cele de lângă destinație, apoi o variantă cu o schimbare. Când nu există
o variantă bună, recomandă mersul pe jos sau Bolt / Uber / taxi.
Date: app/ctp_data.json (rețeaua CTP) + sosirile live (Telelink) pentru „următorul vine în X min”."""
from datetime import datetime, timedelta, timezone

from .geo import distance_m

WALK_M_PER_MIN = 75          # ~4.5 km/h, cu tot cu așteptatul la semafoare
MAX_WALK_TO_STOP = 800       # cât acceptăm să mergi până la / de la stație
WALK_ONLY_UNDER = 1100       # sub ~15 min pe jos nu are rost să aștepți tramvaiul
SPEED = {"tram": 300, "bus": 350}   # m/min viteză comercială (estimare)
TRANSFER_PENALTY = 6         # minute „pierdute” la o schimbare


def _data():
    from .routers.transit import data
    return data()


def _walk_min(m: float) -> int:
    return max(1, round(m * 1.25 / WALK_M_PER_MIN))


def _patterns():
    for r in _data()["routes"]:
        for p in r["patterns"]:
            yield r, p


def _ride_min(route: dict, stops: list[str], i: int, j: int) -> int:
    st = _data()["stops"]
    dist = sum(distance_m(st[a]["lat"], st[a]["lng"], st[b]["lat"], st[b]["lng"])
               for a, b in zip(stops[i:j], stops[i + 1:j + 1]) if a in st and b in st)
    return max(2, round(dist * 1.2 / SPEED.get(route["mode"], 320) + 0.4 * (j - i)))


_NEIGH: dict[str, list[str]] | None = None


def _neighbours() -> dict[str, list[str]]:
    """Stațiile la max. 200 m una de alta (peronul de vizavi, colțul străzii) – acolo se poate schimba linia."""
    global _NEIGH
    if _NEIGH is None:
        st = _data()["stops"]
        _NEIGH = {a: [b for b, sb in st.items() if distance_m(sa["lat"], sa["lng"], sb["lat"], sb["lng"]) <= 200]
                  for a, sa in st.items()}
    return _NEIGH


def _near_stops(lat: float, lng: float) -> dict[str, float]:
    return {sid: d for sid, s in _data()["stops"].items()
            if (d := distance_m(lat, lng, s["lat"], s["lng"])) <= MAX_WALK_TO_STOP}


def _vehicle(route: dict) -> str:
    return ("tramvaiul " if route["mode"] == "tram" else "autobuzul ") + route["ref"]


def _next_departure(stop_id: str, route: dict) -> int | None:
    """Minute până la următorul vehicul al liniei din stația respectivă (din orarul live), dacă îl știm."""
    try:
        from .routers.transit import _fetch, _parse
        raw = _fetch(f"/stops/{stop_id}/times", ttl=25) or []
    except Exception:
        return None
    now = datetime.now(timezone.utc)
    best = None
    for g in raw:
        if (g.get("route") or {}).get("routeId") != route["id"]:
            continue
        for t in g.get("times") or []:
            at = _parse(t.get("scheduledArrival"))
            if at:
                m = int(((at + timedelta(seconds=t.get("arrivalDelay") or 0)) - now).total_seconds() // 60)
                if m >= 0 and (best is None or m < best):
                    best = m
    return best


def plan(from_lat: float, from_lng: float, to_lat: float, to_lng: float) -> dict:
    """Întoarce cea mai bună variantă: {"kind": walk|transit|transfer|car, "minutes": .., "text": ..}."""
    total = distance_m(from_lat, from_lng, to_lat, to_lng)
    walk_total = _walk_min(total)
    if total <= WALK_ONLY_UNDER:
        return {"kind": "walk", "minutes": walk_total,
                "text": f"E aproape: ~{walk_total} min pe jos ({round(total)} m), nu merită să aștepți tramvaiul."}
    st = _data()["stops"]
    origins, dests = _near_stops(from_lat, from_lng), _near_stops(to_lat, to_lng)
    best = None   # (minute, descriere)

    # 1) o singură linie
    for r, p in _patterns():
        stops = p["stops"]
        for i, a in enumerate(stops):
            if a not in origins:
                continue
            for j in range(i + 1, len(stops)):
                b = stops[j]
                if b not in dests:
                    continue
                w1, w2 = _walk_min(origins[a]), _walk_min(dests[b])
                ride = _ride_min(r, stops, i, j)
                cost = w1 + ride + w2 + 4   # +4 min așteptare medie
                if not best or cost < best[0]:
                    best = (cost, {"kind": "transit", "legs": [(r, p, a, b, j - i, ride)], "w1": w1, "w2": w2})
    # 2) cu o schimbare (doar dacă nu e linie directă rezonabilă)
    if not best or best[0] > walk_total * 0.8:
        # pentru fiecare variantă de linie: cea mai bună urcare (lângă tine) și cea mai bună coborâre (lângă destinație)
        firsts, seconds = [], []
        for r, p in _patterns():
            stops = p["stops"]
            ups = [(origins[a], i, a) for i, a in enumerate(stops) if a in origins]
            downs = [(dests[b], j, b) for j, b in enumerate(stops) if b in dests]
            if ups:
                _, i, a = min(ups)
                firsts.append((r, p, i, a))
            if downs:
                _, j, b = min(downs)
                seconds.append((r, p, j, b))
        neigh = _neighbours()
        for r1, p1, i, a in firsts:
            for r2, p2, j, b in seconds:
                if r2["id"] == r1["id"]:
                    continue
                before = {}
                for k2, s2 in enumerate(p2["stops"][:j]):
                    before.setdefault(s2, k2)
                found = None
                for k1 in range(i + 1, len(p1["stops"])):
                    s1 = p1["stops"][k1]
                    for s2 in neigh.get(s1, []):
                        if s2 in before:
                            found = (k1, s1, before[s2], s2)
                            break
                    if found:
                        break
                if not found:
                    continue
                k1, s1, k2, s2 = found
                w1, w2 = _walk_min(origins[a]), _walk_min(dests[b])
                ride1, ride2 = _ride_min(r1, p1["stops"], i, k1), _ride_min(r2, p2["stops"], k2, j)
                cost = w1 + ride1 + TRANSFER_PENALTY + ride2 + w2 + 4
                if not best or cost < best[0]:
                    best = (cost, {"kind": "transfer", "legs": [(r1, p1, a, s1, k1 - i, ride1), (r2, p2, s2, b, j - k2, ride2)],
                                   "w1": w1, "w2": w2})

    in_city = 46.12 < to_lat < 46.25 and 21.20 < to_lng < 21.42
    car_min = max(5, round(total * 1.3 / (450 if in_city else 1000)) + 3)   # ~27 km/h în oraș, ~60 km/h pe drumuri județene
    if not best or best[0] > walk_total * 1.1 or best[0] > car_min * 4:
        if total <= 2500:
            return {"kind": "walk", "minutes": walk_total,
                    "text": f"Nu e o linie CTP potrivită, dar e la ~{walk_total} min pe jos ({total / 1000:.1f} km). "
                            f"Dacă te grăbești, un Bolt, Uber sau taxi face ~{car_min} min."}
        out_of_city = not in_city
        extra = (" Pentru localitățile din județ poți verifica și trenurile CFR (cfrcalatori.ro) sau autobuzele "
                 "județene din autogară.") if out_of_city else ""
        return {"kind": "car", "minutes": car_min,
                "text": f"Acolo nu ajunge nicio linie CTP. Cel mai simplu: Bolt, Uber sau un taxi, cam {car_min} min "
                        f"cu mașina ({total / 1000:.1f} km).{extra}"}

    cost, opt = best
    legs = opt["legs"]
    r, p, a, b, n, ride = legs[0]
    nxt = _next_departure(a, r)
    when = f" Următorul vine în {nxt} min." if nxt is not None and nxt <= 90 else ""
    text = (f"Mergi {opt['w1']} min pe jos până la stația {st[a]['name']}, ia {_vehicle(r)} spre {p['to']} "
            f"și coboară după {n} stații, la {st[b]['name']} (~{ride} min).{when}")
    if opt["kind"] == "transfer":
        r2, p2, a2, b2, n2, ride2 = legs[1]
        hop = f"la {st[a2]['name']}" if st[a2]["name"] != st[legs[0][3]]["name"] else "acolo"
        text += (f" Schimbi {hop} pe {_vehicle(r2)} spre {p2['to']} și cobori după {n2} stații, la {st[b2]['name']} "
                 f"(~{ride2} min).")
    text += f" De acolo mai ai ~{opt['w2']} min pe jos. În total cam {cost} min."
    if cost > car_min * 2:
        text += f" Cu Bolt, Uber sau taxi ar dura ~{car_min} min."
    return {"kind": opt["kind"], "minutes": cost, "text": text}
