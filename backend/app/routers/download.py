"""Descărcarea aplicației Android: pagina /app (cu buton + cod QR) și fișierul /descarca.

Urcarea unui APK nou (cu antetul X-Admin-Token = ADMIN_TOKEN):
  POST /admin/apk   multipart, câmpul „file” (.apk)
"""
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import FileResponse, HTMLResponse

from .. import config
from .tour3d import require_admin

router = APIRouter(tags=["download"])

APK_NAME = "topul-aradului.apk"
MAX_APK_MB = 100


def _apk_path():
    return config.UPLOAD_DIR / "app" / APK_NAME


@router.post("/admin/apk", dependencies=[Depends(require_admin)])
def upload_apk(file: UploadFile = File(...)):
    if not (file.filename or "").lower().endswith(".apk"):
        raise HTTPException(400, "Trimite un fișier .apk")
    target = _apk_path()
    target.parent.mkdir(parents=True, exist_ok=True)
    tmp = target.with_suffix(".tmp")
    written = 0
    with tmp.open("wb") as out:
        while chunk := file.file.read(1024 * 1024):
            written += len(chunk)
            if written > MAX_APK_MB * 1024 * 1024:
                out.close()
                tmp.unlink(missing_ok=True)
                raise HTTPException(413, f"APK-ul depășește {MAX_APK_MB} MB.")
            out.write(chunk)
    tmp.replace(target)  # înlocuire atomică: cine descarcă în timpul urcării primește versiunea veche, întreagă
    return {"mb": round(written / 1048576, 1)}


@router.get("/descarca", include_in_schema=False)
def download_apk():
    path = _apk_path()
    if not path.exists():
        raise HTTPException(404, "Aplicația nu e încă disponibilă pentru descărcare.")
    # nume cu data urcării: telefonul nu mai confundă versiunea nouă cu una descărcată mai demult
    stamp = datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).strftime("%m%d-%H%M")
    return FileResponse(
        path, media_type="application/vnd.android.package-archive",
        filename=APK_NAME.replace(".apk", f"-{stamp}.apk"),
        headers={"Cache-Control": "no-store"},
    )


@router.get("/app", response_class=HTMLResponse, include_in_schema=False)
def app_page():
    path = _apk_path()
    if path.exists():
        st = path.stat()
        size = f"{st.st_size / 1048576:.1f} MB"
        updated = datetime.fromtimestamp(st.st_mtime, timezone.utc).strftime("%d.%m.%Y %H:%M UTC")
        button = f'<a class="btn" href="/descarca?v={int(st.st_mtime)}">Descarcă aplicația <span>(Android · {size})</span></a>'
        meta = f'<p class="muted">Versiune actualizată: {updated}</p>'
    else:
        button = '<p class="btn off">Aplicația va fi disponibilă în curând</p>'
        meta = ""
    return HTMLResponse(f"""<!doctype html>
<html lang="ro"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Descarcă Topul Aradului</title>
<style>
  body {{ margin: 0; background: #0F1226; color: #E6E8FF; font: 16px/1.6 system-ui, -apple-system, Roboto, sans-serif; }}
  main {{ max-width: 520px; margin: 0 auto; padding: 40px 20px 56px; text-align: center; }}
  .crown {{ font-size: 72px; line-height: 1; }}
  h1 {{ color: #FFD23F; letter-spacing: 2px; font-size: 28px; margin: 8px 0 4px; }}
  .tag {{ color: #fff; margin: 0 0 28px; }}
  .btn {{ display: block; background: #FFD23F; color: #412402; font-weight: 800; font-size: 19px; text-decoration: none;
          border-radius: 18px; padding: 18px; margin: 0 0 8px; }}
  .btn span {{ font-weight: 600; font-size: 14px; }}
  .btn.off {{ background: #1B2045; color: #9AA0D0; }}
  .muted {{ color: #9AA0D0; font-size: 13px; }}
  #qr {{ background: #fff; display: inline-block; padding: 14px; border-radius: 18px; margin: 24px 0 8px; }}
  .steps {{ text-align: left; background: #1B2045; border-radius: 18px; padding: 16px 20px; margin-top: 24px; }}
  .steps b {{ color: #FFD23F; }}
  a {{ color: #FFD23F; }}
</style></head>
<body><main>
  <div class="crown">👑</div>
  <h1>TOPUL ARADULUI</h1>
  <p class="tag">Cucerește orașul. Devino Primar.</p>
  {button}
  {meta}
  <div id="qr"></div>
  <p class="muted">Scanează ca să deschizi pagina pe telefon</p>
  <div class="steps">
    <b>Cum instalezi (Android):</b>
    <ol>
      <li>Apasă <b>Descarcă aplicația</b>.</li>
      <li>Deschide fișierul descărcat (<i>topul-aradului.apk</i>).</li>
      <li>Dacă telefonul întreabă, permite <b>instalarea din această sursă</b>.</li>
      <li>Apasă <b>Instalează</b>, apoi intră cu Google sau cu email.</li>
    </ol>
  </div>
  <p class="muted" style="margin-top:28px"><a href="/despre">Despre</a> · <a href="/confidentialitate">Confidențialitate</a> · <a href="/termeni">Termeni</a></p>
</main>
<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
<script>new QRCode(document.getElementById('qr'), {{ text: location.origin + '/app', width: 200, height: 200 }});</script>
</body></html>""")
