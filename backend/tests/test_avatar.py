from app import config

JPEG = b"\xff\xd8\xff\xe0" + b"0" * 200


def test_poza_profil_upload_inlocuire_stergere(client, auth):
    r = client.post("/users/me/avatar", files={"photo": ("a.jpg", JPEG, "image/jpeg")}, headers=auth)
    assert r.status_code == 200, r.text
    url = r.json()["avatar_url"]
    assert url.startswith("/uploads/av_") and client.get(url).status_code == 200
    first = config.UPLOAD_DIR / url.rsplit("/", 1)[1]

    r2 = client.post("/users/me/avatar", files={"photo": ("b.png", b"\x89PNG\r\n" + b"1" * 50, "image/png")}, headers=auth)
    assert r2.json()["avatar_url"].endswith(".png") and not first.exists()  # poza veche ștearsă

    top = client.get("/leaderboard/users", params={"limit": 50}).json()
    assert any(u["avatar_url"] == r2.json()["avatar_url"] for u in top)

    assert client.delete("/users/me/avatar", headers=auth).json()["avatar_url"] is None


def test_poza_profil_respinge_ce_nu_e_poza(client, auth):
    bad = client.post("/users/me/avatar", files={"photo": ("x.jpg", b"<html>nu e poza", "image/jpeg")}, headers=auth)
    assert bad.status_code == 400
    big = client.post("/users/me/avatar", files={"photo": ("x.jpg", JPEG + b"0" * (2 * 1024 * 1024), "image/jpeg")}, headers=auth)
    assert big.status_code == 413
    assert client.post("/users/me/avatar", files={"photo": ("a.jpg", JPEG, "image/jpeg")}).status_code == 401
