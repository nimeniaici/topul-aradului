package ro.hack4arad.topul.ui.city

import android.content.Context
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.hack4arad.topul.data.GunoiPlace
import ro.hack4arad.topul.data.GunoiSchedule
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.userMessage

internal fun hexColor(hex: String, fallback: Color = Color(0xFF546E7A)) =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

private const val PREFS = "gunoi"

/** Calendarul de ridicare a deșeurilor (date RETIM): cauți strada și vezi zilele pentru fiecare tip. */
@Composable
fun GunoiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GunoiPlace>>(emptyList()) }
    var placeId by remember { mutableStateOf(prefs.getInt("place_id", 0).takeIf { it > 0 }) }
    var schedule by remember { mutableStateOf<GunoiSchedule?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(250)
        runCatching { Network.api.gunoiSearch(query.trim()) }.onSuccess { results = it; error = null }.onFailure { error = it.userMessage() }
    }
    LaunchedEffect(placeId) {
        val id = placeId ?: return@LaunchedEffect
        runCatching { Network.api.gunoi(id) }.onSuccess { schedule = it; error = null }.onFailure { error = it.userMessage() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
            Column(Modifier.weight(1f)) {
                Text(tr("Calendar gunoi", "Waste calendar", "Hulladéknaptár"), color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(tr("Când vine mașina pe strada ta", "Pickup days on your street", "Mikor jön az autó"), color = TextMuted, fontSize = 13.sp)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextMuted) },
            placeholder = { Text(tr("Caută strada (ex. Ady Endre)", "Search your street", "Utca keresése"), color = TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, focusedBorderColor = Ink,
                unfocusedBorderColor = Ink.copy(alpha = 0.45f), cursorColor = Ink),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        error?.let { Text(it, color = Pink, modifier = Modifier.padding(16.dp)) }
        if (results.isNotEmpty()) {
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp, Track, RoundedCornerShape(16.dp))) {
                results.take(10).forEach { p ->
                    Column(Modifier.fillMaxWidth().clickable {
                        placeId = p.id; query = ""; results = emptyList()
                        prefs.edit().putInt("place_id", p.id).apply()
                    }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(p.street, color = Ink, fontWeight = FontWeight.Bold)
                        Text(tr("Cartier: ", "District: ", "Negyed: ") + p.cartier, color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        val s = schedule
        if (s == null && results.isEmpty()) {
            Text(tr("Scrie numele străzii tale și îți arăt zilele pentru menajer, bio, hârtie și plastic. Aplicația ține minte strada.",
                "Type your street name to see pickup days. The app remembers it.", "Írd be az utcád nevét."),
                color = TextMuted, modifier = Modifier.padding(20.dp))
        }
        if (s != null) {
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink).padding(16.dp)) {
                Text(s.place.street, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(tr("Cartier ", "District ", "Negyed ") + s.place.cartier, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                s.fractions.firstOrNull()?.let { f ->
                    Spacer(Modifier.size(10.dp))
                    Text(tr("Următoarea ridicare: ", "Next pickup: ", "Következő: ") + f.label.lowercase() + ", " + f.nextLabel,
                        color = Gold, fontWeight = FontWeight.Bold)
                }
            }
            Section(tr("Pe tipuri", "By type", "Típusonként"))
            s.fractions.forEach { f ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                    .border(1.dp, Track, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(hexColor(f.color)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Delete, null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(f.label, color = Ink, fontWeight = FontWeight.Bold)
                        Text(f.hint, color = TextMuted, fontSize = 12.sp)
                        f.weekday?.let { Text(tr("În fiecare ", "Every ", "Minden ") + it, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(f.nextLabel, color = if (f.inDays <= 1) Pink else Ink, fontWeight = FontWeight.Black)
                        if (f.inDays > 1) Text(tr("în ${f.inDays} zile", "in ${f.inDays} days", "${f.inDays} nap"), color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
            Section(tr("Următoarele 2 săptămâni", "Next 2 weeks", "Következő 2 hét"))
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                .border(1.dp, Track, RoundedCornerShape(16.dp)).padding(12.dp)) {
                if (s.upcoming.isEmpty()) Text(tr("Nicio ridicare programată.", "No pickups scheduled.", "Nincs."), color = TextMuted)
                s.upcoming.groupBy { it.date }.forEach { (_, items) ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(items.first().label, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items.forEach { u ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(hexColor(u.color)))
                                    Text(" " + u.typeLabel.substringBefore(" (").substringBefore(" și"), color = Ink, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Text(" " + s.note + " " + tr("Sursa: ", "Source: ", "Forrás: ") + s.source + ". " +
                    tr("Verifică și pe retim.ro dacă se schimbă programul.", "Check retim.ro for changes.", "Ellenőrizd a retim.ro oldalon."),
                    color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
internal fun Section(title: String) {
    Text(title.uppercase(), color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
}
