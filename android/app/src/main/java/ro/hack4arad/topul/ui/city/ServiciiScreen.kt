package ro.hack4arad.topul.ui.city

import ro.hack4arad.topul.util.runCatching
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.LocalPostOffice
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.ServiceItem
import ro.hack4arad.topul.data.ServiceType
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.OnInk
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.util.currentFix
import ro.hack4arad.topul.util.hasLocationPermission
import ro.hack4arad.topul.util.userMessage

fun serviceIcon(type: String): ImageVector = when (type) {
    "farmacie" -> Icons.Rounded.LocalPharmacy
    "spital" -> Icons.Rounded.LocalHospital
    "medic" -> Icons.Rounded.MedicalServices
    "scoala" -> Icons.Rounded.School
    "politie" -> Icons.Rounded.LocalPolice
    "posta" -> Icons.Rounded.LocalPostOffice
    "banca" -> Icons.Rounded.LocalAtm
    else -> Icons.Rounded.AccountBalance
}

/** Servicii locale (OpenStreetMap): farmacii, spitale, școli, poliție, poștă, bancomate, instituții – cele mai apropiate primele. */
@Composable
fun ServiciiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf("farmacie") }
    var items by remember { mutableStateOf<List<ServiceItem>>(emptyList()) }
    var types by remember { mutableStateOf<List<ServiceType>>(emptyList()) }
    var fix by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) runCatching { currentFix(context) }.getOrNull()?.let { fix = it.lat to it.lng }
    }
    LaunchedEffect(type, fix) {
        runCatching { Network.api.servicii(type = type, lat = fix?.first ?: 46.1745, lng = fix?.second ?: 21.3185) }
            .onSuccess { items = it.items; types = it.types; error = null }.onFailure { error = it.userMessage() }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Înapoi", "Back", "Vissza"), tint = Ink) }
                Column(Modifier.weight(1f)) {
                    Text(tr("Servicii locale", "Local services", "Helyi szolgáltatások"), color = Ink, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(tr("Cele mai apropiate de tine", "Closest to you first", "A legközelebbiek"), color = TextMuted, fontSize = 13.sp)
                }
            }
            if (type == "spital" || type == "politie") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFFB00020))
                    .clickable { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Call, null, tint = androidx.compose.ui.graphics.Color.White)
                    Text("  " + tr("Urgență? Sună la 112", "Emergency? Call 112", "Vészhelyzet? 112"), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { t ->
                    val on = t.code == type
                    Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) Ink else Panel).border(1.dp, Track, RoundedCornerShape(50))
                        .clickable { type = t.code }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(serviceIcon(t.code), null, tint = if (on) OnInk else Ink, modifier = Modifier.size(16.dp))
                        Text(" ${t.label} (${t.count})", color = if (on) OnInk else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            error?.let { Text(it, color = Pink, modifier = Modifier.padding(16.dp)) }
        }
        items(items, key = { it.id }) { s -> ServiceRow(s) }
        item { Text(tr("Date: © OpenStreetMap. Verifică programul înainte să pleci.", "Data: © OpenStreetMap. Check opening hours before going.", "Adatok: © OpenStreetMap."),
            color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(16.dp)) }
    }
}

@Composable
private fun ServiceRow(s: ServiceItem) {
    val context = LocalContext.current
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
        .border(1.dp, Track, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Track), contentAlignment = Alignment.Center) {
            Icon(serviceIcon(s.type), null, tint = Ink)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(s.name, color = Ink, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(s.kind.takeIf { it.isNotBlank() && it != s.name }, s.address.takeIf { it.isNotBlank() },
                s.distanceM?.let { if (it < 1000) "$it m" else String.format("%.1f km", it / 1000.0) }).joinToString(" · "),
                color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                s.open247 -> Text(tr("Deschis non-stop", "Open 24/7", "Éjjel-nappal"), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                s.hours.isNotBlank() -> Text(s.hours, color = Gold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (s.emergency && s.type == "spital") Text(tr("Are primiri urgențe", "Has an emergency room", "Sürgősségi"), color = Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (s.phone.isNotBlank()) IconButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + s.phone.replace(" ", ""))))
        }) { Icon(Icons.Rounded.Call, tr("Sună", "Call", "Hívás"), tint = Ink) }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Gold).clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${s.lat},${s.lng}")))
        }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Directions, tr("Cum ajung", "Directions", "Útvonal"), tint = GoldDark) }
    }
}
