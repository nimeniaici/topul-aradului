"""Iasmi „local”: asistentul care merge GRATUIT, fără niciun serviciu AI plătit.

Înțelege întrebarea după cuvinte-cheie (fără diacritice, cu forme apropiate: „cafea”, „cafeaua”, „cafenea”),
apoi răspunde din:
- datele aplicației (locuri, distanțe, sesizări, Primarul fiecărui loc),
- o mică bază de cunoștințe despre Arad și serviciile publice (fără prețuri/orare inventate),
- ghidul aplicației.
"""
import random
import re
import unicodedata
from datetime import timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from .categories import CATEGORIES
from .geo import distance_m
from .models import Place, Report, utcnow


def plain(text: str) -> str:
    t = unicodedata.normalize("NFKD", (text or "").lower()).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9 ]+", " ", t)


def _has(t: str, *roots: str) -> bool:
    """Caută rădăcini de cuvinte („plat” găsește plătesc, plata, plăți)."""
    words = t.split()
    return any(w.startswith(r) for r in roots for w in words)


# ce categorii de locuri înseamnă cuvintele din întrebare
CATEGORY_WORDS = {
    "fastfood": ("fast", "burger", "shaorm", "kfc", "mcdonald", "pui", "cartof", "rapid"),
    "restaurant": ("restaurant", "mananc", "mancare", "pranz", "cina", "pizz", "paste", "foame", "manca"),
    "cafenea": ("cafe", "cafea", "cafen", "espresso", "latte", "cappuccino", "ceai"),
    "cofetarie": ("cofet", "prajit", "tort", "dulce", "desert", "patiser"),
    "gelaterie": ("inghet", "gelat", "gelato"),
    "terasa": ("teras", "afara", "aer liber"),
    "bar": ("bar", "bere", "cocktail", "iesit seara", "party", "petrecere"),
    "obiectiv": ("obiectiv", "istori", "muzeu", "palat", "cetat", "monument", "turist", "vizit", "statui", "teatr", "vechi"),
    "manastire": ("manastir", "rugaciun"),
    "biserica": ("bisearic", "biseric", "catedral", "sinagog", "slujb"),
    "supermarket": ("supermarket", "market", "lidl", "kaufland", "profi", "penny", "carrefour", "cumparatur", "alimentar"),
    "benzinarie": ("benzin", "motorin", "carburant", "alimentez", "petrom", "omv", "mol", "rompetrol", "lukoil", "gpl"),
    "cazare": ("hotel", "pensiun", "cazar", "dorm", "inna", "camer", "motel", "hostel"),
    "natura": ("parc", "plimb", "natur", "strand", "mures", "copii", "joaca", "verde", "padur"),
    "mall": ("mall", "cumpar", "shopping", "magazin", "haine"),
}

STOP = {"unde", "care", "este", "sunt", "cum", "pot", "vreau", "un", "o", "la", "in", "din", "de", "si", "cu", "pe",
        "ce", "mai", "bun", "buna", "bune", "aproape", "langa", "mine", "arad", "aradul", "aradului", "ma", "imi", "iau",
        "gasesc", "recomanzi", "recomanda", "ceva", "loc", "locuri", "the", "a", "an", "where", "can", "i", "get", "to"}


def _intent_category(t: str) -> str | None:
    best, hits = None, 0
    for cat, roots in CATEGORY_WORDS.items():
        n = sum(1 for r in roots if r in t)
        if n > hits:
            best, hits = cat, n
    return best


def _keywords(t: str) -> list[str]:
    return [w for w in t.split() if len(w) > 2 and w not in STOP]


def _match_places(db: Session, t: str, category: str | None) -> list[Place]:
    places = db.scalars(select(Place)).all()
    words = _keywords(t)

    def name_hits(p: Place) -> int:
        hay = plain(f"{p.name} {p.address}")
        return sum(1 for w in words if w in hay or (len(w) >= 5 and w[:-1] in hay))

    named = sorted((p for p in places if name_hits(p)), key=name_hits, reverse=True)
    if category:
        # „o cafea” = cafenele, chiar dacă un bar are „cafe” în nume; numele exacte din categorie vin primele
        in_cat = [p for p in places if p.category == category]
        exact = [p for p in named if p.category == category]
        strong = [p for p in named if name_hits(p) >= 2]  # ex. „Cetatea Aradului” scris complet
        return strong or exact + [p for p in in_cat if p not in exact]
    if named:
        return named
    desc = [p for p in places if any(w in plain(p.description) for w in words)]
    return desc


def _fmt_dist(m: float) -> str:
    return f"{round(m)} m" if m < 1000 else f"{m / 1000:.1f} km"


def _walk(m: float) -> str:
    mins = max(1, round(m * 1.3 / 83))
    return f"~{mins} min pe jos" if mins <= 40 else "mai bine cu tramvaiul sau mașina"


def _place_dict(p: Place, near) -> dict:
    d = {"id": p.id, "name": p.name, "category": CATEGORIES.get(p.category, p.category), "address": p.address}
    if near:
        d["distance_m"] = round(distance_m(near[0], near[1], p.lat, p.lng))
    return d


KNOWLEDGE = [
    (("impozit", "taxa", "taxe", "amend", "ghiseul"),
     "Taxele și impozitele locale (casă, teren, mașină) și amenzile se pot plăti online pe ghiseul.ro, cu cardul, "
     "sau la casieriile Direcției Venituri a Primăriei Arad. Pe ghiseul.ro îți faci cont o singură dată și vezi tot ce ai de plată."),
    (("apa", "canal", "factur"),
     "Factura la apă o plătești la Compania de Apă Arad: online din contul de client, la casieriile lor sau prin bancă. "
     "La curent și gaze, plata se face la furnizorul tău, în aplicația sau pe site-ul lui. Ai numărul de client pe factură."),
    (("bilet", "abonament", "tramvai", "autobuz", "ctp", "transport"),
     "Transportul în comun din Arad e asigurat de CTP Arad (tramvaie și autobuze). Biletele și abonamentele se cumpără de la "
     "punctele de vânzare CTP; prețurile și orarele actualizate sunt pe site-ul CTP Arad. Pentru un loc anume, deschide-l în "
     "aplicație și apasă „Du-mă acolo”."),
    (("parcar", "parca"),
     "Parcarea în zonele cu plată din Arad se plătește la automate, prin SMS sau prin aplicațiile de plată a parcării; "
     "detaliile și zonele sunt pe site-ul Primăriei Arad. O mașină parcată ilegal o poți semnala din „Semnalează o problemă”."),
    (("act", "buletin", "pasaport", "permis", "inmatricul", "certificat"),
     "Pentru acte (buletin, certificate de stare civilă, documente de la Primărie) găsești programul și programările pe "
     "site-ul Primăriei Arad, la serviciul de evidență a persoanelor sau de stare civilă. Ia cu tine actul de identitate."),
    (("gunoi", "salubr", "deseu", "reciclar"),
     "Zilele de ridicare a gunoiului pe strada ta le găsești în Acasă → Calendar gunoi (date RETIM). Gunoiul aruncat "
     "unde nu trebuie îl poți semnala din „Semnalează o problemă” → „Gunoi / mizerie”."),
    (("primari", "primar", "program", "contact"),
     "Primăria Arad e în Palatul Administrativ, Bulevardul Revoluției 75 (clădirea cu turnul cu ceas). Programul și "
     "contactele sunt pe site-ul primariaarad.ro. Problemele din oraș le poți trimite direct din aplicație."),
]

APP_HELP = [
    (("check", "primarul", "tron", "cucer"),
     "Check-in: mergi la un loc, deschide-l pe hartă și apasă „Cucerește tronul” sau „Fă check-in”. Cine are cele mai multe "
     "check-in-uri la un loc devine Primarul lui. Primești XP, urci în nivel și câștigi insigne."),
    (("duel", "vot"),
     "La Dueluri îți apar două locuri și o alegi pe cea mai bună. Voturile tuturor fac clasamentul din Topul Aradului."),
    (("parol", "cont", "username", "sterg", "limb", "tema", "culoar", "setari"),
     "Contul, scorurile, limba și culorile aplicației le găsești în Setări (din Acasă → Mai mult → Setări)."),
    (("trecut", "1901", "vechi", "poze vechi", "ghid audio", "asculta"),
     "La „Descoperă Aradul” găsești poze vechi (Primăria în 1901, Palatul Cultural în 1931, Teatrul în 1874) și ghid audio "
     "în română pentru obiective."),
]

NOUNS = {"fastfood": "localuri de fast food", "restaurant": "restaurante", "cafenea": "cafenele", "cofetarie": "cofetării",
         "gelaterie": "gelaterii", "terasa": "terase", "bar": "baruri", "obiectiv": "obiective", "manastire": "mănăstiri", "biserica": "biserici", "supermarket": "supermarketuri",
         "benzinarie": "benzinării", "cazare": "hoteluri și pensiuni",
         "natura": "parcuri și locuri în natură", "mall": "centre comerciale", "eveniment": "evenimente"}

ARTICLE = {"restaurant": "Restaurantul", "cafenea": "Cafeneaua", "cofetarie": "Cofetăria", "gelaterie": "Gelateria",
           "bar": "Barul", "terasa": "Terasa", "fastfood": "Localul",
           "supermarket": "Magazinul", "benzinarie": "Benzinăria"}
OWN_TYPE = ("pizz", "rest", "cafe", "coffee", "cofet", "gelat", "bar", "teras", "bistro", "kfc", "mcdonald", "burger",
            "parcul", "manastir", "palatul", "cetatea", "muzeul", "teatrul", "catedral", "statui", "biseric", "hotel", "pensiun", "motel", "hostel", "lidl", "kaufland", "profi",
            "penny", "carrefour", "petrom", "omv", "mol", "rompetrol", "lukoil")


def _where(p: Place, d: dict) -> str:
    """„pe Strada Cloșca 12, la 580 m de tine” – fără „, Arad” la final."""
    addr = re.sub(r",?\s*Arad$", "", (p.address or "").strip())
    parts = []
    if addr and plain(addr) != "arad":
        parts.append(("pe " if re.match(r"(?i)(strada|str\.|bulevardul|calea|piata|piața|aleea|splaiul)", addr) else "la ") + addr)
    else:
        parts.append("în Arad")
    if "distance_m" in d:
        parts.append(f"la {_fmt_dist(d['distance_m'])} de tine ({_walk(d['distance_m'])})")
    return ", ".join(parts)


def _say(p: Place, d: dict, first: bool) -> str:
    art = ARTICLE.get(p.category) if not any(k in plain(p.name) for k in OWN_TYPE) else None
    if first:
        who = f"{art} {p.name}" if art else p.name
        return f"{who} e {_where(p, d)}."
    addr = re.sub(r",?\s*Arad$", "", (p.address or "").strip())
    on = (" pe " if re.match(r"(?i)(strada|bulevardul|calea|pia)", addr) else " la ") + addr if addr and plain(addr) != "arad" else ""
    dist_txt = f" ({_fmt_dist(d['distance_m'])})" if "distance_m" in d else ""
    return f"{p.name},{on}{dist_txt}" if on else f"{p.name}{dist_txt}"


GREETINGS = ("salut", "buna", "hello", "hei", "hey", "neata", "servus", "szia", "hi")
THANKS = ("mersi", "multumesc", "thanks", "multam", "mersii")


def answer(db: Session, text: str, near: tuple[float, float] | None) -> tuple[str, list[dict]]:
    t = plain(text)
    words = t.split()

    if any(w in t for w in ("112", "urgent", "pericol", "accident", "incendiu", "foc ", "ranit")):
        return ("Dacă e cineva în pericol chiar acum, sună imediat la 112. După ce ești în siguranță, poți semnala problema "
                "și din aplicație, ca să ajungă la Primărie."), []

    if words and all(w in GREETINGS for w in words[:2]):
        return ("Bună! Sunt Iasmi. Mă poți întreba unde să mănânci sau să bei o cafea, cum ajungi la un loc, cum plătești "
                "o taxă sau ce probleme s-au semnalat în oraș."), []
    if any(w in THANKS for w in words):
        return random.choice(["Cu drag! Mai am ceva cu ce să te ajut?", "Oricând! Spor în Arad."]), []

    # sesizări: „ce probleme s-au semnalat?” vs. „cum semnalez o groapă?”
    about_reports = _has(t, "problem", "sesiz", "semnal", "groap", "alert")
    asks_list = any(k in f" {t} " for k in (" ce probleme ", " ce sesizari ", " cate ", " azi ", " recent", " saptaman", " ultim"))
    if about_reports and asks_list:
        since = utcnow() - timedelta(days=7)
        rows = db.execute(select(Report.category, Report.status, func.count()).where(Report.created_at >= since)
                          .group_by(Report.category, Report.status)).all()
        if not rows:
            return "În ultima săptămână nu s-a semnalat nimic în aplicație. Orașul arată bine!", []
        from .routers.reports import REPORT_CATEGORIES, STATUSES
        total = sum(r[2] for r in rows)
        lines = [f"- {REPORT_CATEGORIES.get(c, c)}: {n} ({STATUSES.get(s, s).lower()})" for c, s, n in rows]
        return f"În ultimele 7 zile s-au semnalat {total} probleme:\n" + "\n".join(lines), []
    if about_reports or _has(t, "copac", "inunda", "iluminat", "stalp", "vandal", "furtun"):
        return ("Apasă „Semnalează o problemă” (în Acasă sau în meniul hărții), alege tipul – groapă, copac căzut, "
                "inundație, iluminat, vandalism și altele –, adaugă o poză și trimite. Ajunge la Primărie, iar statusul "
                "îl vezi în „Sesizările mele”. Primești +10 XP, plus 20 când e rezolvată."), []

    # servicii: farmacii, spitale, poliție, poștă, bancomate (OpenStreetMap)
    service = next((k for k, roots in (("farmacie", ("farmac", "medicament")), ("spital", ("spital", "urgent", "upu", "clinic")),
                                       ("politie", ("politi", "pompier")), ("posta", ("posta", "colet")),
                                       ("banca", ("bancomat", "atm", "banca", "numerar")), ("medic", ("dentist", "stomatolog", "doctor", "medic")))
                    if _has(t, *roots)), None)
    if service and not _has(t, "ajung"):
        from .routers.servicii import nearest
        where = near or (46.1745, 21.3185)
        rows = nearest(service, where[0], where[1], 3)
        if rows:
            first = rows[0]
            parts = [f"{first['name']}" + (f", pe {first['address']}" if first['address'] else "") +
                     (f", la {_fmt_dist(first['distance_m'])} de tine ({_walk(first['distance_m'])})" if near else "")]
            extra = "; ".join(f"{r['name']} ({_fmt_dist(r['distance_m'])})" for r in rows[1:]) if near else ""
            msg = f"Cel mai aproape: {parts[0]}."
            if first.get("hours"):
                msg += f" Program: {first['hours']}."
            if extra:
                msg += f" Mai sunt: {extra}."
            if service == "spital":
                msg = "Dacă e o urgență, sună la 112. " + msg
            return msg + " Toate le găsești în Acasă → Servicii locale.", []

    # calendarul gunoiului pe stradă (date RETIM)
    if _has(t, "gunoi", "deseu", "pubel", "reciclab", "ridica", "hartie", "plastic", "menajer"):
        from .routers.gunoi import answer_for
        ans = answer_for(text)
        if ans:
            return ans, []
        if _has(t, "cand", "ce zi", "vine", "trece", "ridica"):
            return ("Spune-mi strada ta, de exemplu „când vine gunoiul pe strada Ady Endre?”, sau caută-o în "
                    "Acasă → Calendar gunoi: îți arăt zilele pentru menajer, bio, hârtie și plastic."), []

    # „cum devin Primar / iau tronul” = jocul din aplicație, nu Primăria orașului
    if _has(t, "devin", "tron", "cucer", "check"):
        return APP_HELP[0][1], []

    # cunoștințe despre servicii publice
    for roots, reply in KNOWLEDGE:
        if _has(t, *roots) and not _has(t, "ajung", "traseu"):   # „cum ajung cu tramvaiul la X” = rută, nu bilete
            return reply, []

    # cum ajung la ...
    phrases = (" unde e ", " unde este ", " unde se afla ", " cum ajung ", " drumul ", " ce tramvai ", " ce autobuz ", " cu ce ")
    if _has(t, "ajung", "traseu", "navig", "tramvai", "autobuz") and _match_places(db, t, _intent_category(t)) or _has(t, "ajung", "traseu", "navig") or any(ph in f" {t} " for ph in phrases):
        places = _match_places(db, t, _intent_category(t))
        # întâi locul al cărui nume se potrivește cel mai bine, apoi cel mai apropiat
        words = _keywords(t)
        hits = lambda p: sum(1 for w in words if w in plain(p.name))
        places.sort(key=lambda p: (-hits(p), distance_m(near[0], near[1], p.lat, p.lng) if near else 0))
        if places:
            p = places[0]
            d = _place_dict(p, near)
            addr = p.address if p.address and p.address.strip().lower() != "arad" else ""
            first = f"{p.name} e pe {addr}." if addr else f"Am găsit {p.name}."
            if near:
                from .route_planner import plan
                try:
                    route = plan(near[0], near[1], p.lat, p.lng)["text"]
                except Exception:
                    route = f"Ești la {_fmt_dist(d['distance_m'])}, {_walk(d['distance_m'])}."
                return f"{first} {route} Pentru navigare pas cu pas, apasă pe el mai jos și „Du-mă acolo”.", [d]
            return (f"{first} Pornește locația și îți spun ce tramvai sau autobuz iei. Sau apasă pe el mai jos "
                    f"și „Du-mă acolo”: pornește navigarea în Google Maps."), [d]
        return "Spune-mi numele locului (de exemplu „Cetatea Aradului” sau „Palatul Cultural”) și îți arăt drumul.", []

    # ghidul aplicației
    for roots, reply in APP_HELP:
        if _has(t, *roots):
            return reply, []

    # recomandări de locuri
    category = _intent_category(t)
    places = _match_places(db, t, category)
    if places:
        kw = _keywords(t)
        hits = lambda p: sum(1 for w in kw if w in plain(p.name) or (len(w) >= 5 and w[:-1] in plain(p.name)))
        if near:
            places.sort(key=lambda p: (-hits(p), distance_m(near[0], near[1], p.lat, p.lng)))
        else:
            places.sort(key=lambda p: (-hits(p), -p.rating))
        top = [_place_dict(p, near) for p in places[:3]]
        sentences = [_say(places[0], top[0], first=True)]
        others = [_say(p, d, first=False) for p, d in zip(places[1:3], top[1:3])]
        if others:
            sentences.append(("Mai poți încerca " + " sau ".join(others) + ".") if len(others) == 1
                             else "Mai poți încerca " + ", ".join(others[:-1]) + " sau " + others[-1] + ".")
        if places[0].mayor:
            sentences.append(f"Primarul de acolo e @{places[0].mayor.name}; îi poți lua tronul cu un check-in.")
        return " ".join(sentences), top

    return ("Nu sunt sigură că am înțeles. Mă poți întreba, de exemplu: „Unde beau o cafea bună?”, „Cum ajung la Cetate?”, "
            "„Cum plătesc impozitul?” sau „Ce probleme s-au semnalat azi?”."), []
