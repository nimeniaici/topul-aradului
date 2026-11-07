package ro.hack4arad.topul.ui.social

import androidx.compose.material.icons.rounded.Group
import ro.hack4arad.topul.ui.theme.OnInk
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.ChatMsg
import ro.hack4arad.topul.data.ChatMsgIn
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Room
import ro.hack4arad.topul.data.UsernameIn
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.NightDeep
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.PanelHigh
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.userMessage
import java.time.LocalDate

/** O conversație: comunitatea generală, un grup sau un prieten. Mesajele noi apar live. */
@Composable
fun ChatScreen(roomId: Int, title: String, onBack: () -> Unit, onUser: (Int) -> Unit = {}, onMembers: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf<ChatMsg>() }
    var room by remember { mutableStateOf<Room?>(null) }
    var input by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) } // anunt | eveniment | add | invite
    val list = rememberLazyListState()

    suspend fun fetchNew() {
        val after = msgs.lastOrNull()?.id ?: 0
        runCatching { Network.api.roomMessages(roomId, after) }.onSuccess { new -> msgs.addAll(new.filter { n -> msgs.none { it.id == n.id } }) }
            .onFailure { toast = it.userMessage() }
    }
    LaunchedEffect(roomId) {
        runCatching { Network.api.rooms() }.onSuccess { r -> room = r.rooms.firstOrNull { it.id == roomId } }
        fetchNew()
    }
    LaunchedEffect(roomId) { Network.live.events.filter { it.type == "chat" && it.roomId == roomId }.collect { fetchNew() } }
    LaunchedEffect(roomId) { while (true) { delay(15_000); fetchNew() } }   // rezervă, dacă legătura live cade
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }
    LaunchedEffect(toast) { if (toast != null) { delay(3000); toast = null } }

    fun send(body: ChatMsgIn) = scope.launch {
        runCatching { Network.api.sendMessage(roomId, body) }.onSuccess { m -> if (msgs.none { it.id == m.id }) msgs.add(m) }
            .onFailure { toast = it.userMessage() }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
            Column(Modifier.weight(1f)) {
                Text(room?.name ?: title, color = Ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
                room?.let { r ->
                    Text(when (r.kind) { "general" -> tr("Toți arădenii · anunțuri și evenimente", "All of Arad", "Mindenki")
                        "direct" -> tr("Conversație privată", "Private chat", "Privát"); else -> r.tagLabel + (r.members?.let { " · $it " + tr("membri", "members", "tag") } ?: "") },
                        color = TextMuted, fontSize = 12.sp)
                }
            }
            IconButton(onClick = onMembers) { Icon(Icons.Rounded.Group, tr("Membri", "Members", "Tagok"), tint = Ink) }
            if (room?.kind == "group") {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Ink) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Codul de invitație", "Invite code", "Meghívókód")) }, onClick = { menu = false; dialog = "invite" })
                    DropdownMenuItem(text = { Text(tr("Adaugă un prieten", "Add a friend", "Barát hozzáadása")) }, onClick = { menu = false; dialog = "add" })
                    DropdownMenuItem(text = { Text(tr("Ieși din grup", "Leave group", "Kilépés")) }, onClick = {
                        menu = false
                        scope.launch { runCatching { Network.api.leaveRoom(roomId) }.onSuccess { onBack() }.onFailure { toast = it.userMessage() } }
                    })
                }
            }
        }
        toast?.let { Text(it, color = OnInk, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink).padding(10.dp)) }

        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (msgs.isEmpty()) item {
                Text(tr("Scrie primul mesaj.", "Write the first message.", "Írd az első üzenetet."), color = TextMuted, modifier = Modifier.padding(16.dp))
            }
            items(msgs, key = { it.id }) { m -> MessageRow(m, onUser) }
        }

        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (room?.kind != "direct") {
                IconButton(onClick = { dialog = "pick" }) { Icon(Icons.Rounded.AddCircle, tr("Anunț sau eveniment", "Announcement or event", ""), tint = Ink) }
            }
            OutlinedTextField(
                value = input, onValueChange = { if (it.length <= 1000) input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(tr("Scrie un mesaj…", "Write a message…", "Üzenet…")) }, shape = RoundedCornerShape(24.dp), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) { send(ChatMsgIn(input.trim())); input = "" } }),
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { if (input.isNotBlank()) { send(ChatMsgIn(input.trim())); input = "" } },
                modifier = Modifier.size(50.dp).clip(CircleShape).background(if (input.isNotBlank()) NightDeep else Track)) {
                Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color.White)
            }
        }
    }

    when (dialog) {
        "pick" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(tr("Ce publici?", "What do you post?", "Mit teszel közzé?")) },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { dialog = "anunt" }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Campaign, null, tint = Ink); Text("  " + tr("Anunț local", "Local announcement", "Helyi hirdetmény"), color = Ink, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { dialog = "eveniment" }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Event, null, tint = Ink); Text("  " + tr("Eveniment (apare în calendar)", "Event (shows in calendar)", "Esemény"), color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
        )
        "anunt" -> {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(tr("Anunț local", "Local announcement", "Hirdetmény")) },
                text = { OutlinedTextField(text, { if (it.length <= 1000) text = it }, label = { Text(tr("Ex.: Mâine se oprește apa pe strada…", "Text", "Szöveg")) }, minLines = 3) },
                confirmButton = { Button(onClick = { send(ChatMsgIn(text.trim(), kind = "anunt")); dialog = null }, enabled = text.isNotBlank()) { Text(tr("Publică", "Post", "Közzétesz")) } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
            )
        }
        "eveniment" -> {
            var titleT by remember { mutableStateOf("") }
            var date by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
            var time by remember { mutableStateOf("19:00") }
            var place by remember { mutableStateOf("") }
            var details by remember { mutableStateOf("") }
            val valid = titleT.isNotBlank() && Regex("""\d{4}-\d{2}-\d{2}""").matches(date) && Regex("""\d{2}:\d{2}""").matches(time)
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(tr("Eveniment nou", "New event", "Új esemény")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(titleT, { titleT = it.take(100) }, label = { Text(tr("Titlu", "Title", "Cím")) }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(date, { date = it.take(10) }, label = { Text(tr("Data (AAAA-LL-ZZ)", "Date", "Dátum")) }, singleLine = true, modifier = Modifier.weight(1.4f))
                            OutlinedTextField(time, { time = it.take(5) }, label = { Text(tr("Ora", "Time", "Idő")) }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(place, { place = it.take(120) }, label = { Text(tr("Locul", "Place", "Hely")) }, singleLine = true)
                        OutlinedTextField(details, { details = it.take(1000) }, label = { Text(tr("Detalii", "Details", "Részletek")) }, minLines = 2)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        send(ChatMsgIn(details.ifBlank { titleT }.trim(), kind = "eveniment", eventTitle = titleT.trim(),
                            eventAt = "${date}T$time:00", eventPlace = place.ifBlank { null }))
                        dialog = null
                    }, enabled = valid) { Text(tr("Publică", "Post", "Közzétesz")) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
            )
        }
        "invite" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(tr("Codul de invitație", "Invite code", "Meghívókód")) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(room?.inviteCode ?: "—", color = Ink, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = 4.sp)
                    Text(tr("Trimite codul vecinilor sau colegilor: îl scriu la Comunitate → „Am un cod”.",
                        "Share it: they enter it under Community → I have a code.", ""), color = TextMuted)
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
        )
        "add" -> {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(tr("Adaugă un prieten", "Add a friend", "Barát hozzáadása")) },
                text = { OutlinedTextField(name, { name = it }, label = { Text("@username") }, singleLine = true) },
                confirmButton = {
                    Button(onClick = {
                        scope.launch { runCatching { Network.api.addMember(roomId, UsernameIn(name)) }.onSuccess { toast = it.detail }.onFailure { toast = it.userMessage() } }
                        dialog = null
                    }, enabled = name.isNotBlank()) { Text(tr("Adaugă", "Add", "Hozzáad")) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
            )
        }
    }
}

@Composable
private fun MessageRow(m: ChatMsg, onUser: (Int) -> Unit = {}) {
    val special = m.kind != "text"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine && !special) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!m.mine || special) {
            androidx.compose.foundation.layout.Box(Modifier.clickable { onUser(m.userId) }) { Avatar(m.userName, 34.dp, colorKey = m.avatarColor, photoUrl = m.avatarUrl) }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier.widthIn(max = if (special) 320.dp else 280.dp).clip(RoundedCornerShape(18.dp))
                .background(when { special -> PanelHigh; m.mine -> NightDeep; else -> Panel }).padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (!m.mine || special) Text("@${m.userName}", color = if (m.mine && !special) Color.White else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onUser(m.userId) })
            if (m.kind == "eveniment") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Event, null, tint = Gold, modifier = Modifier.size(18.dp))
                    Text("  " + (m.eventTitle ?: ""), color = Ink, fontWeight = FontWeight.Black)
                }
                m.eventAt?.let { at ->
                    Text("${at.substring(8, 10)} ${monthName(at.substring(5, 7))} · ${at.substring(11, 16)}" + (m.eventPlace?.let { " · $it" } ?: ""),
                        color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                if (m.text != m.eventTitle) Text(m.text, color = Ink, fontSize = 14.sp)
            } else {
                if (m.kind == "anunt") Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Campaign, null, tint = Gold, modifier = Modifier.size(16.dp))
                    Text(" " + tr("ANUNȚ", "ANNOUNCEMENT", "HIRDETMÉNY"), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Text(m.text, color = if (m.mine && !special) Color.White else Ink, fontSize = 15.sp)
            }
            Text(m.createdAt.substring(11, 16), color = if (m.mine && !special) Color.White.copy(alpha = 0.7f) else TextMuted, fontSize = 10.sp)
        }
    }
}
