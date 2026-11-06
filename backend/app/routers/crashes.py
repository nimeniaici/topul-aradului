"""Rapoarte de erori din aplicație: dacă telefonul crapă, la pornirea următoare trimite eroarea aici.
Nu conțin date personale (doar eroarea, modelul telefonului și versiunea aplicației)."""
import logging
from collections import deque
from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from .tour3d import require_admin

router = APIRouter(tags=["debug"])
log = logging.getLogger("topul.crash")
_recent: deque = deque(maxlen=30)


class CrashIn(BaseModel):
    app_version: str = Field("", max_length=40)
    device: str = Field("", max_length=120)
    android: str = Field("", max_length=40)
    trace: str = Field(..., max_length=20000)


@router.post("/debug/crash", status_code=204)
def report_crash(body: CrashIn):
    entry = {"at": datetime.now(timezone.utc).isoformat(timespec="seconds"), **body.model_dump()}
    _recent.appendleft(entry)
    log.error("CRASH %s %s android %s\n%s", body.app_version, body.device, body.android, body.trace[:4000])


@router.get("/admin/crashes", dependencies=[Depends(require_admin)])
def list_crashes():
    return list(_recent)
