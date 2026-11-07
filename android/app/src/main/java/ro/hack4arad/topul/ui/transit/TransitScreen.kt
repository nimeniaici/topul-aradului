package ro.hack4arad.topul.ui.transit

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import ro.hack4arad.topul.BuildConfig
import ro.hack4arad.topul.data.NextTrams
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Ticket
import ro.hack4arad.topul.data.TicketIn
import ro.hack4arad.topul.data.TransitInfo
import ro.hack4arad.topul.data.TransitLine
import ro.hack4arad.topul.data.TransitReportIn
import ro.hack4arad.topul.data.TransitReportOut
import ro.hack4arad.topul.data.TransitStop
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.currentFix
import ro.hack4arad.topul.util.hasLocationPermission
import ro.hack4arad.topul.util.userMessage

private val ARAD = LatLng(46.1745, 21.3185)
private fun lineColor(hex: String) = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF455A64))

/** Transport CTP Arad: harta liniilor, următorul tramvai din stația ta, semnalări live și bilete (demo). */
@Composable
fun TransitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<TransitLine>>(emptyList()) }
    var info by remember { mutableStateOf<TransitInfo?>(null) }
    var stops by remember { mutableStateOf<List<TransitStop>>(emptyList()) }
    var selectedStop by remember { mutableStateOf<String?>(null) }
    var next by remember { mutableStateOf<NextTrams?>(null) }
    var reports by remember { mutableStateOf<List<TransitReportOut>>(emptyList()) }
    var tickets by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var highlight by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var reportFor by remember { mutableStateOf<String?>(null) } // linia pentru care semnalăm
    var showTicket by remember { mutableStateOf<Ticket?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { Network.api.transitLines() }.onSuccess { lines = it }.onFailure { error = it.userMessage() }
        runCatching { Network.api.transitInfo() }.onSuccess { info = it }
        runCatching { Network.api.transitReports() }.onSuccess { reports = it }
        runCatching { Network.api.myTickets() }.onSuccess { tickets = it }
        val fix = if (hasLocationPermission(context)) runCatching { currentFix(context) }.getOrNull() else null
        runCatching { Network.api.nearStops(fix?.lat ?: ARAD.latitude, fix?.lng ?: ARAD.longitude) }
            .onSuccess { stops = it; if (selectedStop == null) selectedStop = it.firstOrNull()?.id }
    }
    LaunchedEffect(selectedStop) {
        val id = selectedStop ?: return@LaunchedEffect
        // reîmprospătăm minutele cât stai pe ecran
        while (true) {
            runCatching { Network.api.nextTrams(id) }.onSuccess { next = it }
            kotlinx.coroutines.delay(30_000)
        }
    }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(3500); toast = null } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
            Column(Modifier.weight(1f)) {
                Text(tr("Transport CTP", "CTP transport", "CTP közlekedés"), color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(tr("Tramvaiele Aradului", "Arad trams", "Aradi villamosok"), color = TextMuted, fontSize = 13.sp)
            }
        }
        toast?.let {
            Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)).background(Ink).padding(12.dp))
        }
        error?.let { Text(it, color = Pink, modifier = Modifier.padding(16.dp)) }

        // ---- harta liniilor
        if (BuildConfig.HAS_MAPS_KEY && lines.isNotEmpty()) {
            val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(ARAD, 12.6f) }
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp))) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(), cameraPositionState = camera,
                    properties = MapProperties(isMyLocationEnabled = hasLocationPermission(context)),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
                ) {
                    lines.forEach { l ->
                        val on = highlight == null || highlight == l.ref
                        Polyline(
                            points = l.shape.map { LatLng(it[0], it[1]) },
                            color = lineColor(l.color).copy(alpha = if (on) 0.95f else 0.15f),
                            width = if (highlight == l.ref) 14f else 8f,
                        )
                    }
                    stops.forEach { s ->
                        Circle(center = LatLng(s.lat, s.lng), radius = 28.0, fillColor = Ink.copy(alpha = 0.85f), strokeColor = Color.White, strokeWidth = 3f)
                        Marker(
                            state = remember(s.id) { MarkerState(LatLng(s.lat, s.lng)) }, title = s.name,
                            snippet = tr("Linii: ", "Lines: ", "Vonalak: ") + s.lines.joinToString(", "),
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE),
                            onClick = { selectedStop = s.id; false },
                        )
                    }
                }
            }
        }
        // ---- liniile (atingi ca să evidențiezi pe hartă)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.forEach { l ->
                val on = highlight == l.ref
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) lineColor(l.color) else Panel)
                        .border(1.dp, lineColor(l.color), RoundedCornerShape(50)).clickable { highlight = if (on) null else l.ref }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(l.ref, color = if (on) Color.White else lineColor(l.color), fontWeight = FontWeight.Black)
                    Text("  ${l.to}", color = if (on) Color.White else Ink, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        // ---- următorul tramvai
        Section(tr("Următorul tramvai", "Next tram", "Következő villamos"))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stops.forEach { s ->
                val on = selectedStop == s.id
                Text(
                    s.name + (s.distanceM?.let { " · ${it} m" } ?: ""), color = if (on) Color.White else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Panel).border(1.dp, Track, RoundedCornerShape(50))
                        .clickable { selectedStop = s.id }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Card {
            val n = next
            if (n == null) Text(tr("Se încarcă…", "Loading…", "Betöltés…"), color = TextMuted)
            else if (n.departures.isEmpty()) Text(tr("Niciun tramvai în perioada asta.", "No trams right now.", "Most nincs villamos."), color = TextMuted)
            else n.departures.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(lineColor(d.color)), contentAlignment = Alignment.Center) {
                        Text(d.line, color = Color.White, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("spre ", "to ", "") + d.towards, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            Text(" " + d.minutes.joinToString(" · ") { if (it == 0) tr("acum", "now", "most") else "$it min" }, color = TextMuted, fontSize = 13.sp)
                        }
                        if (d.crowded > 0 || d.delayed > 0) {
                            Text(
                                listOfNotNull(
                                    if (d.crowded > 0) tr("aglomerat", "crowded", "zsúfolt") + " (${d.crowded})" else null,
                                    if (d.delayed > 0) tr("întârzieri", "delays", "késés") + " (${d.delayed})" else null,
                                ).joinToString(" · "),
                                color = Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    TextButton(onClick = { reportFor = d.line }) { Icon(Icons.Rounded.Warning, null, tint = Pink, modifier = Modifier.size(18.dp)); Text(" " + tr("Semnalează", "Report", "Jelzés"), color = Pink, fontSize = 13.sp) }
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                Text(" " + tr("Ore estimate după frecvența liniilor. Orarul exact e afișat în stație.",
                    "Estimated from line frequency. Exact timetable is posted at the stop.", "Becsült idők."), color = TextMuted, fontSize = 11.sp)
            }
        }

        // ---- semnalări live
        Section(tr("Ce spun arădenii acum", "Live reports", "Élő jelzések"))
        Card {
            if (reports.isEmpty()) Text(tr("Nicio problemă semnalată în ultima oră.", "No reports in the last hour.", "Nincs jelzés."), color = TextMuted)
            reports.take(6).forEach { r ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (r.kind == "aglomerat") Icons.Rounded.Groups else Icons.Rounded.Warning, null, tint = Pink, modifier = Modifier.size(18.dp))
                    Text("  " + tr("Linia ", "Line ", "") + "${r.line}: ${r.label}" + (r.stopName?.let { " · $it" } ?: ""), color = Ink, fontSize = 14.sp)
                }
            }
        }

        // ---- bilete demo
        Section(tr("Bilete și abonamente", "Tickets and passes", "Jegyek és bérletek"))
        Card {
            Text(tr("Demonstrativ: arată cum ar arăta biletul digital. Nu se plătește nimic și nu e valabil în tramvai. Prețurile sunt cele oficiale CTP Arad.",
                "Demo only: no payment, not valid on board. Prices are official CTP Arad fares.", "Csak bemutató."),
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            info?.tickets?.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ConfirmationNumber, null, tint = Ink)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(t.label, color = Ink, fontWeight = FontWeight.Bold)
                        Text(String.format("%.2f lei", t.price), color = TextMuted, fontSize = 12.sp)
                    }
                    Button(onClick = {
                        scope.launch {
                            runCatching { Network.api.buyTicket(TicketIn(t.code)) }
                                .onSuccess { tickets = listOf(it) + tickets; showTicket = it }
                                .onFailure { toast = it.userMessage() }
                        }
                    }) { Text(tr("Ia (demo)", "Get (demo)", "Kérem")) }
                }
            }
            tickets.firstOrNull { it.active }?.let { t ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Track).clickable { showTicket = t }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tram, null, tint = Ink)
                    Text("  " + tr("Biletul tău activ: ", "Your active ticket: ", "Aktív jegy: ") + t.label, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("QR", color = Ink, fontWeight = FontWeight.Black)
                }
            }
        }

        // ---- contact
        info?.let { i ->
            Row(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                .clickable { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + i.dispatchPhone.replace(" ", "")))) }
                .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Call, null, tint = Ink)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(tr("Dispecerat tramvaie CTP", "CTP tram dispatch", "CTP diszpécser") + ": " + i.dispatchPhone, color = Ink, fontWeight = FontWeight.Bold)
                    Text(i.source, color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }

    reportFor?.let { line ->
        val kinds = listOf(
            "aglomerat" to tr("Foarte aglomerat", "Very crowded", "Zsúfolt"),
            "intarziere" to tr("Întârziere", "Delay", "Késés"),
            "defect" to tr("Tramvai defect / oprit", "Broken down", "Elromlott"),
            "curat" to tr("Murdar", "Dirty", "Piszkos"),
        )
        AlertDialog(
            onDismissRequest = { reportFor = null },
            title = { Text(tr("Linia ", "Line ", "") + line) },
            text = {
                Column {
                    kinds.forEach { (code, label) ->
                        Text(label, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Track).clickable {
                                reportFor = null
                                scope.launch {
                                    runCatching { Network.api.transitReport(TransitReportIn(line, code, selectedStop)) }
                                        .onSuccess { toast = it.detail; runCatching { Network.api.transitReports() }.onSuccess { r -> reports = r } }
                                        .onFailure { toast = it.userMessage() }
                                }
                            }.padding(12.dp))
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { reportFor = null }) { Text(tr("Renunță", "Cancel", "Mégse"), color = TextMuted) } },
        )
    }

    showTicket?.let { t ->
        AlertDialog(
            onDismissRequest = { showTicket = null },
            title = { Text(t.label) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    remember(t.code) { qr("CityConnect Arad DEMO ${t.code} ${t.type} ${t.validUntil}", 520) }?.let {
                        Image(it.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(220.dp))
                    }
                    Text(t.code, color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(tr("Valabil până la ", "Valid until ", "Érvényes: ") + t.validUntil.take(16).replace("T", " ") + " (UTC)", color = TextMuted, fontSize = 12.sp)
                    Text(tr("DEMONSTRATIV · nu e valabil în tramvai", "DEMO · not valid on board", "BEMUTATÓ"), color = Pink, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showTicket = null }) { Text("OK") } },
        )
    }
}

/** Codul QR al biletului (ZXing). */
private fun qr(text: String, size: Int): Bitmap? = runCatching {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        val dark = Ink.toArgb()
        for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (m[x, y]) dark else android.graphics.Color.WHITE)
    }
}.getOrNull()

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel)
        .border(1.dp, Track, RoundedCornerShape(20.dp)).padding(14.dp)) { content() }
}

@Suppress("unused") private val keep = listOf(Lilac, CircleShape)
