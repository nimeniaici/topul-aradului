/* CityConnect Arad – versiunea web (același server ca aplicația Android). */
const PROD = 'https://api-production-a749.up.railway.app';
const API = new URLSearchParams(location.search).get('api') ||
  (/railway\.app|localhost:8000|127\.0\.0\.1:8000/.test(location.host) ? location.origin : PROD);
const ARAD = [46.1745, 21.3185];
const $ = (s, el = document) => el.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const img = u => !u ? '' : (u.startsWith('http') ? u : API + u);
const dist = m => m == null ? '' : m < 1000 ? Math.round(m) + ' m' : (m / 1000).toFixed(1) + ' km';
let token = localStorage.getItem('cc_token'), me = null, myPos = null, timers = [];

/* ---------- utilitare ---------- */
async function api(path, opts = {}) {
  const h = opts.form ? {} : { 'Content-Type': 'application/json' };
  if (token) h.Authorization = 'Bearer ' + token;
  const r = await fetch(API + path, { method: opts.method || (opts.body || opts.form ? 'POST' : 'GET'), headers: h,
    body: opts.form || (opts.body ? JSON.stringify(opts.body) : undefined) });
  if (r.status === 401 && token) { logout(); throw new Error('Sesiunea a expirat. Intră din nou.'); }
  if (r.status === 204) return null;
  const d = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(typeof d.detail === 'string' ? d.detail : (d.detail?.[0]?.msg || 'Eroare ' + r.status));
  return d;
}
function toast(t) { const el = $('#toast'); el.textContent = t; el.style.display = 'block'; clearTimeout(toast.t); toast.t = setTimeout(() => el.style.display = 'none', 3800); }
const fail = e => toast(e.message || String(e));
function locate() {
  return new Promise(res => {
    if (!navigator.geolocation) return res(null);
    navigator.geolocation.getCurrentPosition(p => { myPos = [p.coords.latitude, p.coords.longitude]; res(myPos); }, () => res(null),
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 });
  });
}
function every(ms, fn) { fn(); timers.push(setInterval(fn, ms)); }
function logout() { token = null; me = null; localStorage.removeItem('cc_token'); location.hash = '#login'; }
function need() { if (!token) { location.hash = '#login'; return false; } return true; }
function setView(html, full) { document.body.classList.toggle('mapfull', !!full); $('#view').innerHTML = html; window.scrollTo(0, 0); }
function newMap(id, center = ARAD, z = 14) {
  const m = L.map(id).setView(center, z);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '© OpenStreetMap' }).addTo(m);
  setTimeout(() => m.invalidateSize(), 300);
  return m;
}
const dot = (color) => L.divIcon({ className: '', html: `<div style="width:14px;height:14px;border-radius:50%;background:${color};border:2px solid #fff;box-shadow:0 0 3px #0006"></div>`, iconSize: [14, 14] });
const vehIcon = v => L.divIcon({ className: '', html: `<div class="vbadge" style="background:${v.color}">${esc(v.line)}</div>`, iconSize: null });

/* ---------- ecrane ---------- */
const routes = {
  async login() {
    setView(`<h1>Bun venit!</h1><p class="muted">Orașul tău, conectat. Joacă, semnalează, descoperă.</p>
    <div class="card"><div class="chips"><button class="chip on" data-m="login">Intră</button><button class="chip" data-m="register">Cont nou</button></div>
    <form id="f"><input id="u" placeholder="Username (unic, ex. diana.arad)" style="display:none" autocomplete="username">
    <input id="e" type="email" placeholder="Email" required autocomplete="email"><input id="p" type="password" placeholder="Parolă (min. 6 caractere)" required minlength="6" autocomplete="current-password">
    <button class="btn" style="width:100%">Intră în joc</button></form>
    <p class="muted">Contul e același ca în aplicația Android. Logarea cu Google e disponibilă în aplicație.</p></div>
    <a class="btn ghost" href="#home">Continuă fără cont</a>`);
    let mode = 'login';
    document.querySelectorAll('[data-m]').forEach(b => b.onclick = () => {
      mode = b.dataset.m; document.querySelectorAll('[data-m]').forEach(x => x.classList.toggle('on', x === b));
      $('#u').style.display = mode === 'register' ? '' : 'none'; $('#u').required = mode === 'register';
    });
    $('#f').onsubmit = async e => {
      e.preventDefault();
      try {
        const body = { email: $('#e').value.trim(), password: $('#p').value };
        if (mode === 'register') body.name = $('#u').value.trim().replace(/^@/, '');
        const d = await api(mode === 'register' ? '/auth/register' : '/auth/login', { body });
        token = d.access_token; localStorage.setItem('cc_token', token); location.hash = '#home';
      } catch (er) { fail(er); }
    };
  },

  async home() {
    if (token && !me) me = await api('/users/me').catch(() => null);
    const C = { pink: ['#A8407A', 'rgba(168,64,122,.14)'], deep: ['#3A1230', 'rgba(58,18,48,.10)'], violet: ['#7A2457', 'rgba(122,36,87,.14)'], mint: ['#5DB7A4', 'rgba(93,183,164,.18)'], gold: ['#D9A21B', 'rgba(217,162,27,.16)'] };
    const t = (h, icon, col, b, s) => `<a class="tile" href="${h}"><div class="ic" style="background:${C[col][1]}"><span class="ms" style="color:${C[col][0]}">${icon}</span></div><b>${b}</b><span>${s}</span></a>`;
    const lvl = me ? 100 * me.xp_into_level / me.xp_per_level : 0;
    setView(`${me ? `<div class="hello"><div class="avatar">${me.avatar_url ? `<img src="${img(me.avatar_url)}">` : esc(me.name[0].toUpperCase())}</div>
      <div style="flex:1"><b>Salut, @${esc(me.name)}</b><span class="muted">Nivel ${me.level} · ${me.xp} XP</span><div class="bar"><i style="width:${lvl}%"></i></div></div></div>`
      : `<div class="hello"><div class="avatar"><span class="ms">person</span></div><div style="flex:1"><b>Bun venit!</b><a class="muted" href="#login"><u>Intră în cont</u> ca să joci și să semnalezi</a></div></div>`}
    <a class="ask" href="#iasmi"><div class="orb"><span class="ms">auto_awesome</span></div><div class="grow"><b>Întreab-o pe Iasmi</b><span class="muted">„Cum plătesc?” · „Unde mănânc?”</span></div><span class="ms" style="color:var(--muted)">chevron_right</span></a>
    <a class="hero" href="#map"><b>Harta live a Aradului</b><p>Check-in, cucerește tronuri, vezi ce e agitat</p><span class="pill">Deschide harta <span class="ms">chevron_right</span></span><span class="ms big">map</span></a>
    <div class="sec">Orașul tău</div><div class="grid">
    ${t('#report', 'warning', 'deep', 'Semnalează o problemă', 'Groapă, furtună, vandalism…')}${t('#transit', 'tram', 'mint', 'Transport CTP', 'Tramvaie și autobuze live')}
    ${t('#servicii', 'local_pharmacy', 'pink', 'Servicii locale', 'Farmacii, spitale, școli, poștă')}${t('#gunoi', 'recycling', 'mint', 'Calendar gunoi', 'Când vine mașina pe strada ta')}
    ${t('#reports', 'assignment', 'violet', 'Sesizările mele', 'Ce a răspuns Primăria')}${t('#report/urgent', 'thunderstorm', 'pink', 'Alerte urgente', 'Inundații, gaze, cutremur')}</div>
    <div class="sec">Comunitate</div><div class="grid">
    ${t('#community', 'forum', 'violet', 'Comunitatea Aradului', 'Chat, anunțuri, evenimente')}${t('#polls', 'how_to_vote', 'gold', 'Sondaje', 'Spune ce crezi despre oraș')}
    ${t('#friends', 'group', 'mint', 'Prieteni', 'Pe hartă, chat, tronurile lor')}</div>
    <div class="sec">Joacă</div><div class="grid">
    ${t('#duels', 'sports_mma', 'pink', 'Dueluri', 'Care loc e mai tare?')}${t('#top', 'emoji_events', 'gold', 'Topul Aradului', 'Clasamente locuri și arădeni')}
    ${t('#profile', 'military_tech', 'violet', 'Misiuni și insigne', 'Ce ai de cucerit')}${t('#map', 'view_list', 'mint', 'Lista locurilor', 'Toate locurile, cu poze')}</div>
    <div class="sec">Descoperă Aradul</div><div class="grid">
    ${t('#turism', 'landscape', 'gold', 'Turism în județ', '325 de locuri și 30 de trasee')}${t('#place/discover', 'headphones', 'mint', 'Ghid audio', 'Povestea locurilor, în română')}</div>`);
    document.querySelector('[href="#place/discover"]').href = '#discover';
  },

  async discover() {
    const ds = await api('/places/discover');
    setView(`<a class="back" href="#home"><span class="ms">arrow_back</span>Acasă</a><h1>Descoperă Aradul</h1><p class="muted">Trecut vs prezent · ghid audio</p>` +
      ds.map(p => `<a class="item" href="#place/${p.id}">${p.photo_url ? `<img src="${img(p.photo_url)}">` : ''}<div class="grow"><b>${esc(p.name)}</b><span class="muted">${p.has_story ? '🎧 Ghid audio ' : ''}${p.old_photo ? '· 🕰 Trecut vs prezent' : ''}</span></div><span class="ms">chevron_right</span></a>`).join(''));
  },

  async map() {
    setView(`<div class="mapbar"><div class="search"><span class="ms" style="color:var(--muted)">search</span><input id="sq" placeholder="Caută un loc, ex. pizza, cafenea, KFC"></div><button class="fab" id="mb"><span class="ms">menu</span></button></div>
    <div id="map"></div><button class="fab light" id="me" style="position:fixed;right:16px;bottom:92px;z-index:1100"><span class="ms">my_location</span></button><div id="sh"></div>`, true);
    const m = newMap('map', ARAD, 15); m.zoomControl.remove();
    const places = L.markerClusterGroup({ maxClusterRadius: 60, showCoverageOnHover: false, iconCreateFunction: c => {
      const ms = c.getAllChildMarkers(), p = ms.find(x => x.options.place?.photo_url)?.options.place || ms[0].options.place;
      return L.divIcon({ className: '', iconSize: [52, 52], html: `<div class="cl" ${face(p)}>${p.photo_url ? '' : `<span class="ms">${CI[p.category] || 'place'}</span>`}<em>+${ms.length - 1}</em></div>` });
    } }).addTo(m), veh = L.layerGroup(), reps = L.layerGroup().addTo(m), evs = L.layerGroup().addTo(m);
    let all = [], cat = null, cats = [], vt = null;
    const CI = { fastfood: 'takeout_dining', restaurant: 'restaurant', cafenea: 'local_cafe', cofetarie: 'cake', gelaterie: 'icecream', terasa: 'deck', bar: 'sports_bar',
      obiectiv: 'account_balance', manastire: 'church', biserica: 'church', natura: 'park', mall: 'local_mall', supermarket: 'shopping_cart', benzinarie: 'local_gas_station', cazare: 'hotel', eveniment: 'celebration' };
    const face = p => p.photo_url ? `style="background-image:url('${img(p.photo_url)}')"` : '';
    const pIcon = p => L.divIcon({ className: '', iconSize: [46, 46], iconAnchor: [23, 23],
      html: `<div class="pmark ${p.mayor_id ? '' : 'free'}" ${face(p)}>${p.photo_url ? '' : `<span class="ms">${CI[p.category] || 'place'}</span>`}</div>` });
    const norm = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    function draw() {
      const q = norm($('#sq').value.trim());
      places.clearLayers();
      all.filter(p => (!cat || p.category === cat) && (!q || norm(p.name + ' ' + p.category_label).includes(q)))
        .forEach(p => L.marker([p.lat, p.lng], { icon: pIcon(p), place: p }).addTo(places).on('click', () => sheet(p)));
    }
    function sheet(p) {
      $('#sh').innerHTML = `<div class="sheet">${p.photo_url ? `<div class="ph" style="background-image:url('${img(p.photo_url)}')"></div>` : ''}<div class="in">
      <span class="muted">${esc(p.category_label)}</span><b style="display:block;font-size:20px;font-weight:900">${esc(p.name)}</b>
      <span class="muted">${p.mayor_name ? '👑 Primar: @' + esc(p.mayor_name) : 'Tron liber'}${p.distance_m != null ? ' · ' + dist(p.distance_m) : ''}</span>
      <div class="row" style="margin-top:12px"><a class="btn" href="#place/${p.id}">${p.mayor_id ? 'Fă check-in' : 'Cucerește tronul'}</a><a class="btn ghost" href="#place/${p.id}" style="flex:0">Detalii</a>
      <a class="btn ghost" style="flex:0" target="_blank" href="https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lng}"><span class="ms">directions</span></a></div></div></div>`;
    }
    m.on('click', () => $('#sh').innerHTML = '');
    $('#sq').oninput = draw;
    const [ps, cs] = await Promise.all([api('/places?limit=500').catch(() => []), api('/places/categories').catch(() => [])]);
    all = ps; cats = cs; draw();
    api('/reports?days=30').then(rs => rs.filter(r => r.status !== 'respins').forEach(r => L.marker([r.lat, r.lng], { icon: dot(r.status === 'rezolvat' ? '#2e7d32' : r.status === 'in_lucru' ? '#ef6c00' : '#c62828') })
      .addTo(reps).bindPopup(`<b>${esc(r.category_label)}</b> · ${esc(r.status_label)}<br>${r.confirmed ? 'confirmată' : `confirmări ${r.confirmations}/${r.confirms_needed}`}<br>${esc(r.description)}<br>${r.status === 'nou' && !r.confirmed ? `<a href="#" onclick="confirmReport(${r.id});return false">O văd și eu – confirm</a>` : ''}`))).catch(() => {});
    api('/community/events').then(es => es.filter(e => e.lat).forEach(e => L.marker([e.lat, e.lng], { icon: dot('#8e24aa') }).addTo(evs)
      .bindPopup(`<b>🎉 ${esc(e.title)}</b><br>${esc(e.at.slice(0, 16).replace('T', ' '))} · ${esc(e.place || '')}`))).catch(() => {});
    locate().then(p => { if (p) L.circleMarker(p, { radius: 8, color: '#fff', weight: 3, fillColor: '#1e88e5', fillOpacity: 1 }).addTo(m); });
    $('#me').onclick = async () => { const p = await locate(); p ? m.setView(p, 16) : toast('Permite locația în browser.'); };
    const layers = { live: false, reps: true, evs: true };
    const setLayer = (k, on) => {
      layers[k] = on; const L2 = { live: veh, reps, evs }[k];
      on ? L2.addTo(m) : m.removeLayer(L2);
      if (k === 'live') { clearInterval(vt); if (on) { const f = () => api('/transit/vehicles').then(d => { veh.clearLayers(); d.vehicles.forEach(v => L.marker([v.lat, v.lng], { icon: vehIcon(v) }).addTo(veh).bindPopup(`<b>${esc(v.line)} → ${esc(v.towards)}</b><br>${v.next_stop ? 'Urmează: ' + esc(v.next_stop) : ''}`)); }).catch(() => {}); f(); vt = setInterval(f, 15000); timers.push(vt); } }
    };
    $('#mb').onclick = () => {
      const row = (k, icon, col, title, sub) => `<div class="mrow ${layers[k] ? 'on' : ''}" data-k="${k}"><div class="ic" style="background:${col}22;color:${col}"><span class="ms">${icon}</span></div><div style="flex:1"><b>${title}</b><br><span class="muted">${sub}</span></div>${layers[k] ? '<span class="ms">check</span>' : ''}</div>`;
      const act = (h, icon, col, title, sub) => `<a class="mrow" href="${h}"><div class="ic" style="background:${col}22;color:${col}"><span class="ms">${icon}</span></div><div style="flex:1"><b>${title}</b><br><span class="muted">${sub}</span></div></a>`;
      document.body.insertAdjacentHTML('beforeend', `<div class="menu" id="mn"><div class="box"><h1>Meniu hartă</h1>
      ${act('#report', 'report', '#A8407A', 'Semnalează o problemă', 'Groapă, furtună, inundație, vandalism…')}${act('#reports', 'assignment', '#7A2457', 'Sesizările mele', 'Vezi ce a răspuns Primăria')}
      <div class="sec">Straturi pe hartă</div>${row('live', 'tram', '#5DB7A4', 'Tramvaie și autobuze live', 'Unde sunt acum, pe toate liniile CTP')}${row('evs', 'celebration', '#7A2457', 'Evenimente în oraș', 'Ce anunță comunitatea')}${row('reps', 'report', '#A8407A', 'Sesizări din oraș', 'Gropi, furtună, iluminat…')}
      <div class="sec">Ce vrei să vezi</div><div class="chips" style="flex-wrap:wrap"><button class="chip ${cat ? '' : 'on'}" data-c="">Toate</button>${cats.map(c => `<button class="chip ${cat === c.code ? 'on' : ''}" data-c="${c.code}">${esc(c.label)}</button>`).join('')}</div></div></div>`);
      $('#mn').onclick = e => {
        if (e.target.id === 'mn') return $('#mn').remove();
        const r = e.target.closest('[data-k]'); if (r) { setLayer(r.dataset.k, !layers[r.dataset.k]); $('#mn').remove(); }
        const c = e.target.closest('[data-c]'); if (c) { cat = c.dataset.c || null; draw(); $('#mn').remove(); }
        if (e.target.closest('a')) $('#mn').remove();
      };
    };
  },

  async place(id) {
    const p = await api('/places/' + id);
    setView(`<a href="#map" class="back"><span class="ms">arrow_back</span>Hartă</a><h1>${esc(p.name)}</h1><p class="muted">${esc(p.category_label)} · ${esc(p.address)}</p>
    ${p.photo_url ? `<img src="${img(p.photo_url)}" style="width:100%;max-height:280px;object-fit:cover;border-radius:18px">` : ''}
    <div class="card"><b>${p.mayor_name ? 'Primar: @' + esc(p.mayor_name) : 'Tron liber – primul check-in te face Primar!'}</b><p class="muted">${p.checkin_count} check-in-uri · scor ${Math.round(p.rating)}</p>
    <label class="muted">Poză (opțional, doar cu camera – +5 XP)</label><input type="file" id="ph" accept="image/*" capture="environment">
    <div class="row"><button class="btn" id="ci">${p.mayor_id ? 'Fac check-in aici' : 'Cucerește tronul'}</button><a class="btn ghost" target="_blank" href="https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lng}">Du-mă acolo</a></div></div>
    ${p.story ? `<div class="sec">Povestea locului</div><div class="card"><button class="btn ghost" id="say">▶ Ascultă</button><p>${esc(p.story)}</p></div>` : ''}
    ${p.old_photo ? `<div class="sec">Trecut vs prezent</div><img src="${img(p.old_photo.url)}" style="width:100%;border-radius:18px"><p class="muted">${esc(p.old_photo.caption || '')}</p>` : ''}
    ${p.recent_photos?.length ? `<div class="sec">Poze de la comunitate</div><div class="chips">${p.recent_photos.map(f => `<img src="${img(f.url || f.photo_url)}" style="height:110px;border-radius:12px">`).join('')}</div>` : ''}`);
    $('#ci').onclick = async () => {
      if (!need()) return;
      const pos = await locate(); if (!pos) return toast('Permite locația ca să faci check-in.');
      const f = new FormData(); f.append('place_id', id); f.append('lat', pos[0]); f.append('lng', pos[1]);
      if ($('#ph').files[0]) f.append('photo', await shrink($('#ph').files[0]), 'checkin.jpg');
      try { const r = await api('/checkins', { form: f }); toast(r.message || `Check-in reușit! +${r.xp_gained ?? ''} XP`); me = null; routes.place(id); } catch (e) { fail(e); }
    };
    if ($('#say')) $('#say').onclick = () => { speechSynthesis.cancel(); const u = new SpeechSynthesisUtterance(p.story); u.lang = 'ro-RO'; speechSynthesis.speak(u); };
  },

  async report(kind) {
    if (!need()) return;
    const cats = await api('/reports/categories');
    const list = cats.filter(c => kind === 'urgent' ? c.emergency : !c.emergency);
    setView(`<h1>${kind === 'urgent' ? 'Alertă urgentă' : 'Semnalează o problemă'}</h1>
    ${kind === 'urgent' ? `<a class="btn red" href="tel:112" style="width:100%;text-align:center">Pericol pentru oameni? Sună acum la 112</a><p class="muted">Doar pentru situații extrem de grave. Ajunge prima la Primărie.</p>` : `<p class="muted">Ajunge la Primărie după ce o confirmă 3 oameni. +10 XP.</p>`}
    <div class="card"><select id="c">${list.map(c => `<option value="${c.code}">${esc(c.label)}</option>`).join('')}</select>
    <textarea id="d" rows="3" placeholder="Descriere (opțional)" maxlength="500"></textarea>
    <label class="muted">Poză (camera)</label><input type="file" id="ph" accept="image/*" capture="environment">
    <button class="btn ${kind === 'urgent' ? 'red' : ''}" id="s" style="width:100%">Trimite</button><p class="muted">Locul problemei = unde ești acum.</p></div>`);
    $('#s').onclick = async () => {
      const pos = await locate(); if (!pos) return toast('Permite locația: sesizarea are nevoie de locul exact.');
      const f = new FormData(); f.append('category', $('#c').value); f.append('lat', pos[0]); f.append('lng', pos[1]); f.append('description', $('#d').value);
      if ($('#ph').files[0]) f.append('photo', await shrink($('#ph').files[0]), 'sesizare.jpg');
      $('#s').disabled = true;
      try { const r = await api('/reports', { form: f }); toast(r.confirmed_existing ? `Problema era deja semnalată – ai confirmat-o (${r.report.confirmations}/${r.report.confirms_needed}). +${r.xp_gained} XP` : `Trimisă! +${r.xp_gained} XP`); location.hash = '#reports'; }
      catch (e) { fail(e); $('#s').disabled = false; }
    };
  },

  async reports() {
    if (!need()) return;
    const rs = await api('/reports/mine');
    setView(`<h1>Sesizările mele</h1><a class="btn" href="#report">+ Semnalează o problemă nouă</a>` + (rs.length ? rs.map(r => `<div class="card"><b>${esc(r.category_label)}</b> · <span class="muted">${esc(r.status_label)} · ${r.created_at.slice(0, 10)}</span>
      <p class="muted">${r.emergency ? 'Alertă majoră · ajunsă imediat la Primărie' : r.confirmed ? `Confirmată de ${r.confirmations} arădeni` : `În așteptare: ${r.confirmations}/${r.confirms_needed} confirmări`}</p>
      ${r.description ? `<p>${esc(r.description)}</p>` : ''}${r.photo_url ? `<img src="${img(r.photo_url)}" style="width:100%;max-height:200px;object-fit:cover;border-radius:12px">` : ''}
      ${r.admin_note ? `<p><b>Primăria:</b> ${esc(r.admin_note)}</p>` : ''}</div>`).join('') : '<p class="muted">Încă n-ai semnalat nimic.</p>'));
  },

  async transit() {
    setView(`<h1>Transport CTP</h1><p class="muted" id="cnt">Tramvaie și autobuze · timp real</p><div id="map" style="height:300px"></div>
    <div class="chips" id="lines"></div><div class="sec">Stații lângă tine</div><div class="chips" id="stops"></div><div id="next"></div><div class="sec">Orarul stației</div><div id="tt" class="card muted">Alege o stație.</div>
    <button class="btn" id="sci" style="width:100%;margin-top:10px">Sunt în stație · check-in (+3 XP)</button>`);
    const m = newMap('map', ARAD, 13), shapes = L.layerGroup().addTo(m), veh = L.layerGroup().addTo(m);
    let lines = await api('/transit/lines').catch(() => []), hl = null, stop = null;
    const draw = () => { shapes.clearLayers(); lines.forEach(l => (l.shapes?.length ? l.shapes : [l.shape]).forEach(s => L.polyline(s, { color: l.color, weight: hl === l.ref ? 7 : 4, opacity: !hl || hl === l.ref ? .85 : .12 }).addTo(shapes))); };
    draw();
    $('#lines').innerHTML = lines.map(l => `<button class="chip" data-l="${esc(l.ref)}"><span class="badge" style="background:${l.color}">${esc(l.ref)}</span> ${esc(l.to)}</button>`).join('');
    $('#lines').onclick = e => { const b = e.target.closest('[data-l]'); if (!b) return; hl = hl === b.dataset.l ? null : b.dataset.l; draw(); };
    every(15000, () => api('/transit/vehicles').then(d => { veh.clearLayers(); $('#cnt').textContent = `Tramvaie și autobuze · ${d.vehicles.length} în mers acum`;
      d.vehicles.forEach(v => L.marker([v.lat, v.lng], { icon: vehIcon(v) }).addTo(veh).bindPopup(`<b>${esc(v.line)} → ${esc(v.towards)}</b><br>${v.next_stop ? 'Urmează: ' + esc(v.next_stop) : ''}<br>${v.delay_min > 1 ? 'întârziere ' + v.delay_min + ' min' : 'la timp'}`)); }).catch(() => {}));
    const pos = await locate() || ARAD;
    const stops = await api(`/transit/stops/near?lat=${pos[0]}&lng=${pos[1]}&limit=6`).catch(() => []);
    $('#stops').innerHTML = stops.map(s => `<button class="chip" data-s="${s.id}">${esc(s.name)} · ${dist(s.distance_m)}</button>`).join('');
    const pick = async id => {
      stop = id; $('#stops').querySelectorAll('.chip').forEach(x => x.classList.toggle('on', x.dataset.s === id));
      const n = await api(`/transit/stops/${id}/next`);
      $('#next').innerHTML = n.departures.length ? n.departures.map(d => `<div class="item"><span class="badge" style="background:${d.color}">${esc(d.line)}</span><div class="grow"><b>spre ${esc(d.towards)}</b><span class="muted">${d.minutes.map(x => x ? x + ' min' : 'acum').join(' · ')} ${d.live ? '· ● live' : ''}</span></div></div>`).join('') : '<p class="muted">Nicio plecare în următoarele 3 ore.</p>';
      const t = await api(`/transit/stops/${id}/timetable`);
      $('#tt').innerHTML = t.lines.length ? `<b>${esc(t.stop.name)} · ${t.date}</b>` + t.lines.map(l => `<p><span class="badge" style="background:${l.color}">${esc(l.line)}</span> spre ${esc(l.towards)}</p><div class="times">${l.times.map(x => `<span title="${x.live ? 'live' : ''}">${x.time}${x.tomorrow ? ' (mâine)' : x.delay_min > 1 ? ' → ' + x.expected : ''}</span>`).join('')}</div>`).join('') : 'Orarul nu e disponibil acum.';
    };
    $('#stops').onclick = e => { const b = e.target.closest('[data-s]'); if (b) pick(b.dataset.s); };
    if (stops[0]) pick(stops[0].id);
    $('#sci').onclick = async () => {
      if (!need() || !stop) return;
      const p = await locate(); if (!p) return toast('Permite locația.');
      try { toast((await api('/transit/checkin', { body: { stop_id: stop, lat: p[0], lng: p[1] } })).detail); } catch (e) { fail(e); }
    };
  },

  async gunoi() {
    setView(`<h1>Calendar gunoi</h1><p class="muted">Când vine mașina pe strada ta (date RETIM)</p><input id="q" placeholder="Caută strada (ex. Ady Endre)"><div id="res"></div><div id="sch"></div>`);
    const show = async id => {
      localStorage.setItem('cc_gunoi', id); $('#res').innerHTML = '';
      const s = await api('/gunoi/' + id);
      $('#sch').innerHTML = `<div class="hero"><b style="font-size:22px">${esc(s.place.street)}</b><br>Cartier ${esc(s.place.cartier)}</div>` +
        s.fractions.map(f => `<div class="item"><span class="badge" style="background:${f.color}">&nbsp;</span><div class="grow"><b>${esc(f.label)}</b><span class="muted">${esc(f.hint)}${f.weekday ? ' · în fiecare ' + f.weekday : ''}</span></div><b>${esc(f.next_label)}</b></div>`).join('') +
        `<p class="muted">${esc(s.note)}</p>`;
    };
    let t; $('#q').oninput = e => { clearTimeout(t); t = setTimeout(async () => {
      const q = e.target.value.trim(); if (q.length < 2) return $('#res').innerHTML = '';
      const r = await api('/gunoi/search?q=' + encodeURIComponent(q));
      $('#res').innerHTML = r.map(p => `<a class="item" href="#" data-id="${p.id}"><div class="grow"><b>${esc(p.street)}</b><span class="muted">${esc(p.cartier)}</span></div></a>`).join('');
    }, 250); };
    $('#res').onclick = e => { const a = e.target.closest('[data-id]'); if (a) { e.preventDefault(); show(a.dataset.id); } };
    const saved = localStorage.getItem('cc_gunoi'); if (saved) show(saved);
  },

  async turism() {
    setView(`<h1>Turism în județul Arad</h1><div class="chips" id="ty"><button class="chip on" data-t="obiective">Obiective</button><button class="chip" data-t="agrement">Agrement</button><button class="chip" data-t="cazare">Cazare</button><button class="chip" data-t="trasee">Trasee</button></div><div id="l"></div><p class="muted">Date: CNIPT Arad, visitarad.ro</p>`);
    const pos = await locate();
    const load = async t => {
      if (t === 'trasee') { const d = await api('/turism/trasee'); $('#l').innerHTML = d.trails.map(x => `<a class="item" target="_blank" href="${esc(x.source_url)}"><div class="grow"><b>${esc(x.name)}</b><span class="muted">${esc(x.description.slice(0, 140))}…</span></div></a>`).join(''); return; }
      const d = await api(`/turism?type=${t}` + (pos ? `&lat=${pos[0]}&lng=${pos[1]}` : ''));
      $('#l').innerHTML = d.locations.map(x => `<a class="item" target="_blank" href="https://www.google.com/maps/dir/?api=1&destination=${x.lat},${x.lng}">${x.photo ? `<img src="${esc(x.photo)}" loading="lazy">` : ''}<div class="grow"><b>${esc(x.name)}</b><span class="muted">${esc(x.category)} · ${esc(x.locality)} ${x.distance_m != null ? '· ' + dist(x.distance_m) : ''}</span><br><span class="muted">${esc(x.summary)}</span></div></a>`).join('');
    };
    $('#ty').onclick = e => { const b = e.target.closest('[data-t]'); if (!b) return; $('#ty').querySelectorAll('.chip').forEach(x => x.classList.toggle('on', x === b)); load(b.dataset.t); };
    load('obiective');
  },

  async servicii() {
    setView(`<h1>Servicii locale</h1><p class="muted">Cele mai apropiate de tine (© OpenStreetMap)</p><div class="chips" id="ty"></div><div id="l"></div>`);
    const pos = await locate() || ARAD;
    const load = async t => {
      const d = await api(`/servicii?type=${t}&lat=${pos[0]}&lng=${pos[1]}`);
      $('#ty').innerHTML = d.types.map(x => `<button class="chip ${x.code === t ? 'on' : ''}" data-t="${x.code}">${esc(x.label)} (${x.count})</button>`).join('');
      $('#l').innerHTML = (t === 'spital' || t === 'politie' ? `<a class="btn red" href="tel:112" style="width:100%;text-align:center">Urgență? Sună la 112</a>` : '') +
        d.items.map(s => `<div class="item"><div class="grow"><b>${esc(s.name)}</b><span class="muted">${esc(s.kind)} ${s.address ? '· ' + esc(s.address) : ''} · ${dist(s.distance_m)}</span>${s.hours ? `<br><span class="muted">${esc(s.hours)}</span>` : ''}</div>${s.phone ? `<a class="btn ghost" href="tel:${esc(s.phone.replace(/\s/g, ''))}">☎</a>` : ''}<a class="btn" target="_blank" href="https://www.google.com/maps/dir/?api=1&destination=${s.lat},${s.lng}">→</a></div>`).join('');
    };
    $('#ty').onclick = e => { const b = e.target.closest('[data-t]'); if (b) load(b.dataset.t); };
    load('farmacie');
  },

  async iasmi() {
    if (!need()) return;
    const hist = [];
    setView(`<h1>Iasmi</h1><p class="muted">Asistentul Aradului: unde mănânci, cum ajungi, taxe, gunoi, sesizări.</p><div id="chat" class="card" style="min-height:280px"></div>
    <form id="f" class="row"><input id="q" placeholder="Ex.: cum ajung la Palatul Cultural?" autocomplete="off"><button class="btn" style="flex:0">Trimite</button></form>`);
    const add = (t, who) => { $('#chat').insertAdjacentHTML('beforeend', `<div class="msg ${who}">${esc(t)}</div>`); $('#chat').scrollTop = 1e9; };
    add('Bună! Sunt Iasmi. Cu ce te ajut?', 'them');
    locate();
    $('#f').onsubmit = async e => {
      e.preventDefault(); const q = $('#q').value.trim(); if (!q) return; $('#q').value = ''; add(q, 'me'); hist.push({ role: 'user', content: q });
      try { const r = await api('/iasmi', { body: { messages: hist.slice(-10), lat: myPos?.[0], lng: myPos?.[1] } }); add(r.reply, 'them'); hist.push({ role: 'assistant', content: r.reply });
        (r.places || []).forEach(p => $('#chat').insertAdjacentHTML('beforeend', `<a class="item" href="#place/${p.id}"><div class="grow"><b>${esc(p.name)}</b><span class="muted">${esc(p.category || '')} ${p.distance_m ? '· ' + dist(p.distance_m) : ''}</span></div></a>`));
      } catch (er) { add(er.message, 'them'); }
    };
  },

  async community() {
    if (!need()) return;
    const d = await api('/community/rooms');
    setView(`<div class="row"><h1>Comunitate</h1><a class="btn ghost" style="flex:0" href="#people"><span class="ms">groups</span>Arădenii</a></div>${d.rooms.map(r => `<a class="item" href="#chat/${r.id}"><div class="grow"><b>${esc(r.name)}</b><span class="muted">${esc(r.last_text || r.description || '')}</span></div>${r.invite_code && r.is_owner ? `<span class="muted">cod ${esc(r.invite_code)}</span>` : ''}</a>`).join('')}
    <div class="sec">Grup nou sau intră cu un cod</div><div class="card"><div class="row"><input id="gn" placeholder="Numele grupului (ex. Bloc 12, Micălaca)"><select id="gt">${d.tags.map(t => `<option value="${t.code}">${esc(t.label)}</option>`).join('')}</select></div><button class="btn" id="mk">Creează grupul</button>
    <div class="row"><input id="code" placeholder="Cod de invitație"><button class="btn ghost" id="jn" style="flex:0">Intră</button></div></div>`);
    $('#mk').onclick = () => api('/community/rooms', { body: { name: $('#gn').value, tag: $('#gt').value } }).then(r => location.hash = '#chat/' + r.id).catch(fail);
    $('#jn').onclick = () => api('/community/rooms/join', { body: { code: $('#code').value } }).then(r => location.hash = '#chat/' + r.id).catch(fail);
  },

  async chat(id) {
    if (!need()) return;
    setView(`<div class="row"><a href="#community" class="back"><span class="ms">arrow_back</span>Comunitate</a><a class="btn ghost" style="flex:0" href="#people/${id}"><span class="ms">group</span>Membri</a></div><div id="ms" class="card" style="min-height:320px;max-height:60vh;overflow:auto"></div>
    <form id="f" class="row"><select id="k" style="flex:0 0 120px"><option value="text">Mesaj</option><option value="anunt">Anunț</option><option value="eveniment">Eveniment</option></select><input id="t" placeholder="Scrie un mesaj" autocomplete="off"><button class="btn" style="flex:0">→</button></form>
    <div id="ev" style="display:none" class="row"><input id="et" placeholder="Titlul evenimentului"><input id="ea" type="datetime-local"><input id="ep" placeholder="Unde (ex. Parcul Europa)"></div>`);
    let last = 0;
    const pull = () => api(`/community/rooms/${id}/messages?after_id=${last}`).then(ms => { ms.forEach(m => { last = m.id;
      $('#ms').insertAdjacentHTML('beforeend', `<div class="msg ${m.mine ? 'me' : 'them'}"><small><a href="#user/${m.user_id}"><u>@${esc(m.user_name)}</u></a>${m.kind !== 'text' ? ' · ' + m.kind : ''}</small><br>${m.event_title ? `<b>🎉 ${esc(m.event_title)}</b> · ${esc((m.event_at || '').slice(0, 16).replace('T', ' '))} · ${esc(m.event_place || '')}<br>` : ''}${esc(m.text)}</div>`); });
      if (ms.length) $('#ms').scrollTop = 1e9; }).catch(() => {});
    every(4000, pull);
    $('#k').onchange = e => $('#ev').style.display = e.target.value === 'eveniment' ? 'flex' : 'none';
    $('#f').onsubmit = async e => {
      e.preventDefault(); const k = $('#k').value, body = { text: $('#t').value.trim() || $('#et').value, kind: k };
      if (k === 'eveniment') Object.assign(body, { event_title: $('#et').value, event_at: $('#ea').value ? new Date($('#ea').value).toISOString() : null, event_place: $('#ep').value });
      try { await api(`/community/rooms/${id}/messages`, { body }); $('#t').value = ''; pull(); } catch (er) { fail(er); }
    };
  },

  async people(roomId) {
    if (!need()) return;
    setView(`<a class="back" href="${roomId ? '#chat/' + roomId : '#community'}"><span class="ms">arrow_back</span>Înapoi</a><h1>${roomId ? 'Membri' : 'Arădenii'}</h1>
    <p class="muted">${roomId ? 'Cine e în conversație' : 'Activi recent primii · atinge pentru profil'}</p><input id="q" placeholder="Caută după username"><div id="l"></div>`);
    let all = [];
    const show = () => { const q = $('#q').value.trim().replace(/^@/, '').toLowerCase();
      $('#l').innerHTML = all.filter(p => !q || p.name.toLowerCase().includes(q)).map(p => `<a class="item" href="#user/${p.id}"><div class="avatar" style="width:44px;height:44px;font-size:18px;box-shadow:none">${p.avatar_url ? `<img src="${img(p.avatar_url)}">` : esc(p.name[0].toUpperCase())}</div>
      <div class="grow"><b>@${esc(p.name)}${p.is_me ? ' (tu)' : ''}</b><span class="muted">Nivel ${p.level} · ${p.thrones} tronuri${p.is_owner ? ' · creatorul grupului' : ''}</span></div>
      ${p.friend ? '<span class="muted">prieten</span>' : p.active ? '<span style="color:#2e7d32;font-weight:800;font-size:13px">● activ</span>' : p.new ? '<span class="muted">nou</span>' : ''}</a>`).join('') || '<p class="muted">Nimeni încă.</p>'; };
    all = await api(roomId ? `/community/rooms/${roomId}/members` : '/community/people'); show();
    let t; $('#q').oninput = () => { clearTimeout(t); t = setTimeout(async () => { if (!roomId) all = await api('/community/people?q=' + encodeURIComponent($('#q').value.trim())); show(); }, 250); };
  },

  async friends() {
    if (!need()) return;
    const d = await api('/friends');
    const row = (u, extra) => `<div class="item"><div class="grow"><a href="#user/${u.id}"><b>@${esc(u.name)}</b></a><span class="muted">Nivel ${u.level} · ${u.thrones} tronuri</span></div>${extra}</div>`;
    setView(`<h1>Prieteni</h1><div class="card"><form id="f" class="row"><input id="u" placeholder="Username (ex. diana.arad)"><button class="btn" style="flex:0">Adaugă</button></form></div>
    ${d.incoming?.length ? `<div class="sec">Cereri primite</div>${d.incoming.map(u => row(u, `<button class="btn" onclick="api('/friends/${u.id}/accept',{method:'POST'}).then(()=>routes.friends()).catch(fail)">Acceptă</button>`)).join('')}` : ''}
    <div class="sec">Prietenii tăi (${d.friends.length})</div>${d.friends.map(u => row(u, `<button class="btn ghost" onclick="api('/community/direct/${u.id}',{method:'POST'}).then(r=>location.hash='#chat/'+r.id).catch(fail)">Chat</button>`)).join('') || '<p class="muted">Încă niciun prieten.</p>'}
    ${d.outgoing?.length ? `<div class="sec">Cereri trimise</div>${d.outgoing.map(u => row(u, '')).join('')}` : ''}`);
    $('#f').onsubmit = e => { e.preventDefault(); api('/friends/request', { body: { username: $('#u').value.replace(/^@/, '') } }).then(r => { toast(r.detail); routes.friends(); }).catch(fail); };
  },

  async polls() {
    if (!need()) return;
    const ps = await api('/polls');
    setView(`<h1>Sondaje</h1><details class="card"><summary><b>+ Sondaj nou</b></summary><input id="q" placeholder="Întrebarea"><input id="o" placeholder="Variante, separate prin virgulă"><button class="btn" id="mk">Publică (+5 XP)</button></details>` +
      ps.map(p => `<div class="card"><p class="muted">${p.official ? '🏛 Primăria Arad întreabă' : '@' + esc(p.author)} · ${p.closed ? 'închis' : 'deschis'}</p><b style="font-size:18px">${esc(p.question)}</b>
      ${p.options.map(o => `<div class="opt" onclick="${p.closed ? '' : `api('/polls/${p.id}/vote',{body:{option_id:${o.id}}}).then(()=>routes.polls()).catch(fail)`}"><i style="width:${p.results_visible ? o.percent : 0}%"></i><span>${p.my_vote === o.id ? '✔ ' : ''}${esc(o.text)}${p.results_visible ? ' · ' + o.percent + '%' : ''}</span></div>`).join('')}
      <p class="muted">${p.results_visible ? p.total_votes + ' voturi' : 'Votează ca să vezi rezultatele · +2 XP'}</p></div>`).join(''));
    $('#mk').onclick = () => api('/polls', { body: { question: $('#q').value, options: $('#o').value.split(',').map(s => s.trim()).filter(Boolean), days: 7 } }).then(() => routes.polls()).catch(fail);
  },

  async top(tab = 'places') {
    const users = tab === 'users';
    const d = await api(users ? '/leaderboard/users?limit=50' : '/leaderboard/places?limit=50');
    setView(`<h1>Topul Aradului</h1><div class="chips"><a class="chip ${users ? '' : 'on'}" href="#top">Locuri</a><a class="chip ${users ? 'on' : ''}" href="#top/users">Arădeni</a></div>` +
      d.map((x, i) => users ? `<a class="item" href="#user/${x.id}"><b style="width:30px">${x.rank}</b><div class="grow"><b>@${esc(x.name)}</b><span class="muted">Nivel ${x.level}</span></div><b>${x.xp} XP</b></a>`
        : `<a class="item" href="#place/${x.id}"><b style="width:30px">${i + 1}</b>${x.photo_url ? `<img src="${img(x.photo_url)}">` : ''}<div class="grow"><b>${esc(x.name)}</b><span class="muted">${esc(x.category_label)} · ${x.checkin_count} check-in-uri</span></div><b>${Math.round(x.rating)}</b></a>`).join(''));
  },

  async user(id) {
    if (!need()) return;
    const u = await api(`/users/${id}/public`);
    setView(`<h1>@${esc(u.name)}</h1><p class="muted">Nivel ${u.level} · ${u.xp} XP · #${u.rank} în Arad</p>
    ${u.friendship === 'none' ? `<button class="btn" onclick="api('/friends/request',{body:{username:'${esc(u.name)}'}}).then(r=>toast(r.detail)).catch(fail)">Adaugă prieten</button>` : `<p class="muted">${{ friends: 'Sunteți prieteni', outgoing: 'Cerere trimisă', incoming: 'Ți-a trimis o cerere', me: 'Acesta ești tu' }[u.friendship] || ''}</p>`}
    <div class="grid"><div class="card"><b>${u.thrones}</b> tronuri</div><div class="card"><b>${u.checkins}</b> check-in-uri</div></div>
    <div class="sec">Tronuri</div>${u.conquered.map(c => `<a class="item" href="#place/${c.id}"><b>👑 ${esc(c.name)}</b></a>`).join('') || '<p class="muted">Încă niciun tron.</p>'}
    <div class="sec">Insigne</div>${u.badges.map(b => `<div class="item"><div class="grow"><b>${esc(b.title)}</b><span class="muted">${esc(b.description)}</span></div></div>`).join('') || '<p class="muted">Încă nicio insignă.</p>'}`);
  },

  async duels() {
    const d = await api('/duels/next');
    const card = p => `<div class="card" style="cursor:pointer" data-w="${p.id}">${p.photo_url ? `<img src="${img(p.photo_url)}" style="width:100%;height:140px;object-fit:cover;border-radius:12px">` : ''}<b>${esc(p.name)}</b><p class="muted">${esc(p.address)}</p></div>`;
    setView(`<h1>Care e mai tare?</h1><p class="muted">${esc(d.category_label)}</p><div class="grid" id="g">${card(d.a)}${card(d.b)}</div><button class="btn ghost" onclick="routes.duels()">Sari peste</button>`);
    $('#g').onclick = e => { const c = e.target.closest('[data-w]'); if (!c || !need()) return; const w = +c.dataset.w, l = w === d.a.id ? d.b.id : d.a.id;
      api('/duels/vote', { body: { winner_id: w, loser_id: l } }).then(() => { toast('Vot înregistrat! +2 XP'); routes.duels(); }).catch(fail); };
  },

  async profile() {
    if (!need()) return;
    const [p, rw] = await Promise.all([api('/users/me'), api('/me/rewards').catch(() => null)]); me = p;
    setView(`<h1>@${esc(p.name)}</h1><p class="muted">Nivel ${p.level} · ${p.xp} XP</p><div class="bar"><i style="width:${100 * p.xp_into_level / p.xp_per_level}%"></i></div>
    <div class="grid" style="margin-top:12px"><div class="card"><b>${p.checkins}</b> check-in-uri</div><div class="card"><b>${p.mayor_of.length}</b> tronuri</div></div>
    ${rw ? `<div class="sec">Recompense (demo)</div>${rw.rewards.map(r => `<div class="card"><b>${esc(r.title)}</b><p class="muted">${esc(r.description)}</p><div class="bar"><i style="width:${100 * r.progress / r.goal}%"></i></div><p class="muted">${r.progress}/${r.goal}${r.voucher ? ' · codul tău: <b>' + esc(r.voucher) + '</b>' : ''}</p></div>`).join('')}` : ''}
    <div class="sec">Misiuni</div>${p.missions.slice(0, 4).map(m => `<div class="card"><b>${esc(m.title)}</b> <span class="muted">${m.progress}/${m.target}</span><div class="bar"><i style="width:${100 * m.progress / m.target}%"></i></div></div>`).join('')}
    <div class="sec">Insigne</div>${p.badges.map(b => `<span class="chip">${esc(b.title)}</span>`).join(' ') || '<p class="muted">Încă nicio insignă.</p>'}
    <div class="sec">Cont</div><a class="btn ghost" href="#reports">Sesizările mele</a> <button class="btn ghost" onclick="logout()">Ieși din cont</button>`);
  },
};

async function confirmReport(id) {
  if (!need()) return; const p = await locate(); if (!p) return toast('Permite locația ca să confirmi.');
  try { const r = await api(`/reports/${id}/confirm`, { body: { lat: p[0], lng: p[1] } }); toast(r.report.confirmed ? 'Confirmată! Ajunge la Primărie.' : `Ai confirmat (${r.report.confirmations}/${r.report.confirms_needed}). +${r.xp_gained} XP`); } catch (e) { fail(e); }
}

/* poza redusă la ~1600 px, ca să urce repede de pe date mobile */
function shrink(file) {
  return new Promise(res => { const im = new Image(); im.onload = () => { const s = Math.min(1, 1600 / Math.max(im.width, im.height)), c = document.createElement('canvas');
    c.width = im.width * s; c.height = im.height * s; c.getContext('2d').drawImage(im, 0, 0, c.width, c.height); c.toBlob(b => res(b || file), 'image/jpeg', .85); };
    im.onerror = () => res(file); im.src = URL.createObjectURL(file); });
}

async function router() {
  timers.forEach(clearInterval); timers = []; speechSynthesis?.cancel(); document.getElementById('mn')?.remove();
  const [name, arg] = (location.hash.slice(1) || 'home').split('/');
  document.querySelectorAll('#tabs a').forEach(a => a.classList.toggle('on', a.getAttribute('href') === '#' + name));
  try { await (routes[name] || routes.home)(arg); } catch (e) { setView(`<p>${esc(e.message)}</p><a class="btn" href="#home">Acasă</a>`); }
}
if (localStorage.getItem('cc_dark') === '1') document.documentElement.classList.add('dark');
$('#themeBtn').onclick = () => localStorage.setItem('cc_dark', document.documentElement.classList.toggle('dark') ? '1' : '0');
document.querySelectorAll('h2').forEach(() => {});
addEventListener('hashchange', router);
router();
