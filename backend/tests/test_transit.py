"""Transport CTP: rețeaua completă (date deschise), sosiri/vehicule live (Telelink, simulat aici),
semnalări și bilete demo."""
from datetime import datetime, timedelta, timezone

from app.routers import transit


def _iso(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def test_retea_completa(client):
    lines = client.get("/transit/lines").json()
    assert len(lines) == 29
    assert sum(1 for l in lines if l["mode"] == "tram") == 8
    refs = {l["ref"] for l in lines}
    assert {"1", "3", "6", "7", "21", "46"} <= refs
    l1 = next(l for l in lines if l["ref"] == "1")
    assert len(l1["stops"]) >= 5 and len(l1["shape"]) > 10 and l1["shapes"]
    assert [l["mode"] for l in client.get("/transit/lines", params={"mode": "bus"}).json()] == ["bus"] * 21
    info = client.get("/transit/info").json()
    assert any(t["code"] == "calatorie" and t["price"] == 1.0 for t in info["tickets"])


def test_sosiri_si_vehicule_live(client, monkeypatch):
    near = client.get("/transit/stops/near", params={"lat": 46.1866, "lng": 21.3123}).json()
    assert near and near[0]["distance_m"] < 2000 and near[0]["lines"]
    stop = near[0]
    now = datetime.now(timezone.utc)
    route = next(r for r in transit.data()["routes"] if r["ref"] == stop["lines"][0])
    fake = {
        f"/stops/{stop['id']}/times": [{"route": {"routeId": route["id"], "index": route["patterns"][0]["index"]}, "times": [
            {"scheduledArrival": _iso(now + timedelta(minutes=12)), "arrivalDelay": 0, "realtime": False},
            {"scheduledArrival": _iso(now + timedelta(minutes=3)), "arrivalDelay": 120, "realtime": True},
        ]}],
        "/vehicles": [
            {"routeId": route["id"], "patternIndex": route["patterns"][0]["index"], "vehicleId": "241", "latitude": 46.19,
             "longitude": 21.31, "delaySeconds": 90, "lastUpdated": _iso(now), "stopId": int(stop["id"])},
            {"routeId": route["id"], "vehicleId": "999", "latitude": 46.2, "longitude": 21.3,
             "lastUpdated": _iso(now - timedelta(hours=1))},   # vechi -> ascuns
        ],
    }
    monkeypatch.setattr(transit, "_fetch", lambda path, ttl: fake.get(path))
    nxt = client.get(f"/transit/stops/{stop['id']}/next").json()
    assert nxt["available"] and nxt["departures"][0]["line"] == route["ref"]
    d = nxt["departures"][0]
    assert d["live"] is True and d["minutes"][0] in (4, 5) and d["minutes"] == sorted(d["minutes"])
    v = client.get("/transit/vehicles").json()
    assert [x["id"] for x in v["vehicles"]] == ["241"]
    assert v["vehicles"][0]["line"] == route["ref"] and v["vehicles"][0]["next_stop"] == stop["name"]
    assert client.get("/transit/stops/nu-exista/next").status_code == 404


def test_fara_internet_nu_crapa(client):
    nxt = client.get("/transit/stops/23/next").json()   # în teste, CTP_LIVE=0
    assert nxt["departures"] == [] and nxt["available"] is False
    assert client.get("/transit/vehicles").json()["vehicles"] == []


def test_semnalare_si_bilet_demo(client, auth):
    stop = client.get("/transit/stops/near", params={"lat": 46.18, "lng": 21.31}).json()[0]
    r = client.post("/transit/reports", json={"line": stop["lines"][0], "kind": "aglomerat", "stop_id": stop["id"]}, headers=auth)
    assert r.status_code == 201, r.text
    assert client.post("/transit/reports", json={"line": stop["lines"][0], "kind": "aglomerat"}, headers=auth).status_code == 429
    assert any(x["kind"] == "aglomerat" for x in client.get("/transit/reports").json())
    t = client.post("/transit/tickets", json={"type": "calatorie"}, headers=auth).json()
    assert t["demo"] and t["active"] and t["code"].startswith("CC-") and t["price"] == 1.0
    assert client.get("/transit/tickets/mine", headers=auth).json()[0]["id"] == t["id"]
    assert client.post("/transit/tickets", json={"type": "gratis"}, headers=auth).status_code == 400
