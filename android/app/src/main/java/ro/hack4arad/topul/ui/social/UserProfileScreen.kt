package ro.hack4arad.topul.ui.social

import ro.hack4arad.topul.util.runCatching
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.PublicProfile
import ro.hack4arad.topul.data.UsernameIn
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.components.categoryIcon
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.userMessage

/** Profilul public al unui arădean (din clasament): nivel, loc în top, insigne, tronurile cucerite. */
@Composable
fun UserProfileScreen(userId: Int, onBack: () -> Unit, onPlace: (Int) -> Unit, onChat: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf<PublicProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        runCatching { Network.api.publicProfile(userId) }.onSuccess { p = it }.onFailure { error = it.userMessage() }
    }
    LaunchedEffect(userId) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
            Text(tr("Profil", "Profile", "Profil"), color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        error?.let { Text(it, color = Pink, modifier = Modifier.padding(16.dp)) }
        val u = p ?: return@Column
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(u.name, 96.dp, emoji = u.avatarEmoji, colorKey = u.avatarColor, photoUrl = u.avatarUrl)
            Spacer(Modifier.height(10.dp))
            Text("@${u.name}", color = Ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text(tr("Nivel ", "Level ", "Szint ") + "${u.level} · ${u.xp} XP · #${u.rank} " + tr("în Arad", "in Arad", "Aradon"),
                color = TextMuted, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            when (u.friendship) {
                "none" -> Button(onClick = {
                    scope.launch {
                        runCatching { Network.api.friendRequest(UsernameIn(u.name)) }
                            .onSuccess { toast = it.detail; load() }.onFailure { toast = it.userMessage() }
                    }
                }) { Icon(Icons.Rounded.PersonAdd, null); Text("  " + tr("Adaugă prieten", "Add friend", "Barátnak jelöl")) }
                "friends" -> Button(onClick = {
                    scope.launch { runCatching { Network.api.directRoom(u.id) }.onSuccess { onChat(it.id) }.onFailure { toast = it.userMessage() } }
                }) { Text(tr("Prieteni · scrie-i", "Friends · message", "Barátok · üzenet")) }
                "outgoing" -> Text(tr("Cerere de prietenie trimisă", "Friend request sent", "Kérés elküldve"), color = TextMuted)
                "incoming" -> Text(tr("Ți-a trimis o cerere (Acasă → Prieteni)", "Sent you a request (Home → Friends)", "Kérést küldött"), color = Gold, fontWeight = FontWeight.Bold)
                "me" -> Text(tr("Acesta ești tu", "This is you", "Ez te vagy"), color = TextMuted)
            }
            toast?.let { Text(it, color = Ink, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
        }
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(u.thrones.toString(), tr("tronuri", "thrones", "trón"), Modifier.weight(1f))
            Stat(u.checkins.toString(), tr("check-in-uri", "check-ins", "bejelentkezés"), Modifier.weight(1f))
            Stat(u.placesVisited.toString(), tr("locuri", "places", "hely"), Modifier.weight(1f))
        }
        Section(tr("Tronurile cucerite", "Conquered thrones", "Meghódított trónok"))
        if (u.conquered.isEmpty()) Text(tr("Încă niciun tron.", "No thrones yet.", "Még nincs trón."), color = TextMuted, modifier = Modifier.padding(horizontal = 20.dp))
        u.conquered.forEach { c ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
                .border(1.dp, Track, RoundedCornerShape(14.dp)).clickable { onPlace(c.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(categoryIcon(c.category), null, tint = Gold)
                Text("  " + c.name, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.WorkspacePremium, null, tint = Gold, modifier = Modifier.size(18.dp))
            }
        }
        Section(tr("Insigne", "Badges", "Jelvények"))
        if (u.badges.isEmpty()) Text(tr("Încă nicio insignă.", "No badges yet.", "Még nincs jelvény."), color = TextMuted, modifier = Modifier.padding(horizontal = 20.dp))
        u.badges.forEach { b ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
                .border(1.dp, Track, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MilitaryTech, null, tint = Gold)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(b.title, color = Ink, fontWeight = FontWeight.Bold)
                    Text(b.description, color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp, Track, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(label, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
}
