"""Raportează o problemă a aplicației (din Setări). Echipa le vede la /admin/feedback."""
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import Feedback, User, utcnow
from ..security import get_current_user
from .tour3d import require_admin

router = APIRouter(tags=["feedback"])
KINDS = {"bug": "Ceva nu merge", "idee": "Am o idee", "cont": "Problemă cu contul", "altceva": "Altceva"}


class FeedbackIn(BaseModel):
    kind: str = "bug"
    message: str = Field(min_length=3, max_length=2000)
    app_version: str = Field("", max_length=40)
    device: str = Field("", max_length=120)


@router.post("/feedback", status_code=201)
def send_feedback(body: FeedbackIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if body.kind not in KINDS:
        raise HTTPException(400, "Tip necunoscut.")
    recent = db.scalar(select(Feedback.id).where(Feedback.user_id == user.id,
                                                Feedback.created_at >= utcnow() - timedelta(seconds=20)).limit(1))
    if recent:
        raise HTTPException(429, "Ai trimis un mesaj chiar acum. Mai așteaptă puțin.")
    db.add(Feedback(user_id=user.id, kind=body.kind, message=body.message.strip(),
                    app_version=body.app_version, device=body.device))
    db.commit()
    return {"detail": "Mulțumim! Am primit mesajul și ne uităm cât de repede putem."}


@router.get("/admin/feedback", dependencies=[Depends(require_admin)])
def list_feedback(db: Session = Depends(get_db)):
    rows = db.scalars(select(Feedback).order_by(Feedback.created_at.desc()).limit(300)).all()
    return [{"id": f.id, "kind": KINDS.get(f.kind, f.kind), "message": f.message, "app_version": f.app_version,
             "device": f.device, "user_id": f.user_id, "created_at": f.created_at.isoformat()} for f in rows]
