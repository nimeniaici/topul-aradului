"""Topul Aradului - API. Rulează cu:  uvicorn app.main:app --reload --host 0.0.0.0"""
from pathlib import Path
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from . import config
from .db import Base, SessionLocal, engine, ensure_columns, scrub_checkin_gps
from . import usernames
from .routers import auth, checkins, community, crashes, feedback, friends, gunoi, iasmi, polls, rewards, servicii, turism, primarie, transit, download, duels, leaderboard, live, pages, places, reports, tour3d, users
from .seed import seed_places


@asynccontextmanager
async def lifespan(_app: FastAPI):
    Base.metadata.create_all(engine)
    ensure_columns()
    scrub_checkin_gps()
    usernames.migrate(engine)
    community.ensure_general()
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
# poze vechi (libere de drepturi) pentru „Trecut vs prezent”
app.mount("/static", StaticFiles(directory=Path(__file__).parent / "static"), name="static")
# versiunea web (pentru iPhone și orice browser); sursa e în ../web, copiată aici la deploy
app.mount("/web", StaticFiles(directory=Path(__file__).parent / "webapp", html=True), name="web")

app.include_router(reports.admin_router)
app.include_router(polls.admin_router)
for r in (auth, places, leaderboard, checkins, duels, users, live, pages, tour3d, download, reports, crashes, primarie, feedback, iasmi, transit, friends, community, gunoi, turism, rewards, polls, servicii):
    app.include_router(r.router)


@app.get("/", tags=["health"])
def root():
    return {"app": "Topul Aradului", "status": "ok", "docs": "/docs"}
