"""Minimizarea datelor: poziția GPS exactă a utilizatorului nu se păstrează."""
from sqlalchemy import select, text

from app.db import SessionLocal, engine, scrub_checkin_gps
from app.models import Checkin


def test_checkin_stores_place_coords_not_user_gps(client, auth):
    place = client.get("/places", params={"category": "bar"}).json()[0]
    # utilizatorul e la ~30 m de loc (în raza permisă), dar nu exact pe coordonatele lui
    user_lat, user_lng = place["lat"] + 0.00027, place["lng"] - 0.0002
    r = client.post("/checkins", data={"place_id": place["id"], "lat": user_lat, "lng": user_lng}, headers=auth)
    assert r.status_code == 201, r.text
    assert 0 < r.json()["distance_m"] < 100  # distanța s-a calculat din poziția reală
    with SessionLocal() as db:
        c = db.get(Checkin, r.json()["checkin_id"])
        assert (c.lat, c.lng) == (place["lat"], place["lng"])
        assert (c.lat, c.lng) != (user_lat, user_lng)


def test_old_gps_rows_are_scrubbed(client, auth):
    place = client.get("/places", params={"category": "mall"}).json()[0]
    r = client.post("/checkins", data={"place_id": place["id"], "lat": place["lat"], "lng": place["lng"]}, headers=auth)
    cid = r.json()["checkin_id"]
    # simulăm un rând vechi, salvat înainte de schimbare, cu poziția exactă a utilizatorului
    with engine.begin() as conn:
        conn.execute(text("UPDATE checkins SET lat = :a, lng = :b WHERE id = :i"), {"a": 46.1, "b": 21.2, "i": cid})
    scrub_checkin_gps()
    with SessionLocal() as db:
        c = db.scalar(select(Checkin).where(Checkin.id == cid))
        assert (c.lat, c.lng) == (place["lat"], place["lng"])
