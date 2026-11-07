"""Prieteni (cereri, blocare, poziție opțională) și comunitate (chat general, grupuri, privat, evenimente)."""


def _user(client, name):
    r = client.post("/auth/register", json={"name": name, "email": f"{name}@test.ro", "password": "parola123"})
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}, r.json()["user"]["id"]


def test_prieteni_si_pozitie(client):
    a, aid = _user(client, "ana.social")
    b, bid = _user(client, "bogdan.social")
    assert client.post("/friends/request", json={"username": "@bogdan.social"}, headers=a).status_code == 201
    assert client.get("/friends", headers=b).json()["incoming"][0]["name"] == "ana.social"
    assert client.post(f"/friends/{aid}/accept", headers=b).status_code == 200
    assert client.get("/friends", headers=a).json()["friends"][0]["id"] == bid
    # poziția: nimic până nu pornești partajarea
    client.put("/me/location", json={"lat": 46.18, "lng": 21.31}, headers=b)
    assert client.get("/friends/locations", headers=a).json() == []
    client.put("/me/sharing", json={"sharing": True}, headers=b)
    client.put("/me/location", json={"lat": 46.18, "lng": 21.31}, headers=b)
    locs = client.get("/friends/locations", headers=a).json()
    assert locs and locs[0]["id"] == bid
    client.put("/me/sharing", json={"sharing": False}, headers=b)
    assert client.get("/friends/locations", headers=a).json() == []
    assert client.get(f"/friends/{bid}/profile", headers=a).json()["conquered"] == []
    # blocare: nu mai sunt prieteni, iar celălalt nu o găsește
    assert client.post(f"/friends/{bid}/block", headers=a).status_code == 200
    assert client.get("/friends", headers=a).json()["blocked"][0]["id"] == bid
    assert client.post("/friends/request", json={"username": "ana.social"}, headers=b).status_code == 404
    assert client.get(f"/friends/{bid}/profile", headers=a).status_code == 403
    assert client.post(f"/friends/{bid}/unblock", headers=a).status_code == 200


def test_comunitate(client):
    a, aid = _user(client, "carmen.chat")
    b, bid = _user(client, "dan.chat")
    general = client.get("/community/rooms", headers=a).json()["rooms"][0]
    assert general["kind"] == "general"
    r = client.post(f"/community/rooms/{general['id']}/messages", json={"text": "Salut, Arad!"}, headers=a)
    assert r.status_code == 201
    ev = client.post(f"/community/rooms/{general['id']}/messages", headers=b, json={
        "text": "Concert în Piața Avram Iancu", "kind": "eveniment", "event_title": "Concert de toamnă",
        "event_at": "2027-06-01T19:00:00", "event_place": "Piața Avram Iancu", "event_lat": 46.17, "event_lng": 21.31})
    assert ev.status_code == 201
    assert any(e["title"] == "Concert de toamnă" for e in client.get("/community/events", params={"days": 365}).json())
    msgs = client.get(f"/community/rooms/{general['id']}/messages", headers=b).json()
    assert [m["text"] for m in msgs][-2:] == ["Salut, Arad!", "Concert în Piața Avram Iancu"]
    # grup cu cod de invitație
    g = client.post("/community/rooms", json={"name": "Blocul 12, scara B", "tag": "bloc"}, headers=a).json()
    assert g["invite_code"]
    assert client.get(f"/community/rooms/{g['id']}/messages", headers=b).status_code == 404
    assert client.post("/community/rooms/join", json={"code": g["invite_code"]}, headers=b).status_code == 200
    assert client.post(f"/community/rooms/{g['id']}/messages", json={"text": "Bună, vecini"}, headers=b).status_code == 201
    # privat: doar între prieteni
    assert client.post(f"/community/direct/{bid}", headers=a).status_code == 403
    client.post("/friends/request", json={"username": "dan.chat"}, headers=a)
    client.post(f"/friends/{aid}/accept", headers=b)
    dm = client.post(f"/community/direct/{bid}", headers=a).json()
    assert dm["kind"] == "direct" and dm["name"] == "@dan.chat"
    assert client.post(f"/community/direct/{aid}", headers=b).json()["id"] == dm["id"]
