"""Pagina de descărcare a aplicației și urcarea APK-ului."""
from app import config


def test_app_page_before_and_after_upload(client, monkeypatch):
    monkeypatch.setattr(config, "ADMIN_TOKEN", "secret-admin")
    assert client.get("/descarca").status_code in (200, 404)  # depinde dacă alt test a urcat deja
    page = client.get("/app")
    assert page.status_code == 200 and "TOPUL ARADULUI" in page.text

    assert client.post("/admin/apk", files={"file": ("x.apk", b"PK", "application/octet-stream")},
                       headers={"X-Admin-Token": "gresit"}).status_code == 403
    assert client.post("/admin/apk", files={"file": ("x.zip", b"PK", "application/zip")},
                       headers={"X-Admin-Token": "secret-admin"}).status_code == 400

    r = client.post("/admin/apk", files={"file": ("app-debug.apk", b"PK\x03\x04" + b"0" * 1000, "application/octet-stream")},
                    headers={"X-Admin-Token": "secret-admin"})
    assert r.status_code == 200
    d = client.get("/descarca")
    assert d.status_code == 200
    assert d.headers["content-type"] == "application/vnd.android.package-archive"
    assert "topul-aradului.apk" in d.headers["content-disposition"]
    assert d.content.startswith(b"PK")
    assert "Descarcă aplicația" in client.get("/app").text
