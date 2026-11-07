package ro.hack4arad.topul.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ro.hack4arad.topul.BuildConfig
import ro.hack4arad.topul.data.FeedbackIn
import ro.hack4arad.topul.data.MyStats
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.PasswordIn
import ro.hack4arad.topul.data.Profile
import ro.hack4arad.topul.data.UpdateMeIn
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.components.UsernameField
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.Lang
import ro.hack4arad.topul.ui.theme.LangState
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.PALETTES
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.ThemeState
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.userMessage

/** Setări: contul meu (date personale, scoruri), aplicația (limbă, temă) și ajutor (raportează o problemă). */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var stats by remember { mutableStateOf<MyStats?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) } // "password" | "feedback"
    var username by remember { mutableStateOf("") }
    var usernameOk by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching { Network.api.me() }.onSuccess { profile = it; username = it.name }
        runCatching { Network.api.myStats() }.onSuccess { stats = it }
    }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(3500); toast = null } }
    val open = { path: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Network.baseUrl + path))) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
            Text(tr("Setări", "Settings", "Beállítások"), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        toast?.let {
            Text(it, color = Color.White, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink).padding(12.dp))
            Spacer(Modifier.height(8.dp))
        }

        // ---------------- CONTUL MEU ----------------
        Section(tr("Contul meu", "My account", "Fiókom"))
        Card {
            val p = profile
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p?.name ?: "?", 56.dp, emoji = p?.avatarEmoji, colorKey = p?.avatarColor, photoUrl = p?.avatarUrl)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("@${p?.name ?: "…"}", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(p?.email ?: "", color = Lilac, fontSize = 13.sp)
                    Text(
                        if (p?.googleLinked == true) tr("Conectat cu Google", "Signed in with Google", "Google-fiókkal")
                        else tr("Cont cu email și parolă", "Email and password account", "E-mail és jelszó"),
                        color = TextMuted, fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            UsernameField(username, { username = it }, { usernameOk = it }, current = p?.name)
            Button(
                onClick = {
                    scope.launch {
                        runCatching { Network.api.updateMe(UpdateMeIn(name = username)) }
                            .onSuccess { profile = it; Network.session.updateName(it.name); toast = tr("Salvat", "Saved", "Mentve") }
                            .onFailure { toast = it.userMessage() }
                    }
                },
                enabled = usernameOk && username != p?.name,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("Salvează username-ul", "Save username", "Felhasználónév mentése")) }
            Row2(Icons.Rounded.Key, if (p?.hasPassword == false) tr("Setează o parolă", "Set a password", "Jelszó beállítása")
                else tr("Schimbă parola", "Change password", "Jelszó módosítása")) { dialog = "password" }
            Row2(Icons.Rounded.Person, tr("Poza și avatarul", "Photo and avatar", "Fotó és avatar"),
                tr("Din Profil → Editează", "In Profile → Edit", "Profil → Szerkesztés")) { toast = tr("Deschide Profil → Editează", "Open Profile → Edit", "Nyisd meg: Profil → Szerkesztés") }
        }

        // ---------------- SCORURI ----------------
        Section(tr("Scorurile mele", "My scores", "Pontjaim"))
        Card {
            val p = profile
            val s = stats
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Score("${p?.xp ?: 0}", "XP", Modifier.weight(1f))
                Score("${p?.level ?: 1}", tr("nivel", "level", "szint"), Modifier.weight(1f))
                Score(s?.let { "#${it.rank}" } ?: "–", tr("în Arad", "in Arad", "Aradon"), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Score("${p?.checkins ?: 0}", "check-in", Modifier.weight(1f))
                Score("${p?.mayorOf?.size ?: 0}", tr("tronuri", "thrones", "trón"), Modifier.weight(1f))
                Score("${p?.badges?.size ?: 0}", tr("insigne", "badges", "jelvény"), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Score("${s?.reports ?: 0}", tr("sesizări", "reports", "bejelentés"), Modifier.weight(1f))
                Score("${s?.reportsResolved ?: 0}", tr("rezolvate", "resolved", "megoldva"), Modifier.weight(1f))
                Score("${p?.placesVisited ?: 0}", tr("locuri", "places", "hely"), Modifier.weight(1f))
            }
            s?.let { Text(tr("Membru din ", "Member since ", "Tag óta: ") + it.memberSince + " · ${it.totalUsers} " + tr("arădeni în joc", "players", "játékos"),
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
        }

        // ---------------- APLICAȚIA ----------------
        Section(tr("Aplicația", "App", "Alkalmazás"))
        Card {
            Text(tr("Limba", "Language", "Nyelv"), color = Ink, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lang.entries.forEach { l ->
                    val on = LangState.current == l
                    Text(
                        l.label, color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) Ink else Track)
                            .clickable { LangState.select(context, l) }.padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Text(tr("Interfața (paleta de culori)", "Interface (color palette)", "Felület (színpaletta)"), color = Ink,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            PALETTES.forEach { pal ->
                val on = ThemeState.palette.id == pal.id
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) pal.deep else Track, RoundedCornerShape(16.dp))
                        .clickable { ThemeState.select(context, pal.id) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // previzualizare: fundalul circular + cele 5 nuanțe
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.radialGradient(listOf(pal.center, pal.center, pal.edge))), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(pal.deep))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${pal.id} · ${pal.name}", color = Ink, fontWeight = FontWeight.Bold)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf(pal.deep, pal.a1, pal.a2, pal.a3, pal.a4).forEach {
                                Box(Modifier.width(26.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(it))
                            }
                        }
                    }
                    if (on) Icon(Icons.Rounded.Check, null, tint = pal.deep)
                }
            }
        }

        // ---------------- AJUTOR ----------------
        Section(tr("Ajutor", "Help", "Súgó"))
        Card {
            Row2(Icons.Rounded.BugReport, tr("Raportează o problemă a aplicației", "Report an app problem", "Hiba bejelentése"),
                tr("Ceva nu merge? Spune-ne.", "Something broken? Tell us.", "Valami nem működik?"), tint = Pink) { dialog = "feedback" }
            Row2(Icons.Rounded.Info, tr("Despre aplicație", "About", "Névjegy"), "CityConnect Arad · v${BuildConfig.VERSION_NAME}") { open("despre") }
            Row2(Icons.Rounded.Lock, tr("Confidențialitate", "Privacy", "Adatvédelem")) { open("confidentialitate") }
            Row2(Icons.Rounded.Description, tr("Termeni și condiții", "Terms", "Feltételek")) { open("termeni") }
        }

        Section(tr("Ieșire", "Sign out", "Kilépés"))
        Card {
            Row2(Icons.AutoMirrored.Rounded.Logout, tr("Ieși din cont", "Sign out", "Kijelentkezés")) { Network.session.logout() }
            Row2(Icons.Rounded.DeleteForever, tr("Șterge contul definitiv", "Delete account", "Fiók törlése"),
                tr("Toate datele tale dispar", "All your data is removed", "Minden adatod törlődik"), tint = Pink) { dialog = "delete" }
        }
    }

    when (dialog) {
        "password" -> PasswordDialog(hasPassword = profile?.hasPassword != false, onDismiss = { dialog = null }) { old, new ->
            scope.launch {
                runCatching { Network.api.changePassword(PasswordIn(old, new)) }
                    .onSuccess { dialog = null; toast = it.detail; profile = runCatching { Network.api.me() }.getOrNull() ?: profile }
                    .onFailure { toast = it.userMessage() }
            }
        }
        "delete" -> {
            var typed by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(tr("Ștergi contul?", "Delete account?", "Törlöd a fiókot?")) },
                text = {
                    Column {
                        Text(tr("Se șterg definitiv: check-in-urile, pozele, sesizările, insignele și scorul. Nu se poate anula.",
                            "Your check-ins, photos, reports, badges and score are deleted for good.",
                            "Minden adatod véglegesen törlődik."), color = Lilac)
                        OutlinedTextField(typed, { typed = it }, label = { Text(tr("Scrie STERGE", "Type STERGE", "Írd be: STERGE")) }, singleLine = true)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            runCatching { Network.api.deleteMe() }
                                .onSuccess { if (it.isSuccessful) Network.session.logout() else toast = "Eroare ${it.code()}" }
                                .onFailure { toast = it.userMessage() }
                        }
                    }, enabled = typed.trim().uppercase() == "STERGE") { Text(tr("Șterge", "Delete", "Törlés")) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
            )
        }
        "feedback" -> FeedbackDialog(onDismiss = { dialog = null }) { kind, message ->
            scope.launch {
                runCatching {
                    Network.api.sendFeedback(FeedbackIn(kind, message, BuildConfig.VERSION_NAME, "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"))
                }.onSuccess { dialog = null; toast = it.detail }.onFailure { toast = it.userMessage() }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Track, RoundedCornerShape(20.dp)).padding(14.dp)) {
        content()
    }
}

@Composable
private fun Score(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Track.copy(alpha = 0.6f)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(label, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Row2(icon: ImageVector, title: String, subtitle: String? = null, tint: Color = Ink, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (tint == Ink) Ink else tint, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = TextMuted, fontSize = 12.sp) }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted)
    }
}

@Composable
private fun PasswordDialog(hasPassword: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Parola", "Password", "Jelszó")) },
        text = {
            Column {
                if (hasPassword) OutlinedTextField(old, { old = it }, label = { Text(tr("Parola actuală", "Current password", "Jelenlegi jelszó")) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(new, { new = it }, label = { Text(tr("Parola nouă (min. 6)", "New password (min. 6)", "Új jelszó (min. 6)")) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            }
        },
        confirmButton = { Button(onClick = { onSave(old, new) }, enabled = new.length >= 6 && (!hasPassword || old.isNotEmpty())) { Text(tr("Salvează", "Save", "Mentés")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
    )
}

@Composable
private fun FeedbackDialog(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var kind by remember { mutableStateOf("bug") }
    var message by remember { mutableStateOf("") }
    val kinds = listOf(
        "bug" to tr("Ceva nu merge", "Something's broken", "Hiba"),
        "idee" to tr("Am o idee", "I have an idea", "Ötletem van"),
        "cont" to tr("Contul meu", "My account", "Fiókom"),
        "altceva" to tr("Altceva", "Other", "Egyéb"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Raportează o problemă", "Report a problem", "Hiba bejelentése")) },
        text = {
            Column {
                kinds.chunked(2).forEach { row ->
                    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (code, label) ->
                            val on = kind == code
                            Text(label, color = if (on) Color.White else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) Ink else Track)
                                    .clickable { kind = code }.padding(vertical = 10.dp))
                        }
                    }
                }
                OutlinedTextField(message, { if (it.length <= 2000) message = it },
                    label = { Text(tr("Ce s-a întâmplat?", "What happened?", "Mi történt?")) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                Text(tr("Trimitem și versiunea aplicației și modelul telefonului, ca să putem repara.",
                    "We also send the app version and phone model so we can fix it.",
                    "Az alkalmazás verzióját és a telefon típusát is elküldjük."), color = TextMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { Button(onClick = { onSend(kind, message.trim()) }, enabled = message.trim().length >= 3) { Text(tr("Trimite", "Send", "Küldés")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
    )
}
