"""Conturi: login cu Google, editare profil, ștergere cont, resetare parolă."""
from app import config, mailer, security


def _register(client, email, password="parola123", name="Cont Test"):
    r = client.post("/auth/register", json={"name": name, "email": email, "password": password})
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


# ---------- Google ----------
def _fake_google(monkeypatch, **claims):
    info = {"sub": "google-123", "email": "gigel@gmail.com", "email_verified": True, "given_name": "Gigel", **claims}
    monkeypatch.setattr(config, "GOOGLE_CLIENT_IDS", ["web-client.apps.googleusercontent.com"])
    monkeypatch.setattr(security, "verify_google_token", lambda token: info)
    import app.routers.auth as auth_router
    monkeypatch.setattr(auth_router, "verify_google_token", lambda token: info)


def test_google_not_configured(client, monkeypatch):
    monkeypatch.setattr(config, "GOOGLE_CLIENT_IDS", [])
    assert client.post("/auth/google", json={"id_token": "x" * 30}).status_code == 503


def test_google_creates_then_reuses_account(client, monkeypatch):
    _fake_google(monkeypatch)
    r1 = client.post("/auth/google", json={"id_token": "x" * 30})
    assert r1.status_code == 200 and r1.json()["user"]["name"] == "Gigel"
    r2 = client.post("/auth/google", json={"id_token": "y" * 30})
    assert r2.json()["user"]["id"] == r1.json()["user"]["id"]
    # contul Google nu are parolă: login-ul clasic explică de ce
    r3 = client.post("/auth/login", json={"email": "gigel@gmail.com", "password": "orice123"})
    assert r3.status_code == 401 and "Google" in r3.json()["detail"]
    me = client.get("/users/me", headers={"Authorization": f"Bearer {r1.json()['access_token']}"}).json()
    assert me["google_linked"] is True and me["has_password"] is False


def test_google_links_existing_email_account(client, monkeypatch):
    _register(client, "maria@gmail.com", name="Maria")
    _fake_google(monkeypatch, sub="google-maria", email="maria@gmail.com", given_name="Maria G")
    r = client.post("/auth/google", json={"id_token": "z" * 30})
    me = client.get("/users/me", headers={"Authorization": f"Bearer {r.json()['access_token']}"}).json()
    assert me["name"] == "Maria" and me["google_linked"] and me["has_password"]


def test_google_rejects_unverified_email(client, monkeypatch):
    _fake_google(monkeypatch, sub="g-unverified", email="x@y.ro", email_verified=False)
    assert client.post("/auth/google", json={"id_token": "x" * 30}).status_code == 401


# ---------- Profil ----------
def test_update_profile(client):
    h = _register(client, "edit@test.ro")
    r = client.patch("/users/me", json={"name": "Nume Nou", "avatar_emoji": "🦊", "avatar_color": "mint"}, headers=h)
    assert r.status_code == 200
    assert (r.json()["name"], r.json()["avatar_emoji"], r.json()["avatar_color"]) == ("Nume Nou", "🦊", "mint")
    assert client.patch("/users/me", json={"avatar_emoji": "💩"}, headers=h).status_code == 400
    assert client.patch("/users/me", json={"avatar_color": "rosu"}, headers=h).status_code == 400
    assert client.patch("/users/me", json={"name": "x"}, headers=h).status_code == 422
    top = client.get("/leaderboard/users", params={"limit": 100}).json()
    assert any(u["name"] == "Nume Nou" and u["avatar_emoji"] == "🦊" for u in top)


# ---------- Ștergere cont ----------
def test_delete_account_removes_data_and_frees_throne(client):
    h = _register(client, "sterg@test.ro")
    place = client.get("/places", params={"category": "natura"}).json()[0]
    r = client.post("/checkins", data={"place_id": place["id"], "lat": place["lat"], "lng": place["lng"]},
                    files={"photo": ("p.jpg", b"\xff\xd8\xff", "image/jpeg")}, headers=h)
    assert r.status_code == 201 and r.json()["became_mayor"]
    photo = client.get(f"/places/{place['id']}").json()["recent_photos"][0]["photo_url"]
    before = client.get(f"/places/{place['id']}").json()["checkin_count"]

    assert client.delete("/users/me", headers=h).status_code == 204
    assert client.get("/users/me", headers=h).status_code == 401
    assert client.post("/auth/login", json={"email": "sterg@test.ro", "password": "parola123"}).status_code == 401
    after = client.get(f"/places/{place['id']}").json()
    assert after["checkin_count"] == before - 1 and after["mayor_id"] is None
    assert client.get(photo).status_code == 404


# ---------- Resetare parolă ----------
def test_reset_password_flow(client, monkeypatch):
    sent = {}
    monkeypatch.setattr(mailer, "send_reset_code", lambda to, name, code: sent.update(to=to, code=code))
    _register(client, "uituc@test.ro", password="veche123")

    r = client.post("/auth/forgot-password", json={"email": "UITUC@test.ro"})
    assert r.status_code == 202 and sent["to"] == "uituc@test.ro" and len(sent["code"]) == 6
    # email inexistent: același răspuns, fără email trimis
    sent.clear()
    assert client.post("/auth/forgot-password", json={"email": "nimeni@test.ro"}).json()["detail"] == r.json()["detail"]
    assert not sent

    client.post("/auth/forgot-password", json={"email": "uituc@test.ro"})
    code = sent["code"]
    wrong = "000000" if code != "000000" else "111111"
    assert client.post("/auth/reset-password", json={"email": "uituc@test.ro", "code": wrong, "new_password": "noua1234"}).status_code == 400
    ok = client.post("/auth/reset-password", json={"email": "uituc@test.ro", "code": code, "new_password": "noua1234"})
    assert ok.status_code == 200
    # codul nu merge de două ori; parola veche nu mai merge, cea nouă da
    assert client.post("/auth/reset-password", json={"email": "uituc@test.ro", "code": code, "new_password": "alta1234"}).status_code == 400
    assert client.post("/auth/login", json={"email": "uituc@test.ro", "password": "veche123"}).status_code == 401
    assert client.post("/auth/login", json={"email": "uituc@test.ro", "password": "noua1234"}).status_code == 200


def test_reset_code_locks_after_too_many_attempts(client, monkeypatch):
    sent = {}
    monkeypatch.setattr(mailer, "send_reset_code", lambda to, name, code: sent.update(code=code))
    _register(client, "brute@test.ro")
    client.post("/auth/forgot-password", json={"email": "brute@test.ro"})
    code = sent["code"]
    wrong = "000000" if code != "000000" else "111111"
    for _ in range(5):
        client.post("/auth/reset-password", json={"email": "brute@test.ro", "code": wrong, "new_password": "noua1234"})
    # după 5 greșeli, nici codul corect nu mai e acceptat
    assert client.post("/auth/reset-password", json={"email": "brute@test.ro", "code": code, "new_password": "noua1234"}).status_code == 400


def test_existing_sqlite_db_gets_new_columns(tmp_path):
    """O bază veche (fără coloanele noi) e completată automat la pornire."""
    import sqlalchemy as sa
    from app import db as dbmod
    eng = sa.create_engine(f"sqlite:///{tmp_path / 'old.db'}")
    with eng.begin() as c:
        c.execute(sa.text("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR(40), email VARCHAR(120), "
                          "password_hash VARCHAR(200), xp INTEGER, created_at DATETIME)"))
    old = dbmod.engine
    dbmod.engine = eng
    try:
        dbmod.ensure_columns()
    finally:
        dbmod.engine = old
    cols = {c["name"] for c in sa.inspect(eng).get_columns("users")}
    assert {"google_sub", "avatar_emoji", "avatar_color"} <= cols
