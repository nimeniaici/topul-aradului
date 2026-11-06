from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from ..live import hub

router = APIRouter(tags=["live"])


@router.websocket("/live")
async def live(ws: WebSocket):
    """Stream cu evenimente: {"type": "checkin" | "mayor", ...}."""
    await hub.connect(ws)
    try:
        while True:
            await ws.receive_text()  # ținem conexiunea deschisă (clientul poate trimite "ping")
    except WebSocketDisconnect:
        hub.disconnect(ws)
