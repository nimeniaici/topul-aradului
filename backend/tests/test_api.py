from app import elo


def _place(client, category="cafenea", index=0):
    return client.get("/places", params={"category": category}).json()[index]


def _checkin(client, auth, place, photo=False, **extra):
    data = {"place_id": place["id"], "lat": place["lat"], "lng": place["lng"], **extra}
    files = {"photo": ("poza.jpg", b"\xff\xd8\xff fake jpeg", "image/jpeg")} if photo else None
    return client.post("/checkins", data=data, files=files, headers=auth)


def test_health_and_seed(client):
    assert client.get("/").json()["status"] == "ok"
    cats = {c["code"] for c in client.get("/places/categories").json()}
    assert {"fastfood", "cafenea", "obiectiv", "manastire", "mall", "gelaterie"} <= cats


def test_public_pages(client):
    for path, text in [("/despre", "Topul Aradului"), ("/confidentialitate", "Politica de confidențialitate"),
                       ("/termeni", "Termeni de utilizare")]:
        r = client.get(path)
        assert r.status_code == 200 and "text/html" in r.headers["content-type"] and text in r.text


def test_register_login(client):
    r = client.post("/auth/register", json={"name": "Ana", "email": "Ana@Test.ro", "password": "secret1"})
    assert r.status_code == 201
    assert client.post("/auth/register", json={"name": "Ana", "email": "ana@test.ro", "password": "secret1"}).status_code == 409
    assert client.post("/auth/login", json={"email": "ana@test.ro", "password": "gresit"}).status_code == 401
    r = client.post("/auth/login", json={"email": "ana@test.ro", "password": "secret1"})
    assert r.status_code == 200 and r.json()["user"]["name"] == "ana"


def test_requires_auth(client):
    assert client.get("/users/me").status_code == 401


def test_places_sorted_by_distance(client):
    p = _place(client)
    near = client.get("/places", params={"lat": p["lat"], "lng": p["lng"], "radius_km": 0.05}).json()
    assert near[0]["id"] == p["id"] and near[0]["distance_m"] == 0


def test_checkin_flow(client, auth):
    place = _place(client)
    r = _checkin(client, auth, place, photo=True)
    assert r.status_code == 201, r.text
    body = r.json()
    assert body["xp_gained"] == 20  # loc nou (15) + poză (5)
    assert body["became_mayor"] is True
    assert {"prim_pas", "primar"} <= {b["code"] for b in body["new_badges"]}

    # cooldown
    assert _checkin(client, auth, place).status_code == 429

    detail = client.get(f"/places/{place['id']}").json()
    assert detail["checkin_count"] >= 1 and detail["recent_photos"]
    assert client.get(detail["recent_photos"][0]["photo_url"]).status_code == 200

    heat = client.get("/checkins/recent").json()
    assert any(h["place_id"] == place["id"] for h in heat)

    me = client.get("/users/me", headers=auth).json()
    assert me["checkins"] == 1 and me["mayor_of"][0]["id"] == place["id"]
    assert len(me["all_badges"]) >= len(me["badges"])
    # misiunile = insignele neobținute, cu progres
    codes = {m["code"] for m in me["missions"]}
    assert "prim_pas" not in codes and "explorator" in codes
    explorator = next(m for m in me["missions"] if m["code"] == "explorator")
    assert explorator["progress"] == 1 and explorator["target"] == 5


def test_checkin_too_far_and_mock(client, auth):
    place = _place(client, index=1)
    far = {"place_id": place["id"], "lat": place["lat"] + 0.01, "lng": place["lng"]}
    r = client.post("/checkins", data=far, headers=auth)
    assert r.status_code == 400 and "m de" in r.json()["detail"]
    assert _checkin(client, auth, place, is_mock="true").status_code == 403


def test_mayor_changes(client, auth):
    place = _place(client, "terasa")
    assert _checkin(client, auth, place).json()["became_mayor"] is True
    # al doilea utilizator: egalitate -> primarul rămâne
    r2 = client.post("/auth/register", json={"name": "Rival", "email": "rival@test.ro", "password": "parola123"})
    rival = {"Authorization": f"Bearer {r2.json()['access_token']}"}
    assert _checkin(client, rival, place).json()["became_mayor"] is False


def test_duel_vote_updates_elo(client, auth):
    duel = client.get("/duels/next", params={"category": "fastfood"}).json()
    a, b = duel["a"], duel["b"]
    r = client.post("/duels/vote", json={"winner_id": a["id"], "loser_id": b["id"]}, headers=auth)
    assert r.status_code == 200, r.text
    exp_a, exp_b = elo.update(a["rating"], b["rating"])
    assert abs(r.json()["winner"]["rating"] - exp_a) < 0.2
    assert abs(r.json()["loser"]["rating"] - exp_b) < 0.2
    # același duel imediat -> refuzat
    assert client.post("/duels/vote", json={"winner_id": b["id"], "loser_id": a["id"]}, headers=auth).status_code == 429
    top = client.get("/leaderboard/places", params={"category": "fastfood"}).json()
    assert top[0]["id"] == a["id"]


def test_duel_validation(client, auth):
    pizza = _place(client, "fastfood")
    cafe = _place(client, "cafenea")
    assert client.post("/duels/vote", json={"winner_id": pizza["id"], "loser_id": cafe["id"]}, headers=auth).status_code == 400
    assert client.post("/duels/vote", json={"winner_id": pizza["id"], "loser_id": pizza["id"]}, headers=auth).status_code == 400


def test_user_leaderboard(client):
    users = client.get("/leaderboard/users").json()
    assert users and users[0]["rank"] == 1
    assert users == sorted(users, key=lambda u: -u["xp"])


def test_live_websocket_receives_checkin(client, auth):
    place = _place(client, "restaurant")
    with client.websocket_connect("/live") as ws:
        assert _checkin(client, auth, place).status_code == 201
        msg = ws.receive_json()
        assert msg["type"] == "checkin" and msg["place_id"] == place["id"]


def test_leaderboard_pagination_is_stable(client):
    """Lazy loading în clasament: paginile succesive nu se suprapun și acoperă toate locurile."""
    all_places = client.get("/leaderboard/places", params={"limit": 100}).json()
    pages = []
    for offset in range(0, len(all_places), 20):
        pages += client.get("/leaderboard/places", params={"limit": 20, "offset": offset}).json()
    assert [p["id"] for p in pages] == [p["id"] for p in all_places]
    assert len({p["id"] for p in pages}) == len(pages)
    users = client.get("/leaderboard/users", params={"limit": 2, "offset": 1}).json()
    assert users and users[0]["rank"] == 2


def test_maintenance_sync_keeps_users(client, auth, tmp_path, monkeypatch):
    """Sincronizarea locurilor nu atinge conturile; locurile scoase dispar cu check-in-urile lor."""
    import json
    from app import maintenance, seed
    from app.db import SessionLocal
    from app.models import Place, User
    data = json.loads(seed.SEED_FILE.read_text(encoding="utf-8"))
    victim = data[0]
    place = next(p for p in client.get("/places").json() if p["name"] == victim["name"])
    assert client.post("/checkins", data={"place_id": place["id"], "lat": place["lat"], "lng": place["lng"]},
                       headers=auth).status_code == 201
    smaller = tmp_path / "seed.json"
    smaller.write_text(json.dumps(data[1:] + [{**data[1], "name": "Loc nou de test"}], ensure_ascii=False), encoding="utf-8")
    monkeypatch.setattr(maintenance, "SEED_FILE", smaller)
    with SessionLocal() as db:
        users_before = db.query(User).count()
        result = maintenance.sync_places(db)
        assert victim["name"] in result["șterse"] and "Loc nou de test" in result["adăugate"]
        assert db.query(User).count() == users_before
        assert db.query(Place).filter_by(name=victim["name"]).count() == 0
    assert client.get("/users/me", headers=auth).status_code == 200
    # refacem lista originală pentru celelalte teste
    monkeypatch.setattr(maintenance, "SEED_FILE", seed.SEED_FILE)
    with SessionLocal() as db:
        maintenance.sync_places(db)
