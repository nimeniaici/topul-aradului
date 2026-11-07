"""Transport CTP: linii reale (OSM), următorul tramvai (estimat), semnalări și bilete demo."""


def test_linii_si_statii(client):
    lines = client.get("/transit/lines").json()
    refs = {l["ref"] for l in lines}
    assert {"1", "3", "6", "7"} <= refs
    l1 = next(l for l in lines if l["ref"] == "1")
    assert len(l1["stops"]) >= 5 and len(l1["shape"]) > 10 and l1["stops"][0]["min_from_start"] == 0
    info = client.get("/transit/info").json()
    assert info["estimated"] and any(t["code"] == "calatorie" and t["price"] == 1.0 for t in info["tickets"])


def test_statie_apropiata_si_urmatorul_tramvai(client):
    near = client.get("/transit/stops/near", params={"lat": 46.1866, "lng": 21.3123}).json()
    assert near and near[0]["distance_m"] < 2000 and near[0]["lines"]
    nxt = client.get(f"/transit/stops/{near[0]['id']}/next").json()
    assert nxt["stop"]["id"] == near[0]["id"] and nxt["estimated"] is True
    for d in nxt["departures"]:
        assert d["minutes"] == sorted(d["minutes"]) and d["minutes"][0] >= 0
    assert client.get("/transit/stops/nu-exista/next").status_code == 404


def test_semnalare_si_bilet_demo(client, auth):
    stop = client.get("/transit/stops/near", params={"lat": 46.18, "lng": 21.31}).json()[0]
    r = client.post("/transit/reports", json={"line": stop["lines"][0], "kind": "aglomerat", "stop_id": stop["id"]}, headers=auth)
    assert r.status_code == 201, r.text
    assert client.post("/transit/reports", json={"line": stop["lines"][0], "kind": "aglomerat"}, headers=auth).status_code == 429
    assert any(x["kind"] == "aglomerat" for x in client.get("/transit/reports").json())
    nxt = client.get(f"/transit/stops/{stop['id']}/next").json()
    assert any(d["crowded"] >= 1 for d in nxt["departures"] if d["line"] == stop["lines"][0]) or not nxt["departures"]
    t = client.post("/transit/tickets", json={"type": "calatorie"}, headers=auth).json()
    assert t["demo"] and t["active"] and t["code"].startswith("CC-") and t["price"] == 1.0
    assert client.get("/transit/tickets/mine", headers=auth).json()[0]["id"] == t["id"]
    assert client.post("/transit/tickets", json={"type": "gratis"}, headers=auth).status_code == 400
