"""Topul Aradului - API. Rulează cu:  uvicorn app.main:app --reload --host 0.0.0.0"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from . import config
from .db import Base, SessionLocal, engine, ensure_columns, scrub_checkin_gps
from .routers import auth, checkins, download, duels, leaderboard, live, pages, places, tour3d, users
from .seed import seed_places


@asynccontextmanager
async def lifespan(_app: FastAPI):
    Base.metadata.create_all(engine)
    ensure_columns()
    scrub_checkin_gps()
    config.UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    if config.SEED_ON_STARTUP:
        with SessionLocal() as db:
            seed_places(db)
    yield


app = FastAPI(
    title="Topul Aradului API",
    description="Harta Aradului votată de arădeni - Hack4Arad 2026",
    version="0.1.0",
    lifespan=lifespan,
)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

config.UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=config.UPLOAD_DIR), name="uploads")

for r in (auth, places, leaderboard, checkins, duels, users, live, pages, tour3d, download):
    app.include_router(r.router)


@app.get("/", tags=["health"])
def root():
    return {"app": "Topul Aradului", "status": "ok", "docs": "/docs"}
