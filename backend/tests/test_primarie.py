"""Panoul Primăriei: statusul sesizărilor și bonusul pentru cetățean."""
from app import config


def _report(client, auth):
    r = client.post("/reports", data={"category": "iluminat", "lat": 46.18, "lng": 21.31}, headers=auth)
    assert r.status_code == 201, r.text
    return r.json()["report"]["id"]


def _xp(client, auth):
    return client.get("/users/me", headers=auth).json()["xp"]


def test_primaria_rezolva_si_cetateanul_primeste_xp(client, auth, monkeypatch):
    monkeypatch.setattr(config, "PRIMARIE_TOKEN", "cheie-primarie")
    rid = _report(client, auth)
    h = {"X-Admin-Token": "cheie-primarie"}
    assert any(x["id"] == rid for x in client.get("/admin/reports", headers=h).json())

    r = client.patch(f"/admin/reports/{rid}", json={"status": "in_lucru", "note": "Echipa vine marți"}, headers=h)
    assert r.status_code == 200 and r.json()["status_label"] == "În lucru"
    before = _xp(client, auth)
    assert client.patch(f"/admin/reports/{rid}", json={"status": "rezolvat"}, headers=h).json()["status"] == "rezolvat"
    assert _xp(client, auth) == before + 20
    # a doua oară „rezolvat” nu mai dă bonus
    client.patch(f"/admin/reports/{rid}", json={"status": "rezolvat"}, headers=h)
    assert _xp(client, auth) == before + 20

    pub = next(x for x in client.get("/reports").json() if x["id"] == rid)
    assert pub["status"] == "rezolvat" and pub["admin_note"] == "Echipa vine marți"
    stats = client.get("/admin/reports/stats", headers=h).json()
    assert stats["by_status"]["rezolvat"] >= 1


def test_panou_protejat(client, auth, monkeypatch):
    monkeypatch.setattr(config, "PRIMARIE_TOKEN", "cheie-primarie")
    rid = _report(client, auth)
    assert client.get("/admin/reports").status_code == 403
    assert client.patch(f"/admin/reports/{rid}", json={"status": "rezolvat"}, headers={"X-Admin-Token": "gresit"}).status_code == 403
    assert client.patch(f"/admin/reports/{rid}", json={"status": "zburat"}, headers={"X-Admin-Token": "cheie-primarie"}).status_code == 400
    assert client.patch("/admin/reports/999999", json={"status": "rezolvat"}, headers={"X-Admin-Token": "cheie-primarie"}).status_code == 404
