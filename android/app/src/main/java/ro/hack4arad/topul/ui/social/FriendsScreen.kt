package ro.hack4arad.topul.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.FriendProfile
import ro.hack4arad.topul.data.FriendsOut
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.SharingIn
import ro.hack4arad.topul.data.UserCard
import ro.hack4arad.topul.data.UsernameIn
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.LocationShare
import ro.hack4arad.topul.util.userMessage

/** Prieteni: caută după username, cereri, lista de prieteni (chat, profil cu locurile cucerite, șterge, blochează)
 *  și comutatorul „Arată-mă prietenilor pe hartă” (oprit implicit). */
@Composable
fun FriendsScreen(onBack: () -> Unit, onChat: (Int) -> Unit, onPlace: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<FriendsOut?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserCard>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<FriendProfile?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, UserCard>?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) { runCatching { Network.api.friends() }.onSuccess { data = it }.onFailure { toast = it.userMessage() } }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        runCatching { Network.api.searchUsers(query.trim()) }.onSuccess { results = it }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(3000); toast = null } }

    fun act(block: suspend () -> String) = scope.launch {
        runCatching { block() }.onSuccess { toast = it; reload++ }.onFailure { toast = it.userMessage() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
            Text(tr("Prieteni", "Friends", "Barátok"), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
        toast?.let { Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink).padding(12.dp)) }

        // poziția pe hartă: oprită implicit
        val d = data
        Box16 {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Arată-mă prietenilor pe hartă", "Show me to friends on the map", "Mutass a térképen"), color = Ink, fontWeight = FontWeight.Bold)
                    Text(tr("Doar prietenii acceptați te văd, doar cât folosești aplicația. Poți opri oricând.",
                        "Only accepted friends, only while you use the app. Turn off anytime.", "Csak a barátok látnak."),
                        color = TextMuted, fontSize = 12.sp)
                }
                Switch(checked = d?.sharing == true, onCheckedChange = { on ->
                    scope.launch {
                        runCatching { Network.api.setSharing(SharingIn(on)) }.onSuccess {
                            LocationShare.setEnabled(context, on); reload++
                            if (on) LocationShare.pushNow(context)
                        }.onFailure { toast = it.userMessage() }
                    }
                })
            }
        }

        // căutare
        OutlinedTextField(query, { query = it }, label = { Text(tr("Caută după @username", "Search @username", "Keresés")) },
            singleLine = true, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth())
        results.forEach { u ->
            PersonRow(u) {
                IconButton(onClick = { act { Network.api.friendRequest(UsernameIn(u.name)).detail } }) { Icon(Icons.Rounded.PersonAdd, tr("Adaugă", "Add", "Hozzáad"), tint = Ink) }
            }
        }

        if (d != null && d.incoming.isNotEmpty()) {
            Section(tr("Cereri primite", "Requests", "Kérések"))
            d.incoming.forEach { u ->
                PersonRow(u) {
                    IconButton(onClick = { act { Network.api.acceptFriend(u.id).detail } }) { Icon(Icons.Rounded.Check, tr("Acceptă", "Accept", "Elfogad"), tint = Ink) }
                    IconButton(onClick = { act { Network.api.removeFriend(u.id).detail } }) { Icon(Icons.Rounded.Close, tr("Refuză", "Decline", "Elutasít"), tint = TextMuted) }
                }
            }
        }

        Section(tr("Prietenii tăi", "Your friends", "Barátaid") + (d?.friends?.size?.let { " ($it)" } ?: ""))
        if (d != null && d.friends.isEmpty()) {
            Text(tr("Încă n-ai prieteni aici. Caută-i după @username.", "No friends yet. Search by @username.", "Még nincsenek barátaid."),
                color = TextMuted, modifier = Modifier.padding(horizontal = 20.dp))
        }
        d?.friends?.forEach { u ->
            PersonRow(u, onClick = { scope.launch { runCatching { Network.api.friendProfile(u.id) }.onSuccess { profile = it } } }) {
                IconButton(onClick = { scope.launch { runCatching { Network.api.directRoom(u.id) }.onSuccess { onChat(it.id) }.onFailure { toast = it.userMessage() } } }) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, "Chat", tint = Ink)
                }
                IconButton(onClick = { confirm = "unfriend" to u }) { Icon(Icons.Rounded.PersonRemove, tr("Șterge", "Unfriend", "Törlés"), tint = TextMuted) }
                IconButton(onClick = { confirm = "block" to u }) { Icon(Icons.Rounded.Block, tr("Blochează", "Block", "Letilt"), tint = Pink) }
            }
        }

        if (d != null && d.outgoing.isNotEmpty()) {
            Section(tr("Cereri trimise", "Sent", "Elküldve"))
            d.outgoing.forEach { u -> PersonRow(u) { TextButton(onClick = { act { Network.api.removeFriend(u.id).detail } }) { Text(tr("Retrage", "Cancel", "Visszavon"), color = TextMuted) } } }
        }
        if (d != null && d.blocked.isNotEmpty()) {
            Section(tr("Blocați", "Blocked", "Letiltva"))
            d.blocked.forEach { u -> PersonRow(u) { TextButton(onClick = { act { Network.api.unblockUser(u.id).detail } }) { Text(tr("Deblochează", "Unblock", "Feloldás"), color = Ink) } } }
        }
    }

    confirm?.let { (what, u) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "block") tr("Blochezi pe @", "Block @", "Letiltod @") + u.name + "?" else tr("Ștergi pe @", "Unfriend @", "Törlöd @") + u.name + "?") },
            text = { Text(if (what == "block") tr("Nu-ți mai poate scrie, nu-ți vede poziția și nu te mai găsește.", "They can't message you or see you.", "Nem írhat neked.")
                else tr("Nu mai sunteți prieteni. Îl poți adăuga din nou oricând.", "You can add them again anytime.", "Bármikor újra hozzáadhatod."), color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    act { if (what == "block") Network.api.blockUser(u.id).detail else Network.api.removeFriend(u.id).detail }
                }) { Text(if (what == "block") tr("Blochează", "Block", "Letilt") else tr("Șterge", "Unfriend", "Törlés"), color = Pink) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
        )
    }

    // profilul prietenului: locurile pe care le-a cucerit
    profile?.let { p ->
        AlertDialog(
            onDismissRequest = { profile = null },
            title = { Text("@${p.name}") },
            text = {
                Column {
                    Text(tr("Nivel ", "Level ", "Szint ") + "${p.level} · ${p.xp} XP · ${p.thrones} " + tr("tronuri", "thrones", "trón"), color = TextMuted)
                    Spacer(Modifier.size(10.dp))
                    if (p.conquered.isEmpty()) Text(tr("Încă n-a cucerit niciun loc.", "No thrones yet.", "Még nincs trónja."), color = Ink)
                    p.conquered.forEach { pl ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { profile = null; onPlace(pl.id) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WorkspacePremium, null, tint = Gold, modifier = Modifier.size(18.dp))
                            Text("  " + pl.name, color = Ink, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { profile = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun Box16(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel)
        .border(1.dp, Track, RoundedCornerShape(18.dp)).padding(14.dp)) { content() }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun PersonRow(u: UserCard, onClick: (() -> Unit)? = null, actions: @Composable () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Avatar(u.name, 44.dp, emoji = u.avatarEmoji, colorKey = u.avatarColor, photoUrl = u.avatarUrl)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("@${u.name}", color = Ink, fontWeight = FontWeight.Bold)
            Text(tr("Nivel ", "Level ", "Szint ") + "${u.level} · ${u.thrones} " + tr("tronuri", "thrones", "trón"), color = TextMuted, fontSize = 12.sp)
        }
        actions()
    }
}
