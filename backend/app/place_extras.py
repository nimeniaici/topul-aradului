"""Conținut în plus pentru locuri, păstrat în fișiere JSON (după numele locului):
- povestea pentru ghidul audio (place_stories.json)
- poza veche pentru „Trecut vs prezent” (old_photos.json, poze libere de drepturi, cu sursa)."""
import json
from functools import lru_cache
from pathlib import Path

_DIR = Path(__file__).parent


@lru_cache
def _load(name: str) -> dict:
    return json.loads((_DIR / name).read_text(encoding="utf-8"))


def story(place_name: str) -> str | None:
    return _load("place_stories.json").get(place_name)


def old_photo(place_name: str) -> dict | None:
    info = _load("old_photos.json").get(place_name)
    if not info:
        return None
    return {"url": info["url"], "year": info["year"], "credit": info["credit"]}
