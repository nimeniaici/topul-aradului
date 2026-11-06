"""Sesizări (gropi etc.) și conturile de test."""
from app import config


def test_report_flow(client, auth):
    cats = client.get("/reports/categories").json()
    assert any(c["code"] == "groapa" for c in cats)
    r = client.post("/reports", data={"category": "groapa", "lat": 46.18, "lng": 21.32, "description": "Groapă mare"},
                    files={"photo": ("g.jpg", b"\xff\xd8\xff", "image/jpeg")}, headers=auth)
    assert r.status_code == 201, r.text
    body = r.json()
    assert body["xp_gained"] == 10 and body["report"]["category_label"] == "Groapă în drum"
    assert body["report"]["photo_url"].startswith("/uploads/")
    assert any(x["id"] == body["report"]["id"] for x in client.get("/reports").json())
    # prea repede a doua oară
    assert client.post("/reports", data={"category": "gunoi", "lat": 46.18, "lng": 21.32}, headers=auth).status_code == 429


def test_report_validation(client, auth):
    assert client.post("/reports", data={"category": "extraterestri", "lat": 46.18, "lng": 21.32}, headers=auth).status_code == 400
    assert client.post("/reports", data={"category": "groapa", "lat": 44.4, "lng": 26.1}, headers=auth).status_code == 400
    assert client.post("/reports", data={"category": "groapa", "lat": 46.18, "lng": 21.32}).status_code == 401


def test_tester_can_check_in_from_anywhere(client, monkeypatch):
    r = client.post("/auth/register", json={"name": "Testerița", "email": "testerita@test.ro", "password": "parola123"})
    h = {"Authorization": f"Bearer {r.json()['access_token']}"}
    place = client.get("/places", params={"category": "obiectiv"}).json()[0]
    far = {"place_id": place["id"], "lat": 46.0, "lng": 21.0}
    assert client.post("/checkins", data=far, headers=h).status_code == 400
    monkeypatch.setattr(config, "TESTER_EMAILS", {"testerita@test.ro"})
    assert client.post("/checkins", data=far, headers=h).status_code == 201
