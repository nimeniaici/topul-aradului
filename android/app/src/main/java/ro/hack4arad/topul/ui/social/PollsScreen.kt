package ro.hack4arad.topul.ui.social

import ro.hack4arad.topul.util.runCatching
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Poll
import ro.hack4arad.topul.data.PollIn
import ro.hack4arad.topul.data.PollVoteIn
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.OnInk
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.userMessage

/** Sondaje: ale Primăriei (oficiale) și ale arădenilor. Votezi o dată (poți schimba cât e deschis). */
@Composable
fun PollsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var polls by remember { mutableStateOf<List<Poll>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    suspend fun load() {
        runCatching { Network.api.polls() }.onSuccess { polls = it }.onFailure { toast = it.userMessage() }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(3500); toast = null } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
                    Column(Modifier.weight(1f)) {
                        Text(tr("Sondaje", "Polls", "Szavazások"), color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text(tr("Spune ce crezi despre oraș", "Have your say about the city", "Mondd el a véleményed"), color = TextMuted, fontSize = 13.sp)
                    }
                }
                toast?.let {
                    Text(it, color = OnInk, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)).background(Ink).padding(12.dp))
                }
                if (loaded && polls.isEmpty()) {
                    Text(tr("Încă nu e niciun sondaj. Fă tu primul!", "No polls yet. Start the first one!", "Még nincs szavazás."),
                        color = TextMuted, modifier = Modifier.padding(20.dp))
                }
            }
            items(polls, key = { it.id }) { p ->
                PollCard(p,
                    onVote = { optionId ->
                        scope.launch {
                            runCatching { Network.api.vote(p.id, PollVoteIn(optionId)) }
                                .onSuccess { upd -> polls = polls.map { if (it.id == upd.id) upd else it } }
                                .onFailure { toast = it.userMessage() }
                        }
                    },
                    onDelete = {
                        scope.launch { runCatching { Network.api.deletePoll(p.id) }.onSuccess { load() }.onFailure { toast = it.userMessage() } }
                    })
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true }, containerColor = Gold, contentColor = GoldDark,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(tr("Sondaj nou", "New poll", "Új szavazás")) },
        )
    }

    if (creating) {
        var question by remember { mutableStateOf("") }
        val options = remember { mutableStateListOf("", "") }
        var days by remember { mutableIntStateOf(7) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(tr("Sondaj nou", "New poll", "Új szavazás"), fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(question, { if (it.length <= 200) question = it }, label = { Text(tr("Întrebarea", "Question", "Kérdés")) },
                        modifier = Modifier.fillMaxWidth())
                    options.forEachIndexed { i, o ->
                        OutlinedTextField(o, { if (it.length <= 100) options[i] = it }, singleLine = true,
                            label = { Text(tr("Varianta ", "Option ", "Lehetőség ") + (i + 1)) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    if (options.size < 6) TextButton(onClick = { options.add("") }) { Text(tr("+ încă o variantă", "+ add option", "+ lehetőség")) }
                    Text(tr("Deschis ", "Open for ", "Nyitva ") + "$days " + tr("zile", "days", "nap"), color = TextMuted, fontSize = 13.sp)
                    Row {
                        listOf(1, 3, 7, 14).forEach { d ->
                            Text("$d", color = if (d == days) GoldDark else Ink, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 6.dp, top = 4.dp).clip(RoundedCornerShape(50))
                                    .background(if (d == days) Gold else Track).clickable { days = d }.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { Network.api.createPoll(PollIn(question.trim(), options.map { it.trim() }.filter { it.isNotEmpty() }, days)) }
                            .onSuccess { creating = false; toast = tr("Sondaj publicat! +5 XP", "Poll published! +5 XP", "Közzétéve! +5 XP"); load() }
                            .onFailure { toast = it.userMessage() }
                    }
                }) { Text(tr("Publică", "Publish", "Közzétesz")) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
        )
    }
}

@Composable
private fun PollCard(p: Poll, onVote: (Int) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel)
        .border(if (p.official) 2.dp else 1.dp, if (p.official) Gold else Track, RoundedCornerShape(20.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (p.official) Icons.Rounded.AccountBalance else Icons.Rounded.HowToVote, null, tint = Gold, modifier = Modifier.size(18.dp))
            Text("  " + (if (p.official) tr("Primăria Arad întreabă", "Arad City Hall asks", "Arad városháza kérdezi") else "@" + p.author) +
                " · " + (if (p.closed) tr("închis", "closed", "lezárva") else tr("deschis", "open", "nyitva")),
                color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (p.mine) Icon(Icons.Rounded.Delete, tr("Șterge", "Delete", "Törlés"), tint = TextMuted,
                modifier = Modifier.size(20.dp).clickable(onClick = onDelete))
        }
        Text(p.question, color = Ink, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.padding(vertical = 8.dp))
        p.options.forEach { o ->
            val mine = p.myVote == o.id
            val pct = o.percent ?: 0
            Box(Modifier.padding(vertical = 3.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Track)
                .border(if (mine) 2.dp else 0.dp, if (mine) Gold else Track, RoundedCornerShape(12.dp))
                .clickable(enabled = !p.closed) { onVote(o.id) }) {
                if (p.resultsVisible) Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(Gold.copy(alpha = 0.35f)))
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (mine) Icon(Icons.Rounded.CheckCircle, null, tint = Gold, modifier = Modifier.size(18.dp))
                    Text((if (mine) "  " else "") + o.text, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (p.resultsVisible) Text("$pct%", color = Ink, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (p.resultsVisible) "${p.totalVotes} " + tr("voturi", "votes", "szavazat") + if (!p.closed) tr(" · poți schimba votul", " · you can change your vote", "") else ""
            else tr("Votează ca să vezi rezultatele · +2 XP", "Vote to see the results · +2 XP", "Szavazz az eredményekért"),
            color = TextMuted, fontSize = 12.sp)
    }
}

@Suppress("unused") private val keep = Pink
