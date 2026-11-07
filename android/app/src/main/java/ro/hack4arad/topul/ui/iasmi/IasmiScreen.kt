package ro.hack4arad.topul.ui.iasmi

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.IasmiIn
import ro.hack4arad.topul.data.IasmiMessage
import ro.hack4arad.topul.data.IasmiPlace
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.ui.theme.Electric
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.NightDeep
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.PanelHigh
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.Violet
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.currentFix
import ro.hack4arad.topul.util.hasLocationPermission
import ro.hack4arad.topul.util.userMessage

private data class Bubble(val role: String, val text: String, val places: List<IasmiPlace> = emptyList())

/** Chat cu Iasmi: întrebări despre oraș, plăți, cum ajungi undeva, locuri; răspunsuri cu locuri pe care le poți deschide. */
@Composable
fun IasmiScreen(onBack: () -> Unit, onPlace: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chat = remember {
        mutableStateListOf(Bubble("assistant", tr(
            "Bună! Sunt Iasmi, asistenta ta din Arad. Te pot ajuta să găsești locuri, să afli cum plătești o taxă sau cum ajungi undeva, ori ce s-a semnalat în oraș.",
            "Hi! I'm Iasmi, your Arad assistant. Ask me about places, payments, getting around, or city reports.",
            "Szia! Iasmi vagyok, aradi asszisztensed. Kérdezz helyekről, fizetésről, közlekedésről.",
        )))
    }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(chat.size, thinking) { list.animateScrollToItem(chat.size + if (thinking) 1 else 0) }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || thinking) return
        chat.add(Bubble("user", q))
        input = ""
        thinking = true
        scope.launch {
            val fix = if (hasLocationPermission(context)) runCatching { currentFix(context) }.getOrNull() else null
            // trimitem ultimele mesaje (fără salutul inițial), ca Iasmi să țină minte conversația
            val history = chat.drop(1).takeLast(12).map { IasmiMessage(it.role, it.text) }
            runCatching { Network.api.iasmi(IasmiIn(history, fix?.lat, fix?.lng)) }
                .onSuccess { chat.add(Bubble("assistant", it.reply, it.places)) }
                .onFailure { chat.add(Bubble("assistant", it.userMessage())) }
            thinking = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
            IasmiOrb(40)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Iasmi", color = Ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(tr("Asistenta AI a Aradului", "Arad's AI assistant", "Arad MI-asszisztense"), color = TextMuted, fontSize = 12.sp)
            }
        }

        LazyColumn(
            state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(chat) { _, b -> BubbleRow(b, onPlace) }
            if (thinking) item { Typing() }
        }

        // întrebări rapide
        if (chat.size <= 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(
                    tr("Unde mănânc o pizza bună?", "Where can I get good pizza?", "Hol eszem jó pizzát?"),
                    tr("Cum plătesc impozitul pe casă?", "How do I pay property tax?", "Hogyan fizetem az adót?"),
                    tr("Cum ajung la Cetate?", "How do I get to the Fortress?", "Hogyan jutok a várhoz?"),
                    tr("Ce probleme s-au semnalat azi?", "What was reported today?", "Mit jelentettek ma?"),
                )) { s ->
                    Text(s, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Panel).border(1.dp, Track, RoundedCornerShape(50))
                            .clickable { send(s) }.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
            Spacer(Modifier.size(8.dp))
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { if (it.length <= 2000) input = it },
                placeholder = { Text(tr("Întreab-o pe Iasmi…", "Ask Iasmi…", "Kérdezd Iasmit…")) },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { send(input) }, enabled = input.isNotBlank() && !thinking,
                modifier = Modifier.size(52.dp).clip(CircleShape).background(if (input.isNotBlank()) NightDeep else Track),
            ) { Icon(Icons.AutoMirrored.Rounded.Send, tr("Trimite", "Send", "Küldés"), tint = Color.White) }
        }
    }
}

/** Cercul animat al lui Iasmi (gradient în culorile paletei, care „respiră”). */
@Composable
fun IasmiOrb(sizeDp: Int) {
    val t = rememberInfiniteTransition(label = "orb")
    val s by t.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "s")
    Box(
        Modifier.size(sizeDp.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape)
            .background(Brush.linearGradient(listOf(NightDeep, Violet, Electric, Mint))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size((sizeDp * 0.5f).dp)) }
}

@Composable
private fun BubbleRow(b: Bubble, onPlace: (Int) -> Unit) {
    val mine = b.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Text(
            b.text, color = if (mine) Color.White else Ink, fontSize = 15.sp,
            modifier = Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 4.dp, bottomEnd = if (mine) 4.dp else 20.dp))
                .background(if (mine) NightDeep else Panel)
                .then(if (mine) Modifier else Modifier.border(1.dp, Track, RoundedCornerShape(20.dp)))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        // locurile găsite de Iasmi: le deschizi direct
        b.places.forEach { p ->
            Row(
                Modifier.padding(top = 6.dp).widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(PanelHigh)
                    .clickable { onPlace(p.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Place, null, tint = Ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(p.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(listOfNotNull(p.category, p.distanceM?.let { "${it.toInt()} m" }).joinToString(" · "), color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Typing() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Panel).padding(horizontal = 16.dp, vertical = 14.dp)) {
        listOf(0, 150, 300).forEach { d ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = d), RepeatMode.Reverse), label = "d")
            Box(Modifier.padding(horizontal = 3.dp).size(8.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(Ink))
        }
    }
}
