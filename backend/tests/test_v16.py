"""v1.6: profil public, alerte majore, orar, recompense, rute Iasmi."""
from app.route_planner import plan


def test_profil_public(client, auth):
    top = client.get("/leaderboard/users").json()
    p = client.get(f"/users/{top[0]['id']}/public", headers=auth).json()
    assert p["name"] == top[0]["name"] and p["rank"] >= 1 and "conquered" in p and "badges" in p
    assert "email" not in p
    assert client.get("/users/999999/public", headers=auth).status_code == 404


def test_alerte_majore(client, auth):
    cats = {c["code"]: c for c in client.get("/reports/categories").json()}
    assert cats["gaze"]["emergency"] and cats["cutremur"]["emergency"] and not cats["groapa"]["emergency"]
    data = {"category": "gaze", "lat": "46.18", "lng": "21.31", "description": "miros puternic"}
    r = client.post("/reports", data=data, headers=auth)
    assert r.status_code == 201 and r.json()["report"]["emergency"] and r.json()["report"]["priority"] == 0
    # o alertă majoră nu e blocată de limita de 30 s
    assert client.post("/reports", data=data, headers=auth).status_code == 201


def test_orar_fara_internet(client):
    r = client.get("/transit/stops/23/timetable").json()
    assert r["lines"] == [] and r["available"] is False
    assert client.get("/transit/stops/x/timetable").status_code == 404


def test_recompense_statie(client, auth):
    r = client.post("/transit/checkin", json={"stop_id": "23", "lat": 0, "lng": 0}, headers=auth)
    assert r.status_code == 400   # prea departe de stație
    from app.routers.transit import _stop_index
    s = _stop_index()["23"]
    r = client.post("/transit/checkin", json={"stop_id": "23", "lat": s["lat"], "lng": s["lng"]}, headers=auth)
    assert r.status_code == 201 and r.json()["total"] == 1
    assert client.post("/transit/checkin", json={"stop_id": "23", "lat": s["lat"], "lng": s["lng"]}, headers=auth).status_code == 429
    rw = client.get("/me/rewards", headers=auth).json()
    t = next(x for x in rw["rewards"] if x["code"] == "ctp_saptamana")
    assert t["progress"] == 1 and not t["unlocked"] and t["voucher"] is None


def test_rute():
    assert plan(46.1755, 21.3195, 46.1790, 21.3220)["kind"] == "walk"
    r = plan(46.1700, 21.3100, 46.1953, 21.3341)
    assert r["kind"] in ("transit", "transfer") and "stația" in r["text"]
    assert plan(46.1755, 21.3195, 46.0971, 21.6925)["kind"] == "car"
