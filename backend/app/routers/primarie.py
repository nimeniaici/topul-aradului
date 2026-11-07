"""Panoul Primăriei: /primarie — sesizările cetățenilor pe hartă, cu status (nouă → în lucru → rezolvată).
Pagina e publică, dar datele cer cheia Primăriei (X-Admin-Token), pe care funcționarul o scrie o dată."""
from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter(tags=["primarie"])

_PAGE = r"""<!doctype html>
<html lang="ro"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Panoul Primăriei · CityConnect Arad</title>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css">
<style>
  :root { --bg:#0F1226; --panel:#1B2045; --panel2:#232a58; --line:#2E3566; --text:#fff; --muted:#9AA0D0;
          --gold:#FFD23F; --pink:#FF3D81; --mint:#2EE6A6; --orange:#FF8A3D; --grey:#7A80A8; }
  * { box-sizing: border-box; }
  html, body { margin:0; height:100%; background:var(--bg); color:var(--text);
               font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
  header { display:flex; align-items:center; gap:14px; padding:14px 20px; border-bottom:1px solid var(--line); }
  header h1 { margin:0; font-size:20px; } header h1 b { color:var(--gold); }
  header .live { margin-left:auto; color:var(--mint); font-size:13px; display:flex; align-items:center; gap:6px; }
  header .live i { width:9px; height:9px; border-radius:50%; background:var(--mint); animation:p 1.6s infinite; }
  @keyframes p { 50% { opacity:.25 } }
  .stats { display:grid; grid-template-columns:repeat(5,1fr); gap:12px; padding:16px 20px 0; }
  .stat { background:var(--panel); border-radius:16px; padding:14px 16px; }
  .stat b { display:block; font-size:30px; font-weight:900; } .stat span { color:var(--muted); font-size:13px; }
  main { display:grid; grid-template-columns: 1fr 420px; gap:16px; padding:16px 20px 20px; height:calc(100% - 190px); min-height:520px; }
  #map { border-radius:18px; height:100%; border:1px solid var(--line); }
  aside { background:var(--panel); border-radius:18px; display:flex; flex-direction:column; overflow:hidden; }
  .tabs { display:flex; gap:6px; padding:12px; flex-wrap:wrap; border-bottom:1px solid var(--line); }
  .tabs button { border:0; border-radius:999px; padding:7px 12px; background:var(--panel2); color:var(--text);
                 font-weight:700; cursor:pointer; font-size:13px; }
  .tabs button.on { background:var(--gold); color:#3a2c00; }
  #list { overflow:auto; flex:1; }
  .item { display:flex; gap:12px; padding:12px; border-bottom:1px solid var(--line); cursor:pointer; }
  .item:hover, .item.sel { background:var(--panel2); }
  .thumb { width:64px; height:64px; border-radius:12px; background:var(--panel2) center/cover; flex:none;
           display:flex; align-items:center; justify-content:center; color:var(--muted); font-size:11px; }
  .item h3 { margin:0 0 3px; font-size:15px; } .item p { margin:0; color:var(--muted); font-size:13px; }
  .pill { display:inline-block; border-radius:999px; padding:2px 9px; font-size:12px; font-weight:800; }
  .s-nou { background:#FF3D8133; color:var(--pink); } .s-in_lucru { background:#FF8A3D33; color:var(--orange); }
  .s-rezolvat { background:#2EE6A633; color:var(--mint); } .s-respins { background:#7A80A833; color:var(--grey); }
  #detail { border-top:1px solid var(--line); padding:14px; display:none; max-height:55%; overflow:auto; }
  #detail img { width:100%; max-height:220px; object-fit:cover; border-radius:12px; margin-bottom:10px; }
  #detail textarea { width:100%; min-height:60px; border-radius:12px; border:1px solid var(--line); background:var(--bg);
                     color:var(--text); padding:10px; font:inherit; margin:8px 0; }
  .actions { display:flex; gap:8px; flex-wrap:wrap; }
  .actions button { flex:1; border:0; border-radius:12px; padding:10px; font-weight:800; cursor:pointer; }
  .b-in_lucru { background:var(--orange); color:#2b1300; } .b-rezolvat { background:var(--mint); color:#00281a; }
  .b-respins { background:var(--panel2); color:var(--text); }
  #login { position:fixed; inset:0; background:var(--bg); display:flex; align-items:center; justify-content:center; z-index:2000; }
  #login form { background:var(--panel); padding:28px; border-radius:20px; width:min(380px, 92vw); }
  #login input { width:100%; padding:12px; border-radius:12px; border:1px solid var(--line); background:var(--bg); color:var(--text); font:inherit; margin:12px 0; }
  #login button { width:100%; padding:12px; border:0; border-radius:12px; background:var(--gold); font-weight:900; cursor:pointer; }
  #toast { position:fixed; bottom:20px; left:50%; transform:translateX(-50%); background:var(--pink); padding:12px 18px;
           border-radius:14px; font-weight:800; display:none; z-index:3000; }
  .leaflet-container { background:#0b0e1f; }
  /* hartă întunecată, ca în aplicație */
  .leaflet-tile-pane { filter: invert(1) hue-rotate(180deg) brightness(.9) contrast(.9) saturate(.6); }
  @media (max-width: 900px) { .stats { grid-template-columns:repeat(2,1fr); } main { grid-template-columns:1fr; height:auto; }
                              #map { height:55vh; } aside { max-height:none; } }
</style></head>
<body>
<div id="login"><form onsubmit="login(event)">
  <h2 style="margin:0">Panoul Primăriei</h2>
  <p style="color:var(--muted);margin:6px 0 0">Sesizările arădenilor, în timp real. Introdu cheia primită de la echipă.</p>
  <input id="key" type="password" placeholder="Cheia Primăriei" autocomplete="current-password" required>
  <button>Intră</button><p id="loginErr" style="color:var(--pink);margin:10px 0 0"></p>
</form></div>

<header><h1>Panoul <b>Primăriei Arad</b></h1>
  <span style="color:var(--muted)">· sesizări de la cetățeni prin CityConnect Arad</span>
  <span class="live"><i></i> live</span></header>
<section class="stats">
  <div class="stat"><b id="sTotal">0</b><span>sesizări în total</span></div>
  <div class="stat"><b id="sNou" style="color:var(--pink)">0</b><span>noi, de preluat</span></div>
  <div class="stat"><b id="sLucru" style="color:var(--orange)">0</b><span>în lucru</span></div>
  <div class="stat"><b id="sRez" style="color:var(--mint)">0</b><span>rezolvate</span></div>
  <div class="stat"><b id="sWeek">0</b><span>în ultimele 7 zile</span></div>
</section>
<main>
  <div id="map"></div>
  <aside>
    <div class="tabs" id="tabs"></div>
    <div id="list"></div>
    <div id="detail"></div>
  </aside>
</main>
<div id="toast"></div>

<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
<script>
const COLORS = { nou:'#FF3D81', in_lucru:'#FF8A3D', rezolvat:'#2EE6A6', respins:'#7A80A8' };
const LABELS = { nou:'Nouă', in_lucru:'În lucru', rezolvat:'Rezolvată', respins:'Respinsă' };
const TABS = [['', 'Toate'], ['nou', 'Noi'], ['in_lucru', 'În lucru'], ['rezolvat', 'Rezolvate'], ['respins', 'Respinse']];
let key = sessionStorage.getItem('primarieKey') || '';
let reports = [], filter = '', selected = null, markers = {};
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const when = (iso) => { const d = new Date(iso.endsWith('Z') ? iso : iso + 'Z');
  return d.toLocaleString('ro-RO', { day:'2-digit', month:'short', hour:'2-digit', minute:'2-digit' }); };

const map = L.map('map', { zoomControl: true }).setView([46.1745, 21.3185], 13);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' }).addTo(map);

async function api(path, opts = {}) {
  const r = await fetch(path, { ...opts, headers: { 'X-Admin-Token': key, 'Content-Type': 'application/json', ...(opts.headers || {}) } });
  if (r.status === 403 || r.status === 404) { showLogin('Cheie greșită.'); throw new Error('auth'); }
  if (!r.ok) throw new Error((await r.json().catch(() => ({}))).detail || r.statusText);
  return r.json();
}
function showLogin(msg) { sessionStorage.removeItem('primarieKey'); document.getElementById('login').style.display = 'flex';
  document.getElementById('loginErr').textContent = msg || ''; }
async function login(e) { e.preventDefault(); key = document.getElementById('key').value.trim();
  try { await api('/admin/reports/stats'); sessionStorage.setItem('primarieKey', key);
        document.getElementById('login').style.display = 'none'; load(); } catch (_) {} }

async function load() {
  map.invalidateSize();  // harta a fost creată sub ecranul de login: îi recalculăm mărimea
  const [list, stats] = await Promise.all([api('/admin/reports?days=365'), api('/admin/reports/stats')]);
  reports = list;
  sTotal.textContent = stats.total; sNou.textContent = stats.by_status.nou; sLucru.textContent = stats.by_status.in_lucru;
  sRez.textContent = stats.by_status.rezolvat; sWeek.textContent = stats.last_7_days;
  render();
}
function render() {
  tabs.innerHTML = TABS.map(([k, l]) => `<button class="${k === filter ? 'on' : ''}" onclick="filter='${k}';render()">${l}</button>`).join('');
  // urgențele nepreluate (furtună, inundație, canal deschis, semafor) apar primele
  const rank = (r) => (r.emergency && r.status !== 'rezolvat' && r.status !== 'respins') ? -1 : (r.urgent && r.status === 'nou') ? 0 : r.status === 'nou' ? 1 : r.status === 'in_lucru' ? 2 : 3;
  const shown = reports.filter(r => !filter || r.status === filter).sort((a, b) => rank(a) - rank(b));
  list.innerHTML = shown.length ? shown.map(r => `
    <div class="item ${r.id === selected ? 'sel' : ''}" onclick="select(${r.id})">
      <div class="thumb" style="${r.photo_url ? `background-image:url('${esc(r.photo_url)}')` : ''}">${r.photo_url ? '' : 'fără poză'}</div>
      <div><h3>${r.emergency ? '<span class="pill s-nou" style="background:#b00020;color:#fff">ALERTĂ MAJORĂ</span> ' : r.urgent ? '<span class="pill s-nou">URGENT</span> ' : ''}${esc(r.category_label)}</h3>
        <p>${esc(r.description || 'Fără descriere')}</p>
        <p><span class="pill s-${r.status}">${LABELS[r.status]}</span> · @${esc(r.user_name)} · ${when(r.created_at)}</p></div>
    </div>`).join('') : '<p style="padding:16px;color:var(--muted)">Nicio sesizare aici.</p>';
  for (const m of Object.values(markers)) m.remove();
  markers = {};
  if (!selected && shown.length) map.fitBounds(shown.map(r => [r.lat, r.lng]), { padding: [40, 40], maxZoom: 15 });
  for (const r of shown) {
    markers[r.id] = L.circleMarker([r.lat, r.lng], { radius: r.id === selected ? 13 : 9, color: '#fff', weight: 2,
      fillColor: COLORS[r.status], fillOpacity: .95 }).addTo(map).on('click', () => select(r.id))
      .bindTooltip(`${esc(r.category_label)} · ${LABELS[r.status]}`);
  }
}
function select(id) {
  selected = id; const r = reports.find(x => x.id === id); if (!r) return;
  map.invalidateSize();
  map.setView([r.lat, r.lng], Math.max(map.getZoom(), 16), { animate: true });
  detail.style.display = 'block';
  detail.innerHTML = `
    ${r.photo_url ? `<img src="${esc(r.photo_url)}" alt="">` : ''}
    <h3 style="margin:0 0 4px">${esc(r.category_label)} <span class="pill s-${r.status}">${LABELS[r.status]}</span></h3>
    <p style="margin:0;color:var(--muted)">${esc(r.description || 'Fără descriere')}</p>
    <p style="margin:6px 0 0;color:var(--muted);font-size:13px">Semnalată de @${esc(r.user_name)} · ${when(r.created_at)}
      · <a style="color:var(--gold)" target="_blank" href="https://maps.google.com/?q=${r.lat},${r.lng}">deschide în Google Maps</a></p>
    <textarea id="note" placeholder="Mesaj pentru cetățean (ex.: Echipa ajunge marți)">${esc(r.admin_note || '')}</textarea>
    <div class="actions">
      <button class="b-in_lucru" onclick="setStatus(${r.id},'in_lucru')">Preia (în lucru)</button>
      <button class="b-rezolvat" onclick="setStatus(${r.id},'rezolvat')">Rezolvată</button>
      <button class="b-respins" onclick="setStatus(${r.id},'respins')">Respinge</button>
    </div>`;
  render();
}
async function setStatus(id, status) {
  try {
    await api(`/admin/reports/${id}`, { method: 'PATCH', body: JSON.stringify({ status, note: document.getElementById('note').value }) });
    toast(status === 'rezolvat' ? 'Rezolvată! Cetățeanul a primit notificare și +20 XP.' : 'Status actualizat: ' + LABELS[status]);
    await load(); select(id);
  } catch (e) { if (e.message !== 'auth') toast(e.message); }
}
function toast(t) { const el = document.getElementById('toast'); el.textContent = t; el.style.display = 'block';
  clearTimeout(el._t); el._t = setTimeout(() => el.style.display = 'none', 3500); }

// sesizările noi apar singure (WebSocket /live)
function live() {
  const ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/live');
  ws.onmessage = (ev) => { const m = JSON.parse(ev.data);
    if (m.type === 'report' && key) { toast('Sesizare nouă: ' + m.place_name + ' de la @' + m.user_name); load(); } };
  ws.onclose = () => setTimeout(live, 3000);
}
if (key) { document.getElementById('login').style.display = 'none'; load().catch(() => {}); }
live();
</script>
</body></html>"""


@router.get("/primarie", response_class=HTMLResponse, include_in_schema=False)
def primarie_page():
    return HTMLResponse(_PAGE)
