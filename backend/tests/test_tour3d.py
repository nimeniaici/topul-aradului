"""Turul 3D: administrarea scenelor și pagina vizualizatorului."""
from app import config


def _obiectiv(client):
    return client.get("/places", params={"category": "obiectiv"}).json()[0]


def test_admin_disabled_without_token(client, monkeypatch):
    monkeypatch.setattr(config, "ADMIN_TOKEN", "")
    p = _obiectiv(client)
    assert client.put(f"/admin/places/{p['id']}/splat-url", json={"url": "https://x.ro/a.splat"}).status_code == 404


def test_upload_view_and_delete_splat(client, monkeypatch):
    monkeypatch.setattr(config, "ADMIN_TOKEN", "secret-admin")
    p = _obiectiv(client)
    assert client.get(f"/tur3d/{p['id']}").status_code == 404  # încă fără scenă

    bad = client.post(f"/admin/places/{p['id']}/splat", files={"file": ("a.splat", b"x", "application/octet-stream")},
                      headers={"X-Admin-Token": "gresit"})
    assert bad.status_code == 403
    wrong_ext = client.post(f"/admin/places/{p['id']}/splat", files={"file": ("a.jpg", b"x", "image/jpeg")},
                            headers={"X-Admin-Token": "secret-admin"})
    assert wrong_ext.status_code == 400

    r = client.post(f"/admin/places/{p['id']}/splat", files={"file": ("scena.splat", b"\x00" * 64, "application/octet-stream")},
                    headers={"X-Admin-Token": "secret-admin"})
    assert r.status_code == 200
    url = r.json()["splat_url"]
    assert url.startswith("/uploads/splats/") and url.endswith(".splat")
    assert client.get(url).status_code == 200
    detail = client.get(f"/places/{p['id']}").json()
    assert detail["splat_url"] == url and detail["splat_mb"] is not None

    page = client.get(f"/tur3d/{p['id']}")
    assert page.status_code == 200 and "TUR 3D" in page.text and url in page.text

    ext = client.put(f"/admin/places/{p['id']}/splat-url", json={"url": "https://exemplu.ro/scena.ply"},
                     headers={"X-Admin-Token": "secret-admin"})
    assert ext.json()["splat_url"] == "https://exemplu.ro/scena.ply"
    assert client.get(url).status_code == 404  # fișierul local vechi a fost șters

    d = client.delete(f"/admin/places/{p['id']}/splat", headers={"X-Admin-Token": "secret-admin"})
    assert d.json()["splat_url"] is None
    assert client.get(f"/tur3d/{p['id']}").status_code == 404
