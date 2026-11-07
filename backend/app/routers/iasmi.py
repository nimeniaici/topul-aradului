"""Iasmi: asistentul AI al CityConnect Arad (Claude, prin API-ul Anthropic).

Răspunde la întrebări de tipul „cum plătesc X?”, „cum ajung la Y?”, „ce e deschis lângă mine?”.
Are acces (prin unelte) la datele aplicației: locurile din Arad și sesizările recente.
Fără ANTHROPIC_API_KEY pe server, răspunde într-un mod de rezervă simplu (fără AI), ca aplicația să meargă oricum.
"""
import os
import unicodedata
from collections import defaultdict, deque
from time import monotonic

import anthropic
from anthropic import beta_tool
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from .. import iasmi_local
from ..categories import CATEGORIES
from ..db import SessionLocal, get_db
from ..geo import distance_m
from ..models import Place, Report, User, utcnow
from ..security import get_current_user
from .reports import REPORT_CATEGORIES, STATUSES

router = APIRouter(prefix="/iasmi", tags=["iasmi"])

MODEL = "claude-opus-5-5"
MAX_PER_HOUR = 30  # pe utilizator, ca să ținem costul sub control
_usage: dict[int, deque] = defaultdict(deque)

# Promptul de sistem e fix (fără date care se schimbă), ca să poată fi pus în cache.
SYSTEM = """Ești Iasmi, asistenta aplicației CityConnect Arad, pentru locuitorii și vizitatorii orașului Arad (România).

Cum vorbești:
- Răspunzi în limba în care ți se scrie (implicit română), cald, scurt și la obiect: de obicei 2–5 propoziții sau o listă scurtă.
- Fără emoji. Fără formatare complicată; liste simple cu „-” sunt ok.

Ce știi și ce faci:
- Pentru locuri din Arad (restaurante, cafenele, obiective, parcuri, mănăstiri etc.) folosește unealta search_places; recomandă doar locuri pe care le găsești acolo și spune adresa când o ai.
- Pentru întrebări despre probleme din oraș (gropi, iluminat, furtună) folosește recent_reports.
- Pentru „cum ajung la …” dă indicații generale (mers pe jos / tramvai / mașină) și spune că în aplicație, pe pagina locului, butonul „Du-mă acolo” deschide navigarea.
- Pentru plăți și servicii publice (taxe locale, amenzi, utilități, transport) explică pașii generali și trimite la sursele oficiale: ghiseul.ro pentru taxe și amenzi, site-ul Primăriei Arad (primariaarad.ro), furnizorul utilității respective pentru facturi, CTP Arad pentru transportul în comun. Nu inventa prețuri, orare, numere de telefon sau adrese pe care nu le știi sigur; spune sincer când nu știi și unde se poate verifica.
- Pentru urgențe (pericol pentru oameni) spune imediat: sună la 112.

Funcțiile aplicației, pe care le poți recomanda:
- Harta live cu locuri și poze; check-in la locuri; „Primarul” unui loc (cine are cele mai multe check-in-uri); dueluri între locuri; clasamentul Topul Aradului.
- Semnalează o problemă (groapă, copac căzut, inundație, canal, iluminat, vandalism, gunoi, parcare ilegală, animale) – ajunge la Primărie; în „Sesizările mele” se vede statusul.
- Descoperă Aradul: poze vechi „trecut vs prezent” și ghid audio la obiective.
- Setări: cont, scoruri, limbă, paleta de culori, raportează o problemă a aplicației."""



class ChatMessage(BaseModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(min_length=1, max_length=2000)


class ChatIn(BaseModel):
    messages: list[ChatMessage] = Field(min_length=1, max_length=20)
    lat: float | None = None
    lng: float | None = None


class ChatOut(BaseModel):
    reply: str
    ai: bool
    places: list[dict] = []


def _plain(text: str) -> str:
    return unicodedata.normalize("NFKD", text.lower()).encode("ascii", "ignore").decode()


def _find_places(db: Session, query: str = "", category: str = "", near: tuple[float, float] | None = None,
                 limit: int = 5) -> list[Place]:
    q = select(Place)
    if category and category in CATEGORIES:
        q = q.where(Place.category == category)
    places = db.scalars(q).all()
    words = [w for w in _plain(query).split() if len(w) > 2]

    def score(p: Place) -> float:
        hay = _plain(f"{p.name} {p.description} {p.address} {CATEGORIES.get(p.category, '')}")
        hits = sum(1 for w in words if w in hay or (len(w) >= 5 and w[:-1] in hay))
        return hits * 1000 + p.rating / 10

    if words:
        places = [p for p in places if score(p) >= 1000] or places
    if near:
        places.sort(key=lambda p: distance_m(near[0], near[1], p.lat, p.lng))
    else:
        places.sort(key=score, reverse=True)
    return places[:limit]


def _place_dict(p: Place, near: tuple[float, float] | None) -> dict:
    d = {"id": p.id, "name": p.name, "category": CATEGORIES.get(p.category, p.category), "address": p.address,
         "description": p.description, "rating": round(p.rating)}
    if near:
        d["distance_m"] = round(distance_m(near[0], near[1], p.lat, p.lng))
    return d


def _make_tools(near: tuple[float, float] | None, found: list[dict]):
    """Uneltele pe care Claude le poate folosi; fiecare deschide propria sesiune de bază de date."""

    @beta_tool
    def search_places(query: str = "", category: str = "") -> str:
        """Caută locuri din Arad în baza de date a aplicației (restaurante, cafenele, obiective etc.).

        Args:
            query: Cuvinte cheie, ex. „pizza”, „cafenea liniștită”, „cetate”. Poate fi gol.
            category: Opțional, una dintre: fastfood, restaurant, cafenea, cofetarie, gelaterie, terasa, bar, obiectiv, manastire, natura, mall, eveniment.
        """
        with SessionLocal() as db:
            rows = [_place_dict(p, near) for p in _find_places(db, query, category, near)]
        for r in rows:
            if r["id"] not in {f["id"] for f in found}:
                found.append(r)
        return str(rows) if rows else "Niciun loc găsit."

    @beta_tool
    def recent_reports(days: int = 7) -> str:
        """Sesizările cetățenilor din ultimele zile (gropi, iluminat, furtună etc.), cu statusul de la Primărie.

        Args:
            days: Câte zile în urmă (1-60).
        """
        from datetime import timedelta
        with SessionLocal() as db:
            since = utcnow() - timedelta(days=max(1, min(days, 60)))
            rows = db.scalars(select(Report).where(Report.created_at >= since)
                              .order_by(Report.created_at.desc()).limit(30)).all()
            out = [{"tip": REPORT_CATEGORIES.get(r.category, r.category), "status": STATUSES.get(r.status, r.status),
                    "descriere": r.description[:120], "data": r.created_at.date().isoformat()} for r in rows]
        return str(out) if out else "Nicio sesizare în perioada asta."

    return [search_places, recent_reports]


def _fallback(db: Session, text: str, near: tuple[float, float] | None) -> ChatOut:
    """Iasmi gratuit (fără AI plătit): vezi app/iasmi_local.py."""
    reply, places = iasmi_local.answer(db, text, near)
    return ChatOut(reply=reply, ai=False, places=places)


@router.post("", response_model=ChatOut)
def chat(body: ChatIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if body.messages[-1].role != "user":
        raise HTTPException(400, "Ultimul mesaj trebuie să fie al tău.")
    # limită simplă pe utilizator (costul AI)
    now = monotonic()
    q = _usage[user.id]
    while q and now - q[0] > 3600:
        q.popleft()
    if len(q) >= MAX_PER_HOUR:
        raise HTTPException(429, "Ai pus multe întrebări în ultima oră. Mai încearcă puțin mai târziu.")
    q.append(now)

    near = (body.lat, body.lng) if body.lat is not None and body.lng is not None else None
    if not (os.getenv("ANTHROPIC_API_KEY") or os.getenv("ANTHROPIC_AUTH_TOKEN")):
        return _fallback(db, body.messages[-1].content, near)

    found: list[dict] = []
    messages = [{"role": m.role, "content": m.content} for m in body.messages]
    if near:
        messages[-1]["content"] += f"\n\n[Locația mea aproximativă: {near[0]:.4f}, {near[1]:.4f}]"
    try:
        runner = anthropic.Anthropic(max_retries=2, timeout=60).beta.messages.tool_runner(
            model=MODEL,
            max_tokens=4000,
            system=[{"type": "text", "text": SYSTEM, "cache_control": {"type": "ephemeral"}}],
            tools=_make_tools(near, found),
            messages=messages,
            output_config={"effort": "low"},  # conversație scurtă: răspuns rapid și ieftin
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",  # dacă modelul refuză din motive de siguranță, serverul încearcă alt model
            max_iterations=4,
        )
        final = runner.until_done()
    except anthropic.RateLimitError:
        raise HTTPException(503, "Iasmi e foarte căutată acum. Încearcă din nou în câteva secunde.")
    except anthropic.APIStatusError as e:
        if e.status_code in (401, 403):
            return _fallback(db, body.messages[-1].content, near)
        raise HTTPException(502, "Iasmi nu poate răspunde acum. Încearcă din nou.")
    except anthropic.APIConnectionError:
        raise HTTPException(502, "Iasmi nu poate răspunde acum (fără conexiune la serviciul AI).")

    if final.stop_reason == "refusal":
        return ChatOut(reply="Nu te pot ajuta cu asta. Te pot ajuta cu locuri, sesizări sau servicii din Arad.", ai=True)
    reply = "\n".join(b.text for b in final.content if b.type == "text").strip()
    return ChatOut(reply=reply or "Nu am găsit un răspuns bun. Poți reformula?", ai=True, places=found[:5])
