# Ce ai de făcut tu: publicare online, login cu Google și email

Codul e gata. Pașii de mai jos cer conturile tale, de aceea îi faci tu.
După fiecare pas, trimite-mi ce scrie la **„Îmi trimiți”**, iar eu actualizez și refac aplicația.

Durată totală: cam 25–30 de minute.

---

> ✅ **Pașii 1 și 2 sunt gata.** Codul e pe https://github.com/nimeniaici/topul-aradului, iar serverul rulează
> pe **https://api-production-a749.up.railway.app** (Railway, proiect `topul-aradului`: PostgreSQL + volum `/data`).
> Actualizare server: din folderul `backend`, rulează `railway up --service api --detach`.
>
> ✅ **Pasul 3 e gata.** Clienții OAuth Web și Android sunt creați, aplicația e publicată, `GOOGLE_CLIENT_IDS`
> e setat pe Railway și `GOOGLE_WEB_CLIENT_ID` e în `android/local.properties`.
> Pagini publice: `/despre`, `/confidentialitate`, `/termeni`.
>
> 📲 **Descărcare aplicație:** https://api-production-a749.up.railway.app/app (cod QR: `prezentare/qr-descarca-aplicatia.png`).
> Urcarea unui APK nou (cheia e în `backend/.env.admin`, exclus din git):
> `curl -X POST https://api-production-a749.up.railway.app/admin/apk -H "X-Admin-Token: CHEIA" -F "file=@android/app/build/outputs/apk/debug/app-debug.apk"`
> Tur 3D: `curl -X POST .../admin/places/ID/splat -H "X-Admin-Token: CHEIA" -F "file=@scena.splat"`

## Pasul 1: urcă proiectul pe GitHub (~5 min)

Railway ia codul de pe GitHub, așa că primul pas e să-l pui acolo.

1. Fă-ți cont pe **https://github.com** dacă nu ai.
2. Apasă **+ → New repository**, cu numele `topul-aradului`, bifat **Private**, **fără** README. Apoi **Create repository**.
3. În terminal, în folderul `hackathon`, rulează (înlocuiește `UTILIZATORUL_TAU`):

```bash
git remote add origin https://github.com/UTILIZATORUL_TAU/topul-aradului.git
git push -u origin main
```

Prima dată se deschide o fereastră de login GitHub. Cheia Google Maps, parolele și baza de date locală **nu** se urcă, pentru că sunt excluse în `.gitignore`.

---

## Pasul 2: serverul online pe Railway (~10 min)

1. Intră pe **https://railway.com** și apasă **Login → Login with GitHub**. Verifică planul curent: Railway oferă un trial, iar după aceea e cu plată după consum.
2. **New Project → Deploy from GitHub repo** și alege `topul-aradului`.
3. Apasă pe serviciul creat, apoi **Settings → Root Directory** și scrie `backend`.
4. În proiect: **+ Create → Database → PostgreSQL**.
5. La serviciul backend, în **Variables**, adaugă:

| Nume | Valoare |
|---|---|
| `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` |
| `SECRET_KEY` | un șir lung aleator, de exemplu de pe https://www.random.org/strings (32+ caractere) |
| `UPLOAD_DIR` | `/data/uploads` |

6. Click dreapta pe serviciul backend, apoi **Attach Volume**, cu **Mount path** `/data`. Aici se păstrează pozele de la check-in.
7. **Settings → Networking → Generate Domain**. Primești o adresă de tipul `https://topul-aradului-production.up.railway.app`.
8. Deschide în browser adresa + `/docs`. Dacă vezi pagina API-ului, serverul merge.

**Îmi trimiți:** adresa de la pasul 7.

---

## Pasul 3: login cu Google (~10 min)

Folosești proiectul Google Cloud pe care îl ai deja (cel cu cheia Maps).

1. **https://console.cloud.google.com/auth/overview?project=project-23332d49-e8be-4e4a-887**
   - Dacă îți cere, apasă **Get started**. App name: `Topul Aradului`, support email: emailul tău, Audience: **External**, contact: emailul tău. Apoi **Create**.
2. Mergi la **Audience** și apasă **Publish app**. Altfel doar „test users” se pot loga, deci nu și juriul.
3. Mergi la **Clients → + Create client**:
   - **Application type: Web application**, name `Topul server`, apoi **Create**. **Copiază Client ID-ul** (se termină în `.apps.googleusercontent.com`).
4. Iar **+ Create client**:
   - **Application type: Android**
   - Package name: `ro.hack4arad.topul`
   - SHA-1: `0B:F9:FD:E5:32:52:01:AC:5B:77:32:1C:70:9F:81:79:D7:3B:C4:E5`
   - **Create**. De la acesta nu trebuie copiat nimic.
5. Pe Railway, în **Variables**, adaugă `GOOGLE_CLIENT_IDS` = Client ID-ul de la punctul 3.

**Îmi trimiți:** Client ID-ul de tip Web, de la punctul 3. Nu e secret, apare oricum în aplicație.

---

## Pasul 4: email pentru „Ai uitat parola?” (~5 min)

Fără acest pas, codul de resetare apare doar în log-urile Railway.

1. Pe contul Google pe care vrei să-l folosești ca expeditor, activează **verificarea în 2 pași**: https://myaccount.google.com/signinoptions/two-step-verification
2. Creează o **parolă de aplicație**: https://myaccount.google.com/apppasswords. Nume `Topul Aradului`; primești 16 caractere.
3. Pe Railway, în **Variables**, adaugă:

| Nume | Valoare |
|---|---|
| `SMTP_HOST` | `smtp.gmail.com` |
| `SMTP_PORT` | `587` |
| `SMTP_USER` | adresa ta de Gmail |
| `SMTP_PASSWORD` | parola de aplicație (16 caractere, fără spații) |
| `SMTP_FROM` | `Topul Aradului <adresa.ta@gmail.com>` |

**Nu-mi trimite parola de aplicație.** O pui direct pe Railway.

---

## Pasul 5: scrie-mi „gata”

Îmi trimiți **adresa Railway** și **Client ID-ul Web**. Eu:
- pun adresa serverului și Client ID-ul în aplicație;
- refac APK-ul;
- încarc pe server locurile reale și, dacă vrei, datele demo;
- testez cap-coadă: cont nou, Google, check-in și resetare parolă.

---

## Dacă ceva nu merge
- **Railway „Deploy failed”**: deschide **Deployments → View logs** și trimite-mi ultimele rânduri.
- **„Setează variabila SECRET_KEY”** în log: lipsește `SECRET_KEY` din Variables.
- **Google: „Login-ul Google a eșuat”**: verifică dacă la clientul Android SHA-1 și package name sunt exact ca mai sus și dacă aplicația e **publicată** (pasul 3.2).
