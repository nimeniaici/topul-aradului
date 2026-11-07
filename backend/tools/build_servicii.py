"""Generează app/servicii_data.json din exportul OpenStreetMap (Overpass) al serviciilor din Arad.
Rulare: python tools/build_servicii.py <osm_services.json>"""
import json
import os
import sys

SRC = sys.argv[1]
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "servicii_data.json")
KIND = {"hospital": "Spital", "clinic": "Clinică", "doctors": "Cabinet medical", "dentist": "Cabinet stomatologic",
        "school": "Școală", "kindergarten": "Grădiniță", "college": "Colegiu", "university": "Universitate",
        "police": "Poliție", "fire_station": "Pompieri", "bank": "Bancă", "atm": "Bancomat", "post_office": "Poștă",
        "pharmacy": "Farmacie", "townhall": "Primărie", "courthouse": "Instanță"}

raw = json.load(open(SRC, encoding="utf-8"))
items, seen = [], set()
for typ, elements in raw.items():
    for e in elements:
        t = e.get("tags", {})
        c = e.get("center") or e
        if "lat" not in c:
            continue
        amenity = t.get("amenity") or t.get("office") or ""
        kind = KIND.get(amenity, "Instituție publică" if typ == "public" else "")
        name = (t.get("name") or t.get("brand") or t.get("operator") or "").strip()
        if not name:
            if typ != "banca":          # bancomatele fără nume le păstrăm cu banca operatoare, restul nu
                continue
            name = kind
        key = (typ, name.lower(), round(c["lat"], 4), round(c["lon"], 4))
        if key in seen:
            continue
        seen.add(key)
        street = " ".join(x for x in (t.get("addr:street"), t.get("addr:housenumber")) if x)
        items.append({"id": f"{e['type'][0]}{e['id']}", "type": typ, "kind": kind, "name": name,
                      "lat": round(c["lat"], 6), "lng": round(c["lon"], 6), "address": street,
                      "phone": t.get("phone") or t.get("contact:phone") or "",
                      "hours": t.get("opening_hours") or "", "website": t.get("website") or t.get("contact:website") or "",
                      "emergency": t.get("emergency") == "yes" or (amenity == "hospital" and t.get("emergency") != "no"),
                      "open_24_7": t.get("opening_hours") == "24/7"})
json.dump({"source": "© OpenStreetMap contributors (ODbL)", "items": items}, open(OUT, "w", encoding="utf-8"),
          ensure_ascii=False, separators=(",", ":"))
counts = {}
for x in items:
    counts[x["type"]] = counts.get(x["type"], 0) + 1
print(len(items), counts)
