"""Categoriile de locuri: cod -> etichetă afișată în aplicație."""

CATEGORIES: dict[str, str] = {
    "pizza": "Pizza",
    "burger": "Burger",
    "restaurant": "Restaurant",
    "cafenea": "Cafenea",
    "desert": "Desert",
    "terasa": "Terasă",
    "bar": "Bar",
    "producator": "Producător local",
    "obiectiv": "Obiectiv turistic",
    "natura": "Natură & plimbare",
    "eveniment": "Eveniment",
}

FOOD = {"pizza", "burger", "restaurant", "desert"}


def label(code: str) -> str:
    return CATEGORIES.get(code, code)
