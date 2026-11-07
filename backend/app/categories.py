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
    "biserica": "Biserici și catedrale",
    "manastire": "Mănăstiri",
    "natura": "Natură",
    "mall": "Centre comerciale",
    "supermarket": "Supermarketuri",
    "benzinarie": "Benzinării",
    "cazare": "Hoteluri și pensiuni",
    "eveniment": "Evenimente",
}

FOOD = {"fastfood", "restaurant", "cofetarie", "gelaterie"}


def label(code: str) -> str:
    return CATEGORIES.get(code, code)
