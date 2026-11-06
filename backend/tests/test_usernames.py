from app import usernames


def _reg(client, name, email):
    return client.post("/auth/register", json={"name": name, "email": email, "password": "parola123"})


def test_username_unic_si_fara_majuscule(client):
    assert _reg(client, "Diana.Arad", "d1@test.ro").json()["user"]["name"] == "diana.arad"
    r = _reg(client, "diana.arad", "d2@test.ro")
    assert r.status_code == 409 and "deja luat" in r.json()["detail"]
    assert _reg(client, "@DIANA.ARAD", "d3@test.ro").status_code == 409


def test_username_invalid(client):
    for bad in ["ab", "Diana Pop", "ana..b", ".ana", "ana_", "șoim", "admin", "x" * 21]:
        assert _reg(client, bad, f"{abs(hash(bad))}@test.ro").status_code == 400, bad


def test_verificare_disponibil(client):
    _reg(client, "luat.deja", "l1@test.ro")
    r = client.get("/auth/username-available", params={"u": "luat.deja"}).json()
    assert r["available"] is False and r["suggestion"] == "luat.deja2"
    assert client.get("/auth/username-available", params={"u": "liber.inca"}).json()["available"] is True
    assert client.get("/auth/username-available", params={"u": "a b"}).json()["available"] is False


def test_schimbare_username(client):
    h1 = {"Authorization": "Bearer " + _reg(client, "primul.user", "p1@test.ro").json()["access_token"]}
    _reg(client, "al.doilea", "p2@test.ro")
    assert client.patch("/users/me", json={"name": "al.doilea"}, headers=h1).status_code == 409
    assert client.patch("/users/me", json={"name": "primul.user"}, headers=h1).status_code == 200  # același nume: ok
    assert client.patch("/users/me", json={"name": "nou.nume"}, headers=h1).json()["name"] == "nou.nume"


def test_slugify_si_generare_offline():
    assert usernames.slugify("Diana Pop") == "diana.pop"
    assert usernames.slugify("Ștefan Țăran") == "stefan.taran"
    assert usernames.slugify("Ana (demo)") == "ana.demo"
    assert usernames.slugify("!!") == "aradean"
    seen = {"diana"}
    assert usernames.generate_offline("Diana", seen) == "diana2"
