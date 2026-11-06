# 🏆 Topul Aradului — Hack4Arad 2026

Harta Aradului votată de arădeni: check-in verificat prin GPS, dueluri între locuri (Elo), clasamente,
„Primarul” fiecărui loc, insigne și **heatmap live** cu agitația din oraș.

```
android/   aplicația (Kotlin + Jetpack Compose)      → deschide în Android Studio
backend/   API-ul (Python + FastAPI + SQLAlchemy)     → deschide în VS Code
```

---

## 1. Backend (Python)

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate          # Windows  (macOS/Linux: source .venv/bin/activate)
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- Documentație interactivă (testezi fiecare endpoint din browser): **http://localhost:8000/docs**
- La prima pornire se creează `topul.db` (SQLite) și se încarcă locurile din `app/seed_places.json`.
- Teste: `pytest -q`

### Date demo (heatmap „viu” la prezentare)
```bash
python -m app.seed --reset --demo
```
Creează 6 utilizatori demo (parola `demo1234`) cu check-in-uri în ultima oră și voturi.

### Setări (`backend/.env`, vezi `.env.example`)
| Variabilă | Ce face |
|---|---|
| `CHECKIN_RADIUS_M` | distanța maximă față de loc pentru check-in (implicit 100 m) |
| `CHECKIN_COOLDOWN_MIN` | cât aștepți între două check-in-uri la același loc |
| `SKIP_DISTANCE_CHECK` | `true` **doar la demo**, ca juriul să poată face check-in din sală |
| `DATABASE_URL` | Postgres în producție (instalează și `psycopg[binary]`) |
| `SECRET_KEY` | cheie JWT — schimb-o înainte de deploy |

### API
| Metodă | Endpoint | Ce face |
|---|---|---|
| POST | `/auth/register`, `/auth/login` | cont nou / login → token |
| GET | `/places?category=&lat=&lng=&radius_km=` | locuri (sortate după distanță dacă dai lat/lng) |
| GET | `/places/categories` | categoriile existente |
| GET | `/places/{id}` | detalii + poze recente + locul în clasament |
| POST | `/checkins` (multipart) | check-in: `place_id`, `lat`, `lng`, `is_mock`, `photo?` |
| GET | `/checkins/recent?minutes=60` | puncte pentru heatmap |
| GET | `/duels/next?category=` | două locuri de comparat |
| POST | `/duels/vote` | `{winner_id, loser_id}` → recalculează Elo |
| GET | `/leaderboard/places?category=`, `/leaderboard/users` | clasamente |
| GET | `/users/me` | profil, XP, insigne, unde ești Primar |
| WS | `/live` | evenimente live: `checkin`, `mayor` |

### Reguli de joc
- **Check-in**: doar la ≤100 m de loc, GPS simulat refuzat, o dată la 30 min per loc.
- **XP**: 15 loc nou / 10 loc revizitat, +5 cu poză, +2 per vot. Nivel nou la fiecare 100 XP.
- **Primar**: cele mai multe check-in-uri în ultimele 30 de zile (la egalitate rămâne cel actual).
- **Dueluri**: Elo (K=32), ca la șah — un loc nou poate urca repede dacă bate favoriții.
- **Insigne**: Primul pas, Explorator, Cafegiu, Gurmand, Turist acasă, Susținător local, Fotograf, Primar, Judecător.

---

## 2. Aplicația Android

1. Instalează **Android Studio** (include JDK-ul) și creează un emulator (Device Manager → Pixel, API 34/35, imagine **cu Google Play**).
2. **File → Open →** folderul `android/`. Android Studio descarcă singur Gradle și dependențele (prima oară durează).
3. Copiază `android/local.properties.example` în `android/local.properties` și completează:
   - `MAPS_API_KEY` — din [Google Cloud Console](https://console.cloud.google.com/) → activează **Maps SDK for Android** → Credentials → API key.
   - `BASE_URL` — `http://10.0.2.2:8000/` pentru emulator; pe telefon real `http://IP_LAPTOP:8000/` (același Wi-Fi).
4. Pornește backend-ul, apoi ▶ **Run** în Android Studio.

Fără cheie de Maps aplicația merge, dar harta apare gri.

**În emulator**, locația o setezi din `⋮ Extended controls → Location` (pune coordonatele unui loc ca să poți face check-in).

### Structura
```
data/       Api (Retrofit), modele, sesiune (token), LiveClient (WebSocket)
ui/map      harta + markere pe categorii + heatmap live
ui/place    detalii loc, check-in cu GPS și poză
ui/duels    „Care e mai bun?” — vot Elo
ui/leaderboard  top locuri / top arădeni
ui/profile  nivel, XP, insigne, unde ești Primar
util/       locație, compresie poze, mesaje de eroare
```

---

### Emulator (deja instalat pe acest calculator)
```bash
%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Topul_Pixel
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe install -r android\app\build\outputs\apk\debug\app-debug.apk
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe emu geo fix 21.313248 46.170315   # GPS lângă Pizza Drive (lng lat)
```
Cont de test creat local (doar în `topul.db`): `tester@topul.test` / `test1234`.
Fără cheie Google Maps, tab-ul „Hartă” arată locurile ca listă sortată după distanță.

### Build din linia de comandă (fără Android Studio)
```bash
cd android
gradlew.bat assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
```
Pe acest calculator sunt deja instalate JDK 17 (`%LOCALAPPDATA%\Programs\jdk-17...`, setează `JAVA_HOME`)
și Android SDK (`%LOCALAPPDATA%\Android\Sdk`) — Android Studio le va găsi și refolosi.

---

## 3. Date
`backend/app/seed_places.json` conține **74 de locuri reale din Arad și județ** (pizzerii, burgeri, restaurante,
cafenele, cofetării, terase, baruri, obiective, parcuri), cu coordonate din **OpenStreetMap**
(© OpenStreetMap contributors, licență ODbL — menționați asta în aplicație/prezentare).
Lista se regenerează cu `backend/tools/build_seed_from_osm.py`.

## 4. Înainte de hackathon — TODO
- [ ] **Cheie Google Maps** în `android/local.properties` (fără ea harta e gri), apoi rebuild.
- [ ] Testat pe un telefon real (activează *Developer options → USB debugging*, apoi Run din Android Studio).
- [ ] Mai adăugați producători locali (vin, miere, brânzeturi) — acum e doar Podgoria Miniș-Măderat.
- [ ] Verificați categoriile „terasă” (Belvedere, Ratio Beach, La Ponton, Falezza au fost alese după locație).
- [ ] Deploy backend (Railway/Render): start command `uvicorn app.main:app --host 0.0.0.0 --port $PORT`.

## 5. Demo pentru juriu
1. `python -m app.seed --reset --demo` → harta are deja agitație.
2. `SKIP_DISTANCE_CHECK=true` pe server, ca juriul să poată face check-in din sală.
3. Dați juriului APK-ul (QR) → își fac cont → check-in la „Hack4Arad @ Complex M” → apar **live** pe heatmap-ul de pe proiector.
4. Arătați un duel → clasamentul se schimbă imediat.
