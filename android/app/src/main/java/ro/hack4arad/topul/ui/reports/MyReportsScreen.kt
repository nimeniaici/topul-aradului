package ro.hack4arad.topul.ui.reports

import ro.hack4arad.topul.ui.theme.Ink
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.filter
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Report
import ro.hack4arad.topul.ui.components.ErrorState
import ro.hack4arad.topul.ui.components.Loading
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.util.userMessage

/** Sesizările mele: ce am semnalat, ce status are și ce a răspuns Primăria (se actualizează live). */
@Composable
fun MyReportsScreen(onBack: () -> Unit, onNew: () -> Unit) {
    var items by remember { mutableStateOf<List<Report>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        runCatching { Network.api.myReports() }.onSuccess { items = it; error = null }.onFailure { error = it.userMessage() }
    }
    // când Primăria schimbă statusul, lista se reîncarcă singură
    LaunchedEffect(Unit) {
        Network.live.events.filter { it.type == "report_status" && it.userId == Network.session.userId }.collect { reload++ }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Înapoi", tint = Ink) }
            Text("Sesizările mele", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        }
        val list = items
        when {
            list == null && error != null -> ErrorState(error!!, onRetry = { reload++ })
            list == null -> Loading()
            list.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("Încă n-ai semnalat nimic.", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Ai văzut o groapă, un copac căzut sau un semafor stricat? Spune-i Primăriei.", color = TextMuted)
                Spacer(Modifier.height(16.dp))
                NewButton(onNew)
            }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { NewButton(onNew) }
                items(list, key = { it.id }) { r -> ReportCard(r) }
            }
        }
    }
}

@Composable
private fun NewButton(onNew: () -> Unit) {
    Button(
        onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Icon(Icons.Rounded.Add, null); Text("  Semnalează o problemă nouă", fontWeight = FontWeight.Bold) }
}

@Composable
private fun ReportCard(r: Report) {
    val kind = reportKind(r.category)
    val color = statusColor(r.status)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(kind.icon, null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.categoryLabel, color = Ink, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(r.createdAt.take(10), color = TextMuted, fontSize = 12.sp)
            }
            Text(
                r.statusLabel ?: r.status, color = color, fontWeight = FontWeight.Black, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        if (r.description.isNotBlank()) Text(r.description, color = Lilac, modifier = Modifier.padding(top = 8.dp))
        Network.fullUrl(r.photoUrl)?.let {
            AsyncImage(it, null, contentScale = ContentScale.Crop,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)))
        }
        // pașii: trimisă -> preluată -> rezolvată
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val step = when (r.status) { "rezolvat" -> 3; "in_lucru" -> 2; "respins" -> 0; else -> 1 }
            listOf("Trimisă", "Preluată", "Rezolvată").forEachIndexed { i, label ->
                val done = step > i
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (done) color else TextMuted.copy(alpha = 0.4f)))
                Text(" $label", color = if (done) Ink else TextMuted, fontSize = 12.sp)
                if (i < 2) Box(Modifier.padding(horizontal = 6.dp).width(18.dp).height(2.dp).background(if (step > i + 1) color else TextMuted.copy(alpha = 0.3f)))
            }
        }
        r.adminNote?.let {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Gold.copy(alpha = 0.12f)).padding(10.dp)) {
                Icon(Icons.Rounded.AccountBalance, null, tint = Gold, modifier = Modifier.size(18.dp))
                Text("  Primăria: $it", color = Gold, fontSize = 13.sp)
            }
        }
    }
}
