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
    tables = set(insp.get_table_names())
    new_cols = {
        "users": {"google_sub": "VARCHAR(64)", "avatar_emoji": "VARCHAR(16)", "avatar_color": "VARCHAR(16)"},
        "places": {"splat_url": "VARCHAR(300)"},
    }
    with engine.begin() as conn:
        for table, cols in new_cols.items():
            if table not in tables:
                continue
            have = {c["name"] for c in insp.get_columns(table)}
            for name, sql_type in cols.items():
                if name not in have:
                    conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {name} {sql_type}"))


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
