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


_size_cache: dict[str, float | None] = {}


def splat_size_mb(url: str | None) -> float | None:
    """Dimensiunea scenei 3D în MB (fișier local sau cerere HEAD pentru adrese externe; rezultatul se ține minte)."""
    if not url:
        return None
    prefix = f"/uploads/{_SPLAT_DIR}/"
    if url.startswith(prefix):
        path = config.UPLOAD_DIR / _SPLAT_DIR / url[len(prefix):]
        return round(path.stat().st_size / 1048576, 1) if path.exists() else None
    if url not in _size_cache:
        try:
            import requests
            r = requests.head(url, allow_redirects=True, timeout=4)
            size = int(r.headers.get("content-length", "0"))
            _size_cache[url] = round(size / 1048576, 1) if size else None
        except Exception:
            _size_cache[url] = None
    return _size_cache[url]


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


# Vizualizatorul: Spark (World Labs, MIT) peste three.js — făcut să meargă și pe telefoane slabe.
# Varianta veche (GaussianSplats3D) rămâne ca rezervă: /tur3d/{id}?v=gs3d
_THREE = "https://cdn.jsdelivr.net/npm/three@0.180.0"
_SPARK = "https://cdn.jsdelivr.net/npm/@sparkjsdev/spark@2.3.1/dist/spark.module.js"
_THREE_OLD = "https://cdn.jsdelivr.net/npm/three@0.160.0"
_GS3D = "https://cdn.jsdelivr.net/npm/@mkkellogg/gaussian-splats-3d@0.4.6/build/gaussian-splats-3d.module.js"

_STYLE = """
  html, body { margin: 0; height: 100%; background: #0F1226; overflow: hidden; touch-action: none;
               font-family: system-ui, -apple-system, Roboto, sans-serif; color: #fff; }
  canvas { display: block; }
  #top { position: fixed; top: 0; left: 0; right: 0; padding: 14px 16px; z-index: 5; pointer-events: none;
         background: linear-gradient(#0F1226ee, #0F122600); }
  #top b { color: #FFD23F; font-size: 13px; letter-spacing: 2px; }
  #top div { font-size: 20px; font-weight: 800; }
  #status { position: fixed; top: 84px; left: 16px; z-index: 5; font-size: 12px; color: #9AA0D0; }
  #help { position: fixed; bottom: 16px; left: 16px; right: 16px; z-index: 5; text-align: center; font-size: 13px;
          color: #C9CCF2; background: #1B2045dd; border-radius: 14px; padding: 10px; transition: opacity .6s; }
  #err { position: fixed; inset: 0; display: none; align-items: center; justify-content: center; text-align: center;
         padding: 24px; z-index: 6; background: #0F1226; }
  #err a { color: #FFD23F; }
"""


def _shell(name: str, importmap: dict, script: str) -> str:
    return f"""<!doctype html>
<html lang="ro"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<title>Tur 3D · {name}</title>
<style>{_STYLE}</style></head>
<body>
<div id="top"><b>TUR 3D</b><div>{name}</div></div>
<div id="status">Se pregătește scena…</div>
<div id="help">Trage cu un deget ca să te rotești · ciupește ca să te apropii · două degete ca să te miști</div>
<div id="err"><div><p style="font-size:18px;font-weight:800">Scena 3D nu s-a putut încărca.</p>
<p id="errmsg" style="color:#9AA0D0;font-size:13px"></p>
<p><a href="?v=gs3d">Încearcă vizualizatorul alternativ</a></p></div></div>
<script type="importmap">{json.dumps({"imports": importmap})}</script>
<script type="module">
  const status = (t) => document.getElementById('status').textContent = t;
  const fail = (e) => {{
    document.getElementById('err').style.display = 'flex';
    document.getElementById('errmsg').textContent = String(e && e.message || e);
  }};
  window.addEventListener('error', (ev) => fail(ev.error || ev.message));
  window.addEventListener('unhandledrejection', (ev) => fail(ev.reason));
  const gl = document.createElement('canvas').getContext('webgl2');
  if (!gl) fail(new Error('Telefonul nu suportă WebGL2, necesar pentru scenele 3D.'));
  else {{ {script} }}
</script>
</body></html>"""


_SPARK_SCRIPT = """
  const cfg = __CFG__;
  const params = new URLSearchParams(location.search);
  try {
    const THREE = await import('three');
    const { OrbitControls } = await import('three/addons/controls/OrbitControls.js');
    const { SparkRenderer, SplatMesh } = await import('@sparkjsdev/spark');
    const renderer = new THREE.WebGLRenderer({ antialias: false });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));   // pe telefon: nu randăm la 3x
    renderer.setSize(innerWidth, innerHeight);
    renderer.setClearColor(0x0F1226);
    document.body.prepend(renderer.domElement);
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(60, innerWidth / innerHeight, 0.01, 1000);
    // poziția de start: ?cam=x,y,z și ?look=x,y,z (implicit privim spre centrul scenei din față)
    const vec = (k, d) => (params.get(k) || d).split(',').map(Number);
    camera.position.set(...vec('cam', '0,0,4'));
    scene.add(new SparkRenderer({ renderer }));
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.rotateSpeed = 0.6;
    controls.target.set(...vec('look', '0,0,0'));

    status('Se descarcă scena…');
    const mesh = new SplatMesh({ url: cfg.url });
    // majoritatea scenelor ies din antrenare „cu capul în jos”; ?flip=0 le lasă cum sunt
    if (params.get('flip') !== '0') mesh.quaternion.set(1, 0, 0, 0);
    scene.add(mesh);
    addEventListener('resize', () => {
      camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix();
      renderer.setSize(innerWidth, innerHeight);
    });
    renderer.setAnimationLoop(() => { controls.update(); renderer.render(scene, camera); });
    await mesh.initialized;
    status('');
    setTimeout(() => document.getElementById('help').style.opacity = 0, 6000);
  } catch (e) { fail(e); }
"""

_GS3D_SCRIPT = """
  const cfg = __CFG__;
  const params = new URLSearchParams(location.search);
  const up = (params.get('up') || '0,-1,0').split(',').map(Number);
  try {
    const GS = await import('@mkkellogg/gaussian-splats-3d');
    const viewer = new GS.Viewer({
      cameraUp: up, initialCameraPosition: [0, -1, 5], initialCameraLookAt: [0, 0, 0],
      sharedMemoryForWorkers: false, dynamicScene: false,
      gpuAcceleratedSort: false, enableSIMDInSort: false, halfPrecisionCovariancesOnGPU: true,
      sphericalHarmonicsDegree: 0,
    });
    await viewer.addSplatScene(cfg.url, {
      showLoadingUI: false, progressiveLoad: false,
      onProgress: (p) => status('Se descarcă scena… ' + Math.round(p) + '%'),
    });
    viewer.start();
    status('');
    setTimeout(() => document.getElementById('help').style.opacity = 0, 6000);
  } catch (e) { fail(e); }
"""


@router.get("/tur3d/{place_id}", response_class=HTMLResponse, include_in_schema=False)
def tour_page(place_id: int, v: str = "", db: Session = Depends(get_db)):
    place = _place(db, place_id)
    if not place.splat_url:
        raise HTTPException(404, "Locul nu are încă tur 3D.")
    name = html.escape(place.name)
    cfg = json.dumps({"url": place.splat_url, "format": Path(place.splat_url.split("?")[0]).suffix.lower()})
    if v == "gs3d":
        imports = {"three": f"{_THREE_OLD}/build/three.module.js", "three/": f"{_THREE_OLD}/",
                   "@mkkellogg/gaussian-splats-3d": _GS3D}
        return HTMLResponse(_shell(name, imports, _GS3D_SCRIPT.replace("__CFG__", cfg)))
    imports = {"three": f"{_THREE}/build/three.module.js", "three/addons/": f"{_THREE}/examples/jsm/",
               "@sparkjsdev/spark": _SPARK}
    return HTMLResponse(_shell(name, imports, _SPARK_SCRIPT.replace("__CFG__", cfg)))
