package ro.hack4arad.topul.ui.city

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Trail
import ro.hack4arad.topul.data.TurismDetail
import ro.hack4arad.topul.data.TurismLoc
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.currentFix
import ro.hack4arad.topul.util.hasLocationPermission
import ro.hack4arad.topul.util.userMessage

/** Turism în județul Arad, din datele CNIPT (visitarad.ro): obiective, agrement, cazare și trasee montane. */
@Composable
fun TurismScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tabs = listOf("obiective" to tr("Obiective", "Sights", "Látnivalók"), "agrement" to tr("Agrement", "Leisure", "Szabadidő"),
        "cazare" to tr("Cazare", "Stay", "Szállás"), "trasee" to tr("Trasee", "Trails", "Túrák"))
    var tab by remember { mutableStateOf("obiective") }
    var onlyArad by remember { mutableStateOf(false) }
    var locs by remember { mutableStateOf<List<TurismLoc>>(emptyList()) }
    var trails by remember { mutableStateOf<List<Trail>>(emptyList()) }
    var source by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<TurismDetail?>(null) }
    var trail by remember { mutableStateOf<Trail?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fix by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) runCatching { currentFix(context) }.getOrNull()?.let { fix = it.lat to it.lng }
    }
    LaunchedEffect(tab, onlyArad, fix) {
        error = null; category = null
        if (tab == "trasee") {
            runCatching { Network.api.trails() }.onSuccess { trails = it.trails; source = it.source }.onFailure { error = it.userMessage() }
        } else {
            runCatching { Network.api.turism(type = tab, onlyArad = onlyArad, lat = fix?.first, lng = fix?.second) }
                .onSuccess { locs = it.locations; source = it.source }.onFailure { error = it.userMessage() }
        }
    }
    val categories = remember(locs) { locs.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val shown = remember(locs, category) { if (category == null) locs else locs.filter { it.category == category } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
                Column(Modifier.weight(1f)) {
                    Text(tr("Turism în județul Arad", "Tourism in Arad county", "Arad megyei turizmus"), color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(tr("Obiective, agrement, cazare, trasee", "Sights, leisure, stays, trails", "Látnivalók, túrák"), color = TextMuted, fontSize = 13.sp)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEach { (code, label) -> Chip(label, tab == code) { tab = code } }
                if (tab != "trasee") Chip(tr("Doar orașul Arad", "Arad city only", "Csak Arad"), onlyArad) { onlyArad = !onlyArad }
            }
            if (tab != "trasee" && categories.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(tr("Toate", "All", "Mind") + " (${locs.size})", category == null, small = true) { category = null }
                    categories.forEach { c -> Chip(c, category == c, small = true) { category = if (category == c) null else c } }
                }
            }
            error?.let { Text(it, color = Pink, modifier = Modifier.padding(16.dp)) }
        }
        if (tab == "trasee") {
            items(trails, key = { "t${it.id}" }) { t -> TrailRow(t) { trail = t } }
        } else {
            items(shown, key = { "l${it.id}" }) { l ->
                LocRow(l) {
                    scope.launch {
                        runCatching { Network.api.turismDetail(l.id) }.onSuccess { detail = it }.onFailure { error = it.userMessage() }
                    }
                }
            }
        }
        item {
            Text(tr("Date: ", "Data: ", "Adatok: ") + source, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(16.dp))
        }
    }

    detail?.let { d ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(d.name, fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (d.photos.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(d.photos) { url ->
                                AsyncImage(url, null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(220.dp, 140.dp).clip(RoundedCornerShape(12.dp)).background(Track))
                            }
                        }
                    }
                    Text(listOf(d.category, d.locality).filter { it.isNotBlank() }.joinToString(" · "), color = Gold, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    if (d.address.isNotBlank()) Text(d.address, color = TextMuted, fontSize = 13.sp)
                    Text(d.description.ifBlank { tr("Fără descriere încă.", "No description yet.", "Nincs leírás.") }, color = Ink, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    Column(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(modifier = Modifier.fillMaxWidth(), onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${d.lat},${d.lng}")))
                        }) { Icon(Icons.Rounded.Directions, null); Text(" " + tr("Cum ajung", "Directions", "Útvonal")) }
                        val site = d.website.ifBlank { d.sourceUrl }
                        if (site.isNotBlank()) OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site))) }) {
                            Icon(Icons.Rounded.Language, null, tint = Ink); Text(" " + tr("Site", "Website", "Weboldal"), color = Ink)
                        }
                    }
                    Text(tr("Sursa: ", "Source: ", "Forrás: ") + "CNIPT Arad, visitarad.ro", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("OK") } },
        )
    }
    trail?.let { t ->
        AlertDialog(
            onDismissRequest = { trail = null },
            title = { Text(t.name, fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (t.mapImageUrl.isNotBlank()) AsyncImage(t.mapImageUrl, null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(Track))
                    Text(t.category, color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(t.description, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    if (t.sourceUrl.isNotBlank()) TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.sourceUrl))) }) {
                        Text(tr("Harta traseului pe visitarad.ro", "Trail map on visitarad.ro", "Térkép"))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { trail = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun Chip(label: String, on: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Text(label, color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = if (small) 12.sp else 14.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Panel).border(1.dp, Track, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 6.dp else 8.dp))
}

@Composable
private fun LocRow(l: TurismLoc, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
        .border(1.dp, Track, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Track), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Landscape, null, tint = TextMuted)
            if (l.photo != null) AsyncImage(l.photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(l.name, color = Ink, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(l.category, l.locality.takeIf { it.isNotBlank() },
                l.distanceM?.let { if (it < 1000) "$it m" else String.format("%.1f km", it / 1000.0) }).joinToString(" · "),
                color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (l.summary.isNotBlank()) Text(l.summary, color = TextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrailRow(t: Trail, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
        .border(1.dp, Track, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Hiking, null, tint = Ink, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(t.name, color = Ink, fontWeight = FontWeight.Bold)
            Text(t.description, color = TextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
