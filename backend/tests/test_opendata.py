"""Calendarul gunoiului (RETIM) și locațiile turistice (CNIPT)."""
from datetime import date

from app.routers import gunoi


def test_cauta_strada_si_calendar(client):
    res = client.get("/gunoi/search", params={"q": "Ady Endre"}).json()
    assert res and "Ady Endre" in res[0]["street"]
    s = client.get(f"/gunoi/{res[0]['id']}").json()
    assert s["fractions"] and s["upcoming"]
    types = {f["type"] for f in s["fractions"]}
    assert {"residual", "bio"} <= types
    for f in s["fractions"]:
        assert f["in_days"] >= 0 and f["dates"] == sorted(f["dates"])
    assert client.get("/gunoi/999999").status_code == 404
    assert client.get("/gunoi/search", params={"q": "strada ady endre"}).json()[0]["id"] == res[0]["id"]


def test_calendar_la_o_data_fixa():
    pid = gunoi.search_places("Agricultorilor")[0]["id"]
    s = gunoi.schedule(pid, today=date(2026, 11, 9))
    assert all(f["next"] >= "2026-11-09" for f in s["fractions"])
    assert s["upcoming"][0]["date"] >= "2026-11-09"


def test_iasmi_stie_de_gunoi(client, auth):
    r = client.post("/iasmi", json={"messages": [{"role": "user", "content": "când vine gunoiul pe strada Ady Endre?"}]}, headers=auth).json()
    assert "Ady Endre" in r["reply"] and "ridicări" in r["reply"]


def test_turism(client):
    d = client.get("/turism").json()
    assert len(d["locations"]) == 325 and "CNIPT" in d["source"]
    ob = client.get("/turism", params={"type": "obiective", "only_arad": True, "lat": 46.1754, "lng": 21.3194}).json()["locations"]
    assert ob and all(x["type"] == "Obiective turistice" and x["locality"] == "Arad" for x in ob)
    assert ob[0]["distance_m"] <= ob[-1]["distance_m"]
    pal = client.get("/turism", params={"q": "palatul administrativ"}).json()["locations"]
    assert pal and client.get(f"/turism/{pal[0]['id']}").json()["description"]
    assert len(client.get("/turism/trasee").json()["trails"]) == 30
    assert client.get("/turism/0").status_code == 404
