from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from . import config

_connect_args = {"check_same_thread": False} if config.DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(config.DATABASE_URL, connect_args=_connect_args)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


def ensure_columns() -> None:
    """Mini-migrare: adaugă coloanele noi în tabele create de o versiune mai veche."""
    insp = inspect(engine)
    if "users" not in insp.get_table_names():
        return
    have = {c["name"] for c in insp.get_columns("users")}
    new_cols = {"google_sub": "VARCHAR(64)", "avatar_emoji": "VARCHAR(16)", "avatar_color": "VARCHAR(16)"}
    with engine.begin() as conn:
        for name, sql_type in new_cols.items():
            if name not in have:
                conn.execute(text(f"ALTER TABLE users ADD COLUMN {name} {sql_type}"))


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
