"""Întreținere fără pierderea conturilor.

    python -m app.maintenance sync      # aduce locurile la zi cu seed_places.json (după nume)
    python -m app.maintenance sync --dry-run

- locurile noi se adaugă;
- cele existente își actualizează categoria, descrierea, adresa și coordonatele (rămân rating-ul, Primarul, check-in-urile, scena 3D);
- cele care nu mai sunt în listă se șterg, împreună cu check-in-urile și voturile lor.
"""
import json
import sys

from sqlalchemy import delete, or_, select
from sqlalchemy.orm import Session

from .models import Checkin, Place, Vote
from .seed import SEED_FILE

_FIELDS = ("category", "description", "address", "lat", "lng", "verified")


def sync_places(db: Session, dry_run: bool = False) -> dict:
    wanted = {p["name"]: p for p in json.loads(SEED_FILE.read_text(encoding="utf-8"))}
    existing = {p.name: p for p in db.scalars(select(Place))}
    added, updated, removed = [], [], []

    for name, data in wanted.items():
        place = existing.get(name)
        if place is None:
            added.append(name)
            if not dry_run:
                db.add(Place(**data))
            continue
        changes = {f: data[f] for f in _FIELDS if f in data and getattr(place, f) != data[f]}
        if changes:
            updated.append(name)
            if not dry_run:
                for f, v in changes.items():
                    setattr(place, f, v)

    for name, place in existing.items():
        if name in wanted:
            continue
        removed.append(name)
        if not dry_run:
            db.execute(delete(Checkin).where(Checkin.place_id == place.id))
            db.execute(delete(Vote).where(or_(Vote.winner_id == place.id, Vote.loser_id == place.id)))
            db.delete(place)

    if not dry_run:
        db.commit()
    return {"adăugate": added, "actualizate": updated, "șterse": removed}


if __name__ == "__main__":
    from .db import Base, SessionLocal, engine, ensure_columns

    sys.stdout.reconfigure(encoding="utf-8")
    if len(sys.argv) < 2 or sys.argv[1] != "sync":
        print(__doc__)
        sys.exit(1)
    Base.metadata.create_all(engine)
    ensure_columns()
    with SessionLocal() as session:
        result = sync_places(session, dry_run="--dry-run" in sys.argv)
    for key, names in result.items():
        print(f"{key} ({len(names)}): {', '.join(names) or '-'}")
