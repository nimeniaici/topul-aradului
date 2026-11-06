"""Construiește app/seed_places.json din date OpenStreetMap (© OpenStreetMap contributors, ODbL).

Folosire:  python tools/build_seed_from_osm.py arad.json arad2.json app/seed_places.json
  arad.json  = restaurante, cafenele, baruri, obiective, parcuri (Overpass, orașul Arad)
  arad2.json = fast food, cofetării, gelaterii, terase, mall-uri (Overpass, orașul Arad)
Locurile din județ (cetăți, mănăstiri, Moneasa) au coordonatele din OpenStreetMap trecute direct mai jos.
"""
import json
import math
import sys
from collections import Counter

sys.stdout.reconfigure(encoding="utf-8")
*osm_files, out_file = sys.argv[1:]
els = [e for f in osm_files for e in json.load(open(f, encoding="utf-8"))["elements"] if "tags" in e]
CENTER = (46.1745, 21.3185)


def coords(e):
    return (e.get("lat") or e["center"]["lat"], e.get("lon") or e["center"]["lon"])


def find(name, near=CENTER):
    cands = [e for e in els if e["tags"].get("name") == name]
    if not cands:
        raise SystemExit(f"NU găsesc în OSM: {name}")
    return min(cands, key=lambda e: math.dist(coords(e), near))


def address(t):
    s = " ".join(x for x in (t.get("addr:street", ""), t.get("addr:housenumber", "")) if x).strip()
    return f"{s}, Arad" if s else "Arad"


# categorie -> [(nume în OSM, nume afișat sau None, descriere, aproape de (lat, lng) sau None)]
PICKS = {
    "fastfood": [
        ("KFC", "KFC Atrium", "Pui crocant, lângă Atrium Center.", (46.1903, 21.3202)),
        ("KFC", "KFC Aurel Vlaicu", "Pui crocant.", (46.1853, 21.3152)),
        ("Big Chicken", None, "Pui crocant și burgeri.", None),
        ("McDonald's", "McDonald's Centru", "Burgeri, în centru.", (46.1754, 21.3177)),
        ("McDonald's", "McDonald's Aurel Vlaicu", "Burgeri.", (46.1911, 21.3159)),
        ("Burger King", None, "Burgeri.", None),
        ("Mr. Paco", None, "Pui și cartofi prăjiți.", None),
        ("Shock Kebab Teatru", None, "Kebab, lângă teatru.", None),
        ("Mesopotamia", None, "Kebab.", None),
        ("La Raul", None, "Burgeri, hot-dog, kebab.", None),
        ("Foodie The Van", None, "Street food din food-truck.", None),
        ("Eat like a man", None, "Burgeri și salate.", None),
        ("Noodle Pack", None, "Noodles la cutie.", None),
        ("Pizza 5 Colțuri", None, "Pizza la felie.", (46.1919, 21.3171)),
    ],
    "restaurant": [
        ("Euphoria Biergarten Arad", None, "Bucătărie germană și locală.", None),
        ("Mantua", None, "Restaurant în centru.", None),
        ("Cine Citta", None, "Restaurant în centru.", None),
        ("KHARMA", None, "Steak, burgeri, bucătărie italiană.", None),
        ("Pizzeria Milano", None, "Pizzerie italiană pe Bulevardul Revoluției.", None),
        ("MONDOPIZZA", None, "Pizza, paste, salate.", None),
        ("Pizzeria Don", None, "Pizzerie.", None),
        ("Ema Del Mar", None, "Pește și fructe de mare.", None),
        ("La Bottega", None, "Restaurant în centru.", None),
        ("Restaurant Picasso", None, "Restaurant.", None),
        ("Vegan Time", None, "Mâncare vegană.", None),
        ("Restaurant Coandi", None, "Restaurant.", None),
        ("Taverna Libaneză", None, "Bucătărie libaneză.", None),
        ("Casa Bulzan", None, "Bucătărie tradițională.", None),
        ("Restaurant Transilvania", None, "Restaurant.", None),
        ("Asia Wok", None, "Bucătărie asiatică.", None),
        ("Restaurant Belvedere", None, "Restaurant pe malul Mureșului.", None),
        ("Falezza", None, "Restaurant lângă Mureș.", None),
    ],
    "cafenea": [
        ("Snooze Coffee", None, "Cafenea cu terasă.", None),
        ("Corso", None, "Cafenea pe strada Cloșca.", None),
        ("Makor Specialty Coffee", None, "Cafea de specialitate.", None),
        ("5 to go", None, "Cafea la pahar.", None),
        ("Boeme Cafe", None, "Cafenea.", None),
        ("Le Petit café", None, "Cafenea.", None),
        ("Kaffeine", None, "Cafenea.", None),
        ("Just Blvck Coffee", None, "Cafea de specialitate.", None),
    ],
    "cofetarie": [
        ("Cofetăria Libelula", None, "Prăjituri și torturi.", None),
        ("Boema Delice", None, "Cofetărie.", None),
        ("Cofetăria Boema", None, "Cofetărie.", None),
        ("Cofetăria Crișan", None, "Cofetărie clasică.", (46.1789, 21.3185)),
        ("Caprice", None, "Cofetărie.", None),
        ("Semico Gourmet Pastry", None, "Patiserie fină.", None),
        ("Venetia", None, "Cofetărie.", None),
    ],
    "gelaterie": [
        ("Gelateria Coandi", None, "Înghețată artizanală.", None),
        ("Delizioso", None, "Înghețată.", None),
    ],
    "terasa": [
        ("Jelen Terasa de Vară", None, "Terasă de vară.", None),
        ("Port Arthur", None, "Grădină de bere, lângă Mureș.", None),
        ("La Ponton", None, "Terasă pe ponton, pe Mureș.", None),
        ("Ratio Beach", None, "Terasă de plajă pe malul Mureșului.", None),
    ],
    "bar": [
        ("Jad Café", None, "Pub.", None),
        ("Sky", None, "Pub.", None),
        ("Luci Bar", None, "Bar.", None),
        ("One More Beer", None, "Bere și terasă.", None),
        ("Art Cafe", None, "Bar.", None),
        ("Wings", None, "Pub în Piața Avram Iancu.", None),
    ],
    "obiectiv": [
        ("Palatul Cultural", None, "Complexul Muzeal și Filarmonica de Stat.", None),
        ("Teatrul Clasic Ioan Slavici", "Teatrul Clasic „Ioan Slavici”", "Teatrul din centrul Aradului.", None),
        ("Statuia libertății", "Statuia Libertății", "Monumentul celor 13 generali martiri de la 1849.", None),
        ("Cetatea Aradului", None, "Cetate în stil Vauban din secolul al XVIII-lea.", None),
        ("Catedrala Arhiepiscopală ,,Sfânta Treime\"", "Catedrala „Sfânta Treime”", "Catedrala ortodoxă din Piața Caius Iacob.", None),
        ("Arcul de Triumf", None, "Lângă Parcul Reconcilierii.", None),
        ("Palatul Neumann", None, "Palat istoric din centru.", None),
        ("Casa cu lacăt", None, "Clădire istorică cu legenda lacătului.", None),
        ("Palatul Băncii Naționale", None, "Clădire istorică pe Bulevardul Revoluției.", None),
        ("Muzeul de Istoria Calculatoarelor", None, "Pentru pasionații de tehnologie.", None),
        ("Turnul de apă", None, "Turn de apă transformat în muzeu.", None),
        ("Teatrul de Marionete", None, "Spectacole pentru copii.", None),
    ],
    "natura": [
        ("Parcul Europa", None, "Parc pe malul Mureșului.", None),
        ("Ștrandul Neptun", None, "Ștrand și zonă de agrement.", None),
        ("Parcul Pădurice", None, "Parc cu mulți copaci, aproape de centru.", None),
        ("Parcul Mihai Eminescu", None, "Parc pe malul Mureșului.", None),
        ("Parcul Copiilor", None, "Loc de joacă și plimbare.", None),
        ("Parcul Reconcilierii", None, "Parcul cu Statuia Libertății.", None),
        ("Parcul Alfa", None, "Parc de cartier.", None),
    ],
    "mall": [
        ("Atrium Center", "Atrium Mall", "Centru comercial.", None),
        ("Ziridava Shopping Center", None, "Centru comercial în centru.", None),
        ("Agora Arad", None, "Centru comercial.", None),
        ("Galleria", "Galleria Mall", "Centru comercial.", None),
    ],
}

# Locuri din afara orașului sau fără nume potrivit în OSM: coordonate din OSM/Nominatim
EXTRA = [
    ("eveniment", "Hack4Arad @ Complex M (UAV)", "Locul hackathonului Hack4Arad 2026! Fă check-in aici.",
     "Str. Elena Drăgoi nr. 2, Arad", 46.17665, 21.34360),
    ("obiectiv", "Palatul Administrativ (Primăria)", "Clădirea Primăriei, simbol al Bulevardului Revoluției.",
     "Bulevardul Revoluției 75, Arad", 46.17537, 21.31930),
    ("obiectiv", "Cetatea Șoimoș", "Ruinele cetății de lângă Lipova, cu vedere spre Mureș.", "Lipova, jud. Arad", 46.10877, 21.72299),
    ("obiectiv", "Cetatea Ineului", "Cetate și muzeu în orașul Ineu.", "Ineu, jud. Arad", 46.42825, 21.84123),
    ("obiectiv", "Podgoria Miniș-Măderat", "Podgoria cu tradiție a județului: vinuri locale.", "Miniș, jud. Arad", 46.13502, 21.60452),
    ("manastire", "Mănăstirea Hodoș-Bodrog", "Una dintre cele mai vechi mănăstiri ortodoxe din România.", "jud. Arad", 46.13370, 21.18382),
    ("manastire", "Mănăstirea Gai", "Mănăstire ortodoxă în cartierul Gai din Arad.", "Gai, Arad", 46.21871, 21.27349),
    ("manastire", "Mănăstirea „Maria Radna”", "Mănăstire franciscană și sanctuar marian, lângă Lipova.", "Radna, Lipova, jud. Arad", 46.09887, 21.68611),
    ("manastire", "Mănăstirea Bezdin", "Mănăstire ortodoxă sârbă, în pădurea de lângă Mureș.", "Munar, jud. Arad", 46.13959, 21.02770),
    ("manastire", "Mănăstirea Feredeu", "Mănăstire ortodoxă în Munții Zărandului.", "Feredeu, jud. Arad", 46.24146, 21.65526),
    ("natura", "Stațiunea Moneasa", "Stațiune balneară în Munții Codru-Moma.", "Moneasa, jud. Arad", 46.46111, 22.25674),
]

out = []
for cat, picks in PICKS.items():
    for osm_name, shown, desc, near in picks:
        e = find(osm_name, near or CENTER)
        lat, lng = coords(e)
        out.append({"name": shown or osm_name, "category": cat, "description": desc,
                    "address": address(e["tags"]), "lat": round(lat, 6), "lng": round(lng, 6), "verified": True})
for cat, name, desc, addr, lat, lng in EXTRA:
    out.append({"name": name, "category": cat, "description": desc, "address": addr,
                "lat": lat, "lng": lng, "verified": True})

names = Counter(p["name"] for p in out)
dupes = [n for n, c in names.items() if c > 1]
if dupes:
    raise SystemExit(f"Nume duplicate: {dupes}")
json.dump(out, open(out_file, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(len(out), dict(Counter(p["category"] for p in out)))
