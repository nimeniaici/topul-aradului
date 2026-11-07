"""v1.7: sondaje, servicii locale, evenimente pe hartă, Iasmi pentru farmacii."""
import os
from datetime import datetime, timedelta


def test_sondaj_cetatean(client, auth):
    r = client.post("/polls", json={"question": "Ce parc vreți renovat?", "options": ["Eminescu", "Copiilor", "Europa"], "days": 3},
                    headers=auth)
    assert r.status_code == 201, r.text
    p = r.json()
    assert p["mine"] and not p["official"] and p["results_visible"] is False and p["options"][0]["votes"] is None
    v = client.post(f"/polls/{p['id']}/vote", json={"option_id": p["options"][1]["id"]}, headers=auth).json()
    assert v["my_vote"] == p["options"][1]["id"] and v["total_votes"] == 1 and v["options"][1]["percent"] == 100
    # își poate schimba votul, tot un singur vot
    v = client.post(f"/polls/{p['id']}/vote", json={"option_id": p["options"][0]["id"]}, headers=auth).json()
    assert v["total_votes"] == 1 and v["options"][0]["votes"] == 1
    assert any(x["id"] == p["id"] for x in client.get("/polls", headers=auth).json())
    assert client.post("/polls", json={"question": "Da sau nu?", "options": ["Da", "da"]}, headers=auth).status_code == 400
    assert client.delete(f"/polls/{p['id']}", headers=auth).status_code == 204


def test_sondaj_oficial_primarie(client, auth):
    os.environ.setdefault("PRIMARIE_TOKEN", "")
    from app import config
    config.PRIMARIE_TOKEN = "cheie-test"
    r = client.post("/admin/polls", json={"question": "Unde punem piste de biciclete?", "options": ["Centru", "Micălaca"]},
                    headers={"X-Admin-Token": "cheie-test"})
    assert r.status_code == 201 and r.json()["official"] and r.json()["author"] == "Primăria Arad"
    assert client.post("/admin/polls", json={"question": "x?????", "options": ["a", "b"]}, headers={"X-Admin-Token": "gresit"}).status_code == 403
    first = client.get("/polls", headers=auth).json()[0]
    assert first["official"]


def test_eveniment_pus_pe_harta_dupa_numele_locului(client, auth):
    rooms = client.get("/community/rooms", headers=auth).json()["rooms"]
    gen = next(r for r in rooms if r["kind"] == "general")
    places = client.get("/places", params={"limit": 5}).json()
    name = places[0]["name"]
    at = (datetime.utcnow() + timedelta(days=2)).isoformat()
    r = client.post(f"/community/rooms/{gen['id']}/messages", json={"text": "Concert", "kind": "eveniment",
                    "event_title": "Concert în aer liber", "event_at": at, "event_place": name}, headers=auth)
    assert r.status_code == 201, r.text
    ev = next(e for e in client.get("/community/events").json() if e["title"] == "Concert în aer liber")
    assert ev["lat"] == places[0]["lat"] and ev["lng"] == places[0]["lng"]


def test_servicii(client):
    d = client.get("/servicii", params={"type": "farmacie", "lat": 46.1745, "lng": 21.3185}).json()
    assert {t["code"] for t in d["types"]} >= {"farmacie", "spital", "scoala"}
    if d["items"]:
        assert all(x["type"] == "farmacie" for x in d["items"])
        assert d["items"][0]["distance_m"] <= d["items"][-1]["distance_m"]


def _user(client, i):
    r = client.post("/auth/register", json={"name": f"confirm{i}", "email": f"confirm{i}@test.ro", "password": "parola123"})
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


def test_sesizare_confirmata_de_trei_oameni(client):
    a, b, c = _user(client, 1), _user(client, 2), _user(client, 3)
    spot = {"category": "groapa", "lat": "46.1901", "lng": "21.3302"}
    r1 = client.post("/reports", data=spot, headers=a).json()
    rep = r1["report"]
    assert rep["confirmations"] == 1 and rep["confirmed"] is False and rep["confirms_needed"] == 3
    # al doilea om semnalează aceeași groapă, la câțiva metri -> confirmă sesizarea existentă
    r2 = client.post("/reports", data={**spot, "lat": "46.19012"}, headers=b).json()
    assert r2["confirmed_existing"] and r2["report"]["id"] == rep["id"] and r2["report"]["confirmations"] == 2
    assert client.post("/reports", data={**spot, "lat": "46.19012"}, headers=b).status_code in (409, 429)
    # al treilea confirmă direct de pe hartă
    r3 = client.post(f"/reports/{rep['id']}/confirm", json={"lat": 46.1901, "lng": 21.3302}, headers=c).json()
    assert r3["report"]["confirmations"] == 3 and r3["report"]["confirmed"] is True
    assert client.post(f"/reports/{rep['id']}/confirm", json={"lat": 46.1901, "lng": 21.3302}, headers=a).status_code == 400
    # o alertă majoră e considerată confirmată imediat
    g = client.post("/reports", data={"category": "gaze", "lat": "46.2", "lng": "21.3"}, headers=a).json()
    assert g["report"]["confirmed"] is True
