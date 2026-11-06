"""Pagini publice simple (cerute de Google pentru ecranul de login): despre, confidențialitate, termeni."""
from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter(tags=["pages"], include_in_schema=False)

CONTACT = "dandiana211@gmail.com"
UPDATED = "6 noiembrie 2026"

_STYLE = """
:root { color-scheme: dark; }
body { margin: 0; background: #0F1226; color: #E6E8FF; font: 16px/1.65 system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
main { max-width: 760px; margin: 0 auto; padding: 40px 20px 64px; }
h1 { color: #FFD23F; font-size: 30px; margin: 0 0 4px; }
h2 { color: #FFFFFF; font-size: 20px; margin: 32px 0 8px; }
a { color: #FFD23F; }
.muted { color: #9AA0D0; font-size: 14px; }
.card { background: #1B2045; border-radius: 16px; padding: 16px 20px; margin: 16px 0; }
nav { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 28px; font-size: 14px; }
ul { padding-left: 22px; }
"""


def _page(title: str, body: str) -> HTMLResponse:
    html = f"""<!doctype html>
<html lang="ro"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title} · Topul Aradului</title><style>{_STYLE}</style></head>
<body><main>
<nav><a href="/despre">👑 Topul Aradului</a><a href="/confidentialitate">Confidențialitate</a><a href="/termeni">Termeni</a></nav>
{body}
<p class="muted" style="margin-top:40px">Contact: <a href="mailto:{CONTACT}">{CONTACT}</a> · Actualizat: {UPDATED}</p>
</main></body></html>"""
    return HTMLResponse(html)


@router.get("/despre", response_class=HTMLResponse)
def about():
    return _page("Despre", """
<h1>👑 Topul Aradului</h1>
<p class="muted">Proiect pentru Hack4Arad 2026 · Universitatea „Aurel Vlaicu” din Arad</p>
<p>Harta Aradului votată de arădeni. Descoperi restaurante, cafenele, parcuri și obiective,
faci check-in când ești acolo, votezi în dueluri „care e mai bun?” și cucerești titlul de
<b>Primar</b> al locurilor tale preferate.</p>
<div class="card">
<ul>
<li>📍 Check-in verificat prin GPS (doar când ești lângă loc)</li>
<li>⚔️ Dueluri între locuri, cu clasament în stil Elo</li>
<li>🔥 Hartă live cu locurile unde e agitație acum</li>
<li>🏅 Niveluri, insigne și misiuni</li>
</ul>
</div>
<p>Datele locurilor provin din <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>
(© contribuitorii OpenStreetMap, licență ODbL).</p>
""")


@router.get("/confidentialitate", response_class=HTMLResponse)
def privacy():
    return _page("Politica de confidențialitate", """
<h1>Politica de confidențialitate</h1>
<p>Această pagină explică ce date colectează aplicația <b>Topul Aradului</b>, de ce și cum le poți șterge.</p>

<h2>Ce date colectăm</h2>
<ul>
<li><b>Datele contului:</b> numele afișat, adresa de email și parola (salvată doar criptat, sub formă de hash).
Dacă intri cu Google, primim de la Google numele, emailul și un identificator al contului Google. Nu primim parola ta Google.</li>
<li><b>Locația, doar la check-in, și nu o păstrăm:</b> când apeși „Check-in”, aplicația trimite poziția GPS
din acel moment, iar serverul o folosește <b>doar</b> ca să verifice că ești la cel mult 100 m de loc. Apoi o aruncă:
salvăm doar <i>ce loc</i> ai vizitat și când, nu unde te aflai exact. Nu urmărim locația în fundal.</li>
<li><b>Poze:</b> doar dacă alegi să faci check-in cu poză. Pozele sunt vizibile celorlalți utilizatori pe pagina locului.</li>
<li><b>Sesizări:</b> dacă semnalezi o problemă (de exemplu o groapă), salvăm poza, descrierea și poziția
<i>problemei</i>, ca să apară pe hartă și să poată fi rezolvată. Sesizările sunt publice, cu numele tău afișat.</li>
<li><b>Activitatea în joc:</b> check-in-urile, voturile din dueluri, punctele XP, insignele și avatarul ales.</li>
</ul>

<h2>Ce e public</h2>
<p>Numele afișat, avatarul, nivelul, punctele și titlurile de Primar apar în clasamente și pe paginile locurilor.
Emailul nu este afișat niciodată altor utilizatori.</p>

<h2>De ce folosim datele</h2>
<ul>
<li>ca să-ți ținem contul și progresul în joc;</li>
<li>ca să verificăm check-in-urile (anti-trișare);</li>
<li>ca să-ți trimitem, la cerere, codul pentru resetarea parolei.</li>
</ul>
<p>Nu vindem date, nu afișăm reclame și nu folosim datele pentru publicitate.</p>

<h2>Cum le protejăm</h2>
<ul>
<li>Comunicarea dintre aplicație și server este criptată (HTTPS).</li>
<li>Parolele nu sunt salvate niciodată în clar, ci doar ca hash criptografic (PBKDF2 cu „salt”), pe care nici noi nu-l putem inversa.</li>
<li>Păstrăm doar datele strict necesare jocului (minimizarea datelor).</li>
<li>Datele sunt stocate pe serverele furnizorului de găzduire Railway.</li>
</ul>

<h2>Cât le păstrăm și cum le ștergi</h2>
<p>Păstrăm datele cât timp ai cont. Poți șterge oricând contul din aplicație:
<b>Profil → Șterge contul definitiv</b>. Se șterg imediat contul, check-in-urile, pozele, voturile și insignele.
Pentru orice cerere privind datele tale (acces, rectificare, ștergere), scrie-ne la adresa de mai jos.</p>
""")


@router.get("/termeni", response_class=HTMLResponse)
def terms():
    return _page("Termeni de utilizare", """
<h1>Termeni de utilizare</h1>
<ul>
<li>Topul Aradului este un proiect demonstrativ realizat pentru hackathonul Hack4Arad 2026 și este oferit „ca atare”.</li>
<li>Folosește aplicația cu bun-simț: fără locații simulate, fără conturi multiple pentru a influența clasamentele.</li>
<li>Pozele încărcate trebuie să fie ale tale și potrivite pentru public. Ne rezervăm dreptul de a șterge conținut nepotrivit.</li>
<li>Clasamentele reflectă voturile comunității și nu sunt recenzii oficiale ale localurilor.</li>
<li>Prelucrarea datelor personale este descrisă în <a href="/confidentialitate">Politica de confidențialitate</a>.</li>
</ul>
""")
