package ro.hack4arad.topul.ui.social

import ro.hack4arad.topul.util.runCatching
import ro.hack4arad.topul.ui.theme.OnInk
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.CityEvent
import ro.hack4arad.topul.data.CodeIn
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Room
import ro.hack4arad.topul.data.RoomIn
import ro.hack4arad.topul.data.RoomTag
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.NightDeep
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.userMessage

fun tagIcon(tag: String, kind: String): ImageVector = when {
    kind == "general" -> Icons.Rounded.Campaign
    kind == "direct" -> Icons.Rounded.Person
    tag == "vecini" -> Icons.Rounded.Home
    tag == "cartier" -> Icons.Rounded.Groups
    tag == "bloc" -> Icons.Rounded.Apartment
    tag == "scoala" -> Icons.Rounded.School
    tag == "sport" -> Icons.Rounded.SportsSoccer
    else -> Icons.Rounded.Groups
}

/** Comunitate: chatul general al Aradului (anunțuri, evenimente), grupurile mele (vecini, bloc, școală…), conversațiile private. */
@Composable
fun CommunityScreen(onBack: () -> Unit, onRoom: (Int, String) -> Unit, onPeople: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var tags by remember { mutableStateOf<List<RoomTag>>(emptyList()) }
    var events by remember { mutableStateOf<List<CityEvent>>(emptyList()) }
    var dialog by remember { mutableStateOf<String?>(null) }   // create | join
    var toast by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        runCatching { Network.api.rooms() }.onSuccess { rooms = it.rooms; tags = it.tags }.onFailure { toast = it.userMessage() }
        runCatching { Network.api.events() }.onSuccess { events = it }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
            Text(tr("Comunitate", "Community", "Közösség"), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onPeople) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Groups, null, tint = Ink)
                Text(" " + tr("Arădenii", "People", "Aradiak"), color = Ink, fontWeight = FontWeight.Bold)
            }
        }
        toast?.let { Text(it, color = OnInk, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink).padding(12.dp)) }

        // evenimentele care urmează (din comunitatea generală)
        if (events.isNotEmpty()) {
            Section(tr("Evenimente în Arad", "Events in Arad", "Események Aradon"))
            events.take(5).forEach { e ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.clip(RoundedCornerShape(10.dp)).background(NightDeep).padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(e.at.substring(8, 10), color = Color.White, fontWeight = FontWeight.Black)
                        Text(monthName(e.at.substring(5, 7)), color = Color.White, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = Ink, fontWeight = FontWeight.Bold)
                        Text(e.at.substring(11, 16) + (e.place?.let { " · $it" } ?: ""), color = TextMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.Event, null, tint = Ink)
                }
            }
        }

        Section(tr("Conversații", "Chats", "Beszélgetések"))
        rooms.forEach { r ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (r.kind == "general") NightDeep else Panel).border(1.dp, Track, RoundedCornerShape(18.dp))
                .clickable { onRoom(r.id, r.name) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val onDark = r.kind == "general"
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (onDark) Color.White.copy(alpha = 0.15f) else Track), contentAlignment = Alignment.Center) {
                    Icon(tagIcon(r.tag, r.kind), null, tint = if (onDark) Color.White else Ink)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, color = if (onDark) Color.White else Ink, fontWeight = FontWeight.Black)
                    Text(r.lastText ?: (if (r.kind == "general") r.description else r.tagLabel.ifBlank { tr("Fără mesaje încă", "No messages yet", "Nincs üzenet") }),
                        color = if (onDark) Color.White.copy(alpha = 0.8f) else TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                r.members?.let { Text("$it", color = TextMuted, fontSize = 12.sp) }
            }
        }

        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { dialog = "create" }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Add, null); Text(" " + tr("Grup nou", "New group", "Új csoport")) }
            OutlinedButton(onClick = { dialog = "join" }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Key, null, tint = Ink); Text(" " + tr("Am un cod", "I have a code", "Van kódom"), color = Ink) }
        }
        Text(tr("Grupuri pentru vecini, bloc, cartier, clasă sau echipă. Prietenii îi adaugi direct, ceilalți intră cu codul de invitație.",
            "Groups for neighbours, building, class or team. Add friends directly; others join with the invite code.", ""),
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
    }

    when (dialog) {
        "create" -> {
            var name by remember { mutableStateOf("") }
            var tag by remember { mutableStateOf("vecini") }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(tr("Grup nou", "New group", "Új csoport")) },
                text = {
                    Column {
                        OutlinedTextField(name, { if (it.length <= 60) name = it }, label = { Text(tr("Nume, ex. Blocul 12, scara B", "Name", "Név")) }, singleLine = true)
                        Spacer(Modifier.size(8.dp))
                        tags.chunked(3).forEach { row ->
                            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { t ->
                                    val on = t.code == tag
                                    Text(t.label, color = if (on) OnInk else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Track).clickable { tag = t.code }
                                            .padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            runCatching { Network.api.createRoom(RoomIn(name.trim(), tag)) }
                                .onSuccess { dialog = null; reload++; onRoom(it.id, it.name) }.onFailure { toast = it.userMessage() }
                        }
                    }, enabled = name.trim().length >= 3) { Text(tr("Creează", "Create", "Létrehoz")) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
            )
        }
        "join" -> {
            var code by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(tr("Intră într-un grup", "Join a group", "Csatlakozás")) },
                text = { OutlinedTextField(code, { code = it.uppercase().take(12) }, label = { Text(tr("Codul de invitație", "Invite code", "Meghívókód")) }, singleLine = true) },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            runCatching { Network.api.joinRoom(CodeIn(code)) }
                                .onSuccess { dialog = null; reload++; onRoom(it.id, it.name) }.onFailure { toast = it.userMessage() }
                        }
                    }, enabled = code.length >= 4) { Text(tr("Intră", "Join", "Belépés")) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
            )
        }
    }
}

fun monthName(mm: String): String = listOf("ian", "feb", "mar", "apr", "mai", "iun", "iul", "aug", "sep", "oct", "nov", "dec")
    .getOrElse((mm.toIntOrNull() ?: 1) - 1) { "" }

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
}
