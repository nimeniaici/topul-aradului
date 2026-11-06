"""Categoriile de locuri: cod -> etichetă afișată în aplicație (în ordinea din filtre)."""

CATEGORIES: dict[str, str] = {
    "fastfood": "Fast food",
    "restaurant": "Restaurante",
    "cafenea": "Cafenele",
    "cofetarie": "Cofetării",
    "gelaterie": "Gelaterii",
    "terasa": "Terase",
    "bar": "Baruri",
    "obiectiv": "Obiective",
    "manastire": "Mănăstiri",
    "natura": "Natură",
    "mall": "Centre comerciale",
    "eveniment": "Evenimente",
}

FOOD = {"fastfood", "restaurant", "cofetarie", "gelaterie"}


def label(code: str) -> str:
    return CATEGORIES.get(code, code)
