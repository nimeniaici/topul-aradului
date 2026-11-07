# CityConnect Arad — Hack4Arad 2026

**Orașul tău, conectat. Joacă, semnalează, descoperă.**

O singură aplicație Android în care un arădean găsește tot ce are nevoie despre oraș: transportul CTP în timp real,
sesizări către Primărie, calendarul de gunoi, turism, servicii locale, comunitate și asistentul Iasmi. Partea de joc,
**Topul Aradului**, îi motivează să iasă în oraș: check-in-uri, tronuri, dueluri și clasamente.

- **Descarcă aplicația (Android):** https://api-production-a749.up.railway.app/descarca, sau scanează `qr-descarca.png`
- **Panoul Primăriei:** https://api-production-a749.up.railway.app/primarie (cere cheia Primăriei)
- **API și documentație:** https://api-production-a749.up.railway.app/docs
- Versiunea curentă: **v1.7**

```
android/   aplicația (Kotlin + Jetpack Compose)
backend/   serverul (Python + FastAPI + SQLAlchemy; SQLite local, PostgreSQL pe Railway)
```

Publicare online, login cu Google și email: vezi [GHID-PUBLICARE.md](GHID-PUBLICARE.md).

---

## Funcționalități

**Orașul tău**
- **Sesizări către Primărie**, cu poză și locul exact (12 tipuri). Ajung „confirmate” când **3 oameni** văd aceeași problemă
  în același loc; o poți confirma direct de pe hartă. Statusul și răspunsul Primăriei apar în „Sesizările mele”.
- **Alerte urgente**, separate de sesizări: inundații, gaze, explozii, cutremur, clădiri prăbușite, cabluri căzute, surpări.
  Au prioritate maximă, nu așteaptă confirmări și au buton direct spre 112.
- **Transport CTP live**: 29 de linii (8 de tramvai, 21 de autobuz), 299 de stații, vehiculele pe hartă în timp real,
  plecările din stație cu întârzieri și orarul complet al zilei. Mai sunt aici semnalări de aglomerație și bilete digitale (demo).
- **Calendar gunoi**: cauți strada (1.064 de străzi) și vezi zilele pentru menajer, bio, hârtie, plastic și campanii.
- **Servicii locale**: farmacii, spitale, cabinete, școli și grădinițe, poliție, poștă, bancomate, instituții, cu cele mai apropiate primele.

**Iasmi, asistentul orașului** (gratuit, fără AI plătit)
- Recomandă localuri („unde mănânc o pizza?”) și răspunde cu adresa și distanța.
- **Rute**: „cum ajung la Palatul Cultural?” → ce tramvai sau autobuz iei, de unde, câte stații, când vine următorul.
  Dacă nu există linie, te trimite pe jos sau îți recomandă Bolt, Uber sau taxi.
- Răspunde și despre gunoi, farmacii, spitale, taxe, acte și cum folosești aplicația.

**Descoperă**
- **Ghid audio** în română, cu povești documentate (date, arhitecți, evenimente). Poți alege vocea, viteza și tonul.
- **Trecut vs prezent**: poze vechi ale Aradului peste cele de azi.
- **Turism în județ**: 325 de obiective, locuri de agrement și cazări, plus 30 de trasee montane.
- **Harta**: 298 de locuri (restaurante, cafenele, obiective, biserici, mănăstiri, supermarketuri, benzinării, hoteluri și pensiuni).
  Are straturi pentru prieteni, tramvaie și autobuze live, evenimente, școli, turism și sesizări, plus buton de centrare pe tine.

**Comunitate**
- **Comunitatea Aradului**: chat general cu anunțuri și evenimente; grupuri cu cod de invitație (vecini, bloc, școală); chat privat.
- **Prieteni**: cereri, blocare, poziția pe hartă (doar dacă o pornești tu), tronurile lor; profil public din clasament.
- **Sondaje**: le pot face arădenii și Primăria (sondaje oficiale din panou). Rezultatele le vezi după ce votezi.

**Joacă (Topul Aradului)**
- **Check-in** verificat prin GPS (≤100 m, GPS simulat refuzat), cu poză făcută doar cu camera.
- **Primarul** unui loc este cel cu cele mai multe check-in-uri în ultimele 30 de zile.
- **Dueluri** între locuri, cu scor Elo (ca la șah), clasamente pentru locuri și arădeni, XP, niveluri și insigne.
- **Recompense (demo)**: 50 de check-in-uri în stații CTP aduc o săptămână de transport gratuit;
  10 vizite cu poză la același local aduc 10% reducere acolo.

**Aspect**: 5 palete de culori, fiecare cu logo-ul ei (iconița de pe telefon se schimbă odată cu paleta), plus **mod întunecat**
(negru, gri, alb) și limba română, engleză sau maghiară.

---

## Date folosite

| Sursă | Ce folosim |
|---|---|
| CTP Arad / Telelink, prin [arad-transport-open-data](https://baditaflorin.github.io/arad-transport-open-data/) | linii, stații, trasee, vehicule și sosiri live |
| RETIM, prin [calendar-ridicare-gunoi-arad](https://baditaflorin.github.io/calendar-ridicare-gunoi-arad/) | calendarul de ridicare a deșeurilor (2026) |
| CNIPT Arad (visitarad.ro), prin [hack4arad-turism-arad](https://baditaflorin.github.io/hack4arad-turism-arad/) | locuri turistice, cazare, trasee |
| OpenStreetMap (© contribuitorii OSM, ODbL) | locurile din joc, supermarketuri, benzinării, servicii, școli |
| Wikipedia (CC BY-SA) | poveștile ghidului audio |
| Wikimedia Commons | pozele vechi și silueta Primăriei din logo |
| Google Places | verificarea localurilor închise definitiv |

Datele se regenerează cu `backend/tools/build_opendata.py` (transport, gunoi, turism) și `backend/tools/build_servicii.py` (servicii).

---

## 1. Backend (Python)

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate          # Windows  (macOS/Linux: source .venv/bin/activate)
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- Documentație interactivă: **http://localhost:8000/docs**
- La prima pornire se creează baza de date și se încarcă locurile din `app/seed_places.json`.
- Sincronizarea locurilor cu baza de date existentă: `python -m app.maintenance sync --dry-run`, apoi fără `--dry-run`.
- Teste: `pytest -q` (66 de teste)

### Setări (`backend/.env`, vezi `.env.example`)
| Variabilă | Ce face |
|---|---|
| `DATABASE_URL` | PostgreSQL în producție |
| `SECRET_KEY` | cheia pentru token-uri (schimb-o înainte de deploy) |
| `PRIMARIE_TOKEN` | cheia panoului Primăriei |
| `ADMIN_TOKEN` | urcarea APK-ului nou (`/admin/apk`) |
| `SKIP_DISTANCE_CHECK` | `true` **doar la demo**, ca juriul să poată face check-in din sală |
| `CTP_LIVE` | `0` oprește datele live CTP (folosit în teste) |
| `ANTHROPIC_API_KEY` | opțional; fără ea Iasmi merge local, gratuit |

### Principalele endpoint-uri
| Zonă | Endpoint-uri |
|---|---|
| Cont | `/auth/*`, `/me`, `/users/{id}/public` |
| Locuri și joc | `/places`, `/checkins`, `/duels`, `/leaderboard/*`, `/me/rewards` |
| Sesizări | `/reports`, `/reports/{id}/confirm`, `/admin/reports` (Primăria) |
| Transport | `/transit/lines`, `/transit/vehicles`, `/transit/stops/{id}/next`, `/transit/stops/{id}/timetable`, `/transit/checkin` |
| Oraș | `/gunoi/search`, `/gunoi/{id}`, `/turism`, `/servicii` |
| Comunitate | `/community/*`, `/friends/*`, `/polls`, `/admin/polls` |
| Iasmi | `/iasmi` |
| Live | WebSocket `/live` (check-in-uri, sesizări, alerte, sondaje) |

---

## 2. Aplicația Android

1. Instalează **Android Studio** și creează un emulator (Pixel, API 34/35, imagine **cu Google Play**).
2. **File → Open →** folderul `android/`.
3. Copiază `android/local.properties.example` în `android/local.properties` și completează `MAPS_API_KEY`
   (Google Cloud Console → Maps SDK for Android).
4. Rulează aplicația cu ▶ **Run**.

Build din linia de comandă:
```bash
cd android
gradlew.bat assembleDebug -PbaseUrl=http://10.0.2.2:8000/   # pentru emulator, cu serverul local
gradlew.bat assembleRelease                                  # versiunea publică (serverul de pe Railway)
```

### Structura
```
data/        API (Retrofit), modele, sesiune, LiveClient (WebSocket)
ui/home      Acasă, Descoperă
ui/map       harta, straturi, sesizări și alerte
ui/place     detalii loc, check-in, ghid audio, trecut vs prezent
ui/transit   transport CTP live, orar, bilete demo
ui/city      calendar gunoi, turism, servicii locale
ui/iasmi     asistentul
ui/social    comunitate, chat, prieteni, profil public, sondaje
ui/profile   profil, insigne, recompense
ui/settings  cont, limbă, palete, mod întunecat
```

---

## 3. Demo pentru juriu
1. Pe server: `SKIP_DISTANCE_CHECK=true`, ca juriul să poată face check-in din sală.
2. Juriul scanează codul QR, își face cont și dă check-in la „Hack4Arad @ Complex M”, apoi apare live pe hartă.
3. Arată tramvaiele live, o întrebare pentru Iasmi („cum ajung la Palatul Cultural?”) și o sesizare care apare în panoul Primăriei.

Prezentarea (5 minute) e în artefactul „CityConnect Arad – pitch Hack4Arad”.
