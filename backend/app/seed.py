"""Date inițiale.

    python -m app.seed            # adaugă locurile din seed_places.json (dacă baza e goală)
    python -m app.seed --reset    # șterge tot și reîncarcă locurile
    python -m app.seed --demo     # + utilizatori demo cu check-in-uri și voturi (heatmap viu la prezentare)
"""
import json
import random
import sys
from datetime import timedelta
from pathlib import Path

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from . import elo
from .models import Checkin, Place, User, Vote, utcnow

SEED_FILE = Path(__file__).with_name("seed_places.json")


def seed_places(db: Session) -> int:
    if db.scalar(select(func.count()).select_from(Place)):
        return 0
    data = json.loads(SEED_FILE.read_text(encoding="utf-8"))
    for item in data:
        db.add(Place(**item))
    db.commit()
    return len(data)


def seed_demo(db: Session) -> None:
    from .gamification import award_badges, update_mayor
    from .security import hash_password

    names = ["Ana (demo)", "Mihai (demo)", "Ioana (demo)", "Vlad (demo)", "Sara (demo)", "Radu (demo)"]
    users = []
    for i, n in enumerate(names):
        email = f"demo{i + 1}@topularadului.ro"
        u = db.scalar(select(User).where(User.email == email))
        if not u:
            u = User(name=n, email=email, password_hash=hash_password("demo1234"))
            db.add(u)
        users.append(u)
    db.flush()

    places = db.scalars(select(Place)).all()
    now = utcnow()
    for u in users:
        for p in random.sample(places, k=min(len(places), random.randint(4, 9))):
            for _ in range(random.randint(1, 3)):
                db.add(Checkin(user_id=u.id, place_id=p.id, lat=p.lat, lng=p.lng,
                               created_at=now - timedelta(minutes=random.randint(1, 55))))
                p.checkin_count += 1
                u.xp += 10
    db.flush()

    by_cat: dict[str, list[Place]] = {}
    for p in places:
        by_cat.setdefault(p.category, []).append(p)
    for _ in range(80):
        cat = random.choice([c for c, ps in by_cat.items() if len(ps) >= 2])
        a, b = random.sample(by_cat[cat], 2)
        a.rating, b.rating = elo.update(a.rating, b.rating)
        a.duels += 1
        a.wins += 1
        b.duels += 1
        db.add(Vote(user_id=random.choice(users).id, winner_id=a.id, loser_id=b.id, category=cat))
    db.flush()

    for p in places:
        update_mayor(db, p)
    for u in users:
        award_badges(db, u)
    db.commit()


if __name__ == "__main__":
    from .db import Base, SessionLocal, engine

    sys.stdout.reconfigure(encoding="utf-8")  # consola Windows + diacritice
    if "--reset" in sys.argv:
        Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    with SessionLocal() as session:
        print(f"Locuri adăugate: {seed_places(session)}")
        if "--demo" in sys.argv:
            seed_demo(session)
            print("Utilizatori demo adăugați (parola: demo1234).")
