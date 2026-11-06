"""„Turul 3D”: scene Gaussian splat pentru obiective, afișate într-un vizualizator web (deschis din aplicație).

Administrare (cu antetul X-Admin-Token = ADMIN_TOKEN):
  POST   /admin/places/{id}/splat       încarcă un fișier .ply / .splat / .ksplat (multipart, câmpul „file”)
  PUT    /admin/places/{id}/splat-url   {"url": "https://..."}  folosește o scenă găzduită în altă parte
  DELETE /admin/places/{id}/splat       scoate scena de la loc
"""
import html
import json
import secrets
import shutil
import uuid
from pathlib import Path

from fastapi import APIRouter, Depends, File, Header, HTTPException, UploadFile
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session

from .. import config
from ..db import get_db
from ..models import Place

router = APIRouter(tags=["3d"])

_SPLAT_EXT = {".ply", ".splat", ".ksplat"}
_SPLAT_DIR = "splats"


def require_admin(x_admin_token: str = Header("")) -> None:
    if not config.ADMIN_TOKEN:
        raise HTTPException(404, "Not Found")
    if not secrets.compare_digest(x_admin_token, config.ADMIN_TOKEN):
        raise HTTPException(403, "Cheie de administrare greșită.")


def _place(db: Session, place_id: int) -> Place:
    place = db.get(Place, place_id)
    if not place:
        raise HTTPException(404, "Locul nu există.")
    return place


def _delete_local(place: Place) -> None:
    prefix = f"/uploads/{_SPLAT_DIR}/"
    if place.splat_url and place.splat_url.startswith(prefix):
        (config.UPLOAD_DIR / _SPLAT_DIR / place.splat_url[len(prefix):]).unlink(missing_ok=True)


@router.post("/admin/places/{place_id}/splat", dependencies=[Depends(require_admin)])
def upload_splat(place_id: int, file: UploadFile = File(...), db: Session = Depends(get_db)):
    place = _place(db, place_id)
    ext = Path(file.filename or "").suffix.lower()
    if ext not in _SPLAT_EXT:
        raise HTTPException(400, f"Format acceptat: {', '.join(sorted(_SPLAT_EXT))}")
    folder = config.UPLOAD_DIR / _SPLAT_DIR
    folder.mkdir(parents=True, exist_ok=True)
    name = f"{place.id}-{uuid.uuid4().hex[:8]}{ext}"
    target = folder / name
    limit = config.MAX_SPLAT_MB * 1024 * 1024
    written = 0
    with target.open("wb") as out:  # copiem pe bucăți: fișierele 3D pot avea sute de MB
        while chunk := file.file.read(1024 * 1024):
            written += len(chunk)
            if written > limit:
                out.close()
                target.unlink(missing_ok=True)
                raise HTTPException(413, f"Fișierul depășește {config.MAX_SPLAT_MB} MB.")
            out.write(chunk)
    _delete_local(place)
    place.splat_url = f"/uploads/{_SPLAT_DIR}/{name}"
    db.commit()
    return {"place_id": place.id, "splat_url": place.splat_url, "mb": round(written / 1048576, 1)}


class SplatUrlIn(BaseModel):
    url: str


@router.put("/admin/places/{place_id}/splat-url", dependencies=[Depends(require_admin)])
def set_splat_url(place_id: int, body: SplatUrlIn, db: Session = Depends(get_db)):
    if not body.url.startswith("https://"):
        raise HTTPException(400, "Adresa trebuie să înceapă cu https://")
    place = _place(db, place_id)
    _delete_local(place)
    place.splat_url = body.url
    db.commit()
    return {"place_id": place.id, "splat_url": place.splat_url}


@router.delete("/admin/places/{place_id}/splat", dependencies=[Depends(require_admin)])
def delete_splat(place_id: int, db: Session = Depends(get_db)):
    place = _place(db, place_id)
    _delete_local(place)
    place.splat_url = None
    db.commit()
    return {"place_id": place.id, "splat_url": None}


# Vizualizatorul: Gaussian Splats 3D (three.js), încărcat de pe CDN
_THREE = "https://cdn.jsdelivr.net/npm/three@0.160.0"
_GS3D = "https://cdn.jsdelivr.net/npm/@mkkellogg/gaussian-splats-3d@0.4.6/build/gaussian-splats-3d.module.js"


@router.get("/tur3d/{place_id}", response_class=HTMLResponse, include_in_schema=False)
def tour_page(place_id: int, db: Session = Depends(get_db)):
    place = _place(db, place_id)
    if not place.splat_url:
        raise HTTPException(404, "Locul nu are încă tur 3D.")
    name = html.escape(place.name)
    cfg = json.dumps({"url": place.splat_url, "format": Path(place.splat_url.split("?")[0]).suffix.lower()})
    return HTMLResponse(f"""<!doctype html>
<html lang="ro"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<title>Tur 3D · {name}</title>
<style>
  html, body {{ margin: 0; height: 100%; background: #0F1226; overflow: hidden; touch-action: none;
               font-family: system-ui, -apple-system, Roboto, sans-serif; color: #fff; }}
  #top {{ position: fixed; top: 0; left: 0; right: 0; padding: 14px 16px; z-index: 5; pointer-events: none;
          background: linear-gradient(#0F1226ee, #0F122600); }}
  #top b {{ color: #FFD23F; font-size: 13px; letter-spacing: 2px; }}
  #top div {{ font-size: 20px; font-weight: 800; }}
  #help {{ position: fixed; bottom: 16px; left: 16px; right: 16px; z-index: 5; text-align: center; font-size: 13px;
           color: #C9CCF2; background: #1B2045dd; border-radius: 14px; padding: 10px; transition: opacity .6s; }}
  #err {{ position: fixed; inset: 0; display: none; align-items: center; justify-content: center; text-align: center;
          padding: 24px; z-index: 6; background: #0F1226; }}
</style></head>
<body>
<div id="top"><b>🧊 TUR 3D</b><div>{name}</div></div>
<div id="help">☝️ Trage cu un deget ca să te rotești · ✌️ cu două ca să te apropii și să te miști</div>
<div id="status" style="position:fixed;top:84px;left:16px;z-index:5;font-size:12px;color:#9AA0D0">Se pregătește scena…</div>
<div id="err"><div><div style="font-size:48px">😵</div><p>Scena 3D nu s-a putut încărca.</p><p id="errmsg" style="color:#9AA0D0;font-size:13px"></p></div></div>
<script type="importmap">{{"imports": {{"three": "{_THREE}/build/three.module.js", "three/": "{_THREE}/", "@mkkellogg/gaussian-splats-3d": "{_GS3D}"}}}}</script>
<script type="module">
  const cfg = {cfg};
  const params = new URLSearchParams(location.search);
  const up = (params.get('up') || '0,-1,0').split(',').map(Number);
  const status = (t) => document.getElementById('status').textContent = t;
  try {{
    // Gaussian splats au nevoie de WebGL2; pe telefoane vechi sau emulatoare fără GPU lipsește.
    const gl = document.createElement('canvas').getContext('webgl2');
    if (!gl) throw new Error('Telefonul nu suportă WebGL2, necesar pentru scenele 3D.');
    const dbg = gl.getExtension('WEBGL_debug_renderer_info');
    const gpu = dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : 'necunoscut';
    status('Se descarcă scena… (GPU: ' + gpu + ')');
    const GS = await import('@mkkellogg/gaussian-splats-3d');
    const viewer = new GS.Viewer({{
      cameraUp: up,
      initialCameraPosition: [0, -1, 5],
      initialCameraLookAt: [0, 0, 0],
      sharedMemoryForWorkers: false,   // fără SharedArrayBuffer: merge și în WebView-ul din aplicație
      dynamicScene: false,
    }});
    await viewer.addSplatScene(cfg.url, {{
      showLoadingUI: true,
      progressiveLoad: cfg.format !== '.ply',
      onProgress: (p) => status('Se descarcă scena… ' + Math.round(p) + '%'),
    }});
    viewer.start();
    status('');
    setTimeout(() => document.getElementById('help').style.opacity = 0, 6000);
  }} catch (e) {{
    document.getElementById('err').style.display = 'flex';
    document.getElementById('errmsg').textContent = String(e && e.message || e);
  }}
</script>
</body></html>""")
