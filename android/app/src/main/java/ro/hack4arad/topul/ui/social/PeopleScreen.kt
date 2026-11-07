package ro.hack4arad.topul.ui.social

import ro.hack4arad.topul.util.runCatching
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Person
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr

/** Arădenii din aplicație (activi recent primii) sau membrii unei conversații; atingi pe cineva → profilul lui. */
@Composable
fun PeopleScreen(roomId: Int?, onBack: () -> Unit, onUser: (Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    LaunchedEffect(q, roomId) {
        if (roomId == null) kotlinx.coroutines.delay(250)
        runCatching { if (roomId != null) Network.api.roomMembers(roomId) else Network.api.people(q.trim()) }.onSuccess { people = it }
    }
    val shown = if (roomId != null && q.isNotBlank()) people.filter { it.name.contains(q.trim().removePrefix("@"), true) } else people
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
                Column {
                    Text(if (roomId != null) tr("Membri", "Members", "Tagok") else tr("Arădenii", "People of Arad", "Aradiak"),
                        color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(if (roomId != null) "${people.size} " + tr("persoane", "people", "fő") else tr("Activi recent primii · atinge pentru profil", "Recently active first", "Aktívak elöl"),
                        color = TextMuted, fontSize = 13.sp)
                }
            }
            OutlinedTextField(q, { q = it }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(tr("Caută după username", "Search by username", "Keresés")) },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        items(shown, key = { it.id }) { p ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                .border(1.dp, Track, RoundedCornerShape(16.dp)).clickable { onUser(p.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.name, 44.dp, emoji = p.avatarEmoji, colorKey = p.avatarColor, photoUrl = p.avatarUrl)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("@" + p.name + if (p.isMe) tr(" (tu)", " (you)", " (te)") else "", color = Ink, fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(tr("Nivel ", "Level ", "Szint ") + p.level, "${p.thrones} " + tr("tronuri", "thrones", "trón"),
                        if (p.isOwner) tr("creatorul grupului", "group owner", "alapító") else null).joinToString(" · "), color = TextMuted, fontSize = 12.sp)
                }
                when {
                    p.friend -> Text(tr("prieten", "friend", "barát"), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    p.active -> Text("● " + tr("activ", "active", "aktív"), color = androidx.compose.ui.graphics.Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    p.new -> Text(tr("nou", "new", "új"), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
