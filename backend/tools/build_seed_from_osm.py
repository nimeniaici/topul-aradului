"""Construiește seed_places.json din datele OpenStreetMap (© OpenStreetMap contributors, ODbL)."""
import json, sys, math
sys.stdout.reconfigure(encoding="utf-8")
osm_file, out_file = sys.argv[1], sys.argv[2]
els = json.load(open(osm_file, encoding="utf-8"))["elements"]
C = (46.1745, 21.3185)

def coords(e):
    return (e.get("lat") or e["center"]["lat"], e.get("lon") or e["center"]["lon"])

def find(name, near=C):
    cands = [e for e in els if e["tags"].get("name") == name]
    if not cands:
        raise SystemExit(f"NU găsesc: {name}")
    return min(cands, key=lambda e: math.dist(coords(e), near))

CUISINE = {"pizza": "pizza", "italian": "italian", "burger": "burgeri", "regional": "local", "german": "german",
           "seafood": "pește & fructe de mare", "steak_house": "steak", "coffee_shop": "cafea", "cake": "prăjituri",
           "brunch": "brunch", "chicken": "pui", "salad": "salate", "lebanese": "libanez", "asian": "asiatic"}

def address(t):
    s = " ".join(x for x in (t.get("addr:street", ""), t.get("addr:housenumber", "")) if x).strip()
    return (s + ", Arad") if s else "Arad"

def desc(t, default):
    cs = [CUISINE[c] for c in t.get("cuisine", "").replace(",", ";").split(";") if c in CUISINE]
    return (f"Bucătărie: {', '.join(dict.fromkeys(cs))}." if cs else default)

PICKS = {
    "pizza": ["Pizzeria Milano", "MONDOPIZZA", "Pizzeria Don", "Pizza Drive", "Pizza Ruby", "Venezzia Pizza Express"],
    "burger": ["KHARMA", "Eat like a man", "La Casa de Burger", "Foodie The Van", "La Raul", "McDonald's"],
    "restaurant": ["Euphoria Biergarten Arad", "Mantua", "Cine Citta", "Ema Del Mar", "La Bottega", "Restaurant Picasso",
                   "Vegan Time", "Restaurant Coandi", "Taverna Libaneză", "Casa Bulzan", "Restaurant Transilvania", "Asia Wok"],
    "cafenea": ["Snooze Coffee", "Corso", "Makor Specialty Coffee", "5 to go", "Boeme Cafe", "Le Petit café",
                "Kaffeine", "Just Blvck Coffee"],
    "desert": ["Cofetăria Libelula", "Gelateria Coandi", "Boema Delice", "Caprice", "Delizioso", "Semico Gourmet Pastry"],
    "terasa": ["Restaurant Belvedere", "Ratio Beach", "La Ponton", "Falezza"],
    "bar": ["Jad Café", "Sky", "Luci Bar", "One More Beer", "Art Cafe", "Wings"],
}
DEFAULT_DESC = {"pizza": "Pizzerie din Arad.", "burger": "Burgeri în Arad.", "restaurant": "Restaurant din Arad.",
                "cafenea": "Cafenea din Arad.", "desert": "Dulciuri și prăjituri.", "terasa": "Local cu terasă.",
                "bar": "Bar / pub din Arad."}

LANDMARKS = [  # (nume OSM, nume afișat, categorie, descriere)
    ("Palatul Cultural", "Palatul Cultural", "obiectiv", "Complexul Muzeal și Filarmonica de Stat, lângă Mureș."),
    ("Teatrul Clasic Ioan Slavici", "Teatrul Clasic „Ioan Slavici”", "obiectiv", "Teatrul din centrul Aradului."),
    ("Statuia libertății", "Statuia Libertății", "obiectiv", "Monumentul dedicat celor 13 generali martiri de la 1849."),
    ("Cetatea Aradului", "Cetatea Aradului", "obiectiv", "Cetate în stil Vauban din secolul al XVIII-lea."),
    ("Catedrala Arhiepiscopală ,,Sfânta Treime\"", "Catedrala „Sfânta Treime”", "obiectiv", "Catedrala ortodoxă din Piața Caius Iacob."),
    ("Arcul de Triumf", "Arcul de Triumf", "obiectiv", "Lângă Parcul Reconcilierii."),
    ("Palatul Neumann", "Palatul Neumann", "obiectiv", "Palat istoric din centru."),
    ("Casa cu lacăt", "Casa cu lacăt", "obiectiv", "Clădire istorică cu legenda lacătului."),
    ("Palatul Băncii Naționale", "Palatul Băncii Naționale", "obiectiv", "Clădire istorică pe Bulevardul Revoluției."),
    ("Muzeul de Istoria Calculatoarelor", "Muzeul de Istoria Calculatoarelor", "obiectiv", "Pentru pasionații de tehnologie."),
    ("Turnul de apă", "Turnul de apă", "obiectiv", "Turn de apă transformat în muzeu."),
    ("Teatrul de Marionete", "Teatrul de Marionete", "obiectiv", "Spectacole pentru copii."),
    ("Parcul Europa", "Parcul Europa", "natura", "Parc pe malul Mureșului."),
    ("Ștrandul Neptun", "Ștrandul Neptun", "natura", "Ștrand și zonă de agrement."),
    ("Parcul Pădurice", "Parcul Pădurice", "natura", "Parc cu mulți copaci, aproape de centru."),
    ("Parcul Mihai Eminescu", "Parcul Mihai Eminescu", "natura", "Parc pe malul Mureșului, lângă centru."),
    ("Parcul Copiilor", "Parcul Copiilor", "natura", "Loc de joacă și plimbare."),
    ("Parcul Reconcilierii", "Parcul Reconcilierii", "natura", "Parcul cu Statuia Libertății."),
    ("Parcul Alfa", "Parcul Alfa", "natura", "Parc de cartier."),
]

# Din Nominatim (OpenStreetMap), coordonate verificate
EXTRA = [
    {"name": "Hack4Arad @ Complex M (UAV)", "category": "eveniment",
     "description": "Locul hackathonului Hack4Arad 2026! Fă check-in aici.",
     "address": "Str. Elena Drăgoi nr. 2, Arad", "lat": 46.17665, "lng": 21.34360},
    {"name": "Palatul Administrativ (Primăria)", "category": "obiectiv",
     "description": "Clădirea Primăriei, simbol al Bulevardului Revoluției.",
     "address": "Bulevardul Revoluției 75, Arad", "lat": 46.17537, "lng": 21.31930},
    {"name": "Cetatea Șoimoș", "category": "obiectiv", "description": "Ruinele cetății de lângă Lipova, cu vedere spre Mureș.",
     "address": "Lipova, jud. Arad", "lat": 46.10877, "lng": 21.72299},
    {"name": "Cetatea Ineului", "category": "obiectiv", "description": "Cetate și muzeu în orașul Ineu.",
     "address": "Strada Ioan Slavici, Ineu, jud. Arad", "lat": 46.42825, "lng": 21.84123},
    {"name": "Mănăstirea Hodoș-Bodrog", "category": "obiectiv", "description": "Una dintre cele mai vechi mănăstiri din România.",
     "address": "jud. Arad", "lat": 46.13365, "lng": 21.18374},
    {"name": "Stațiunea Moneasa", "category": "natura", "description": "Stațiune balneară în Munții Codru-Moma.",
     "address": "Moneasa, jud. Arad", "lat": 46.46111, "lng": 22.25674},
    {"name": "Podgoria Miniș-Măderat", "category": "producator", "description": "Podgoria cu tradiție a județului: vinuri locale.",
     "address": "Miniș, jud. Arad", "lat": 46.13502, "lng": 21.60452},
]

out = []
for cat, names in PICKS.items():
    for n in names:
        e = find(n); t = e["tags"]; lat, lng = coords(e)
        out.append({"name": n, "category": cat, "description": desc(t, DEFAULT_DESC[cat]),
                    "address": address(t), "lat": round(lat, 6), "lng": round(lng, 6), "verified": True})
for osm_name, shown, cat, d in LANDMARKS:
    e = find(osm_name); lat, lng = coords(e)
    out.append({"name": shown, "category": cat, "description": d, "address": address(e["tags"]),
                "lat": round(lat, 6), "lng": round(lng, 6), "verified": True})
for x in EXTRA:
    out.append({**x, "verified": True})

json.dump(out, open(out_file, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
from collections import Counter
print(len(out), Counter(p["category"] for p in out))
