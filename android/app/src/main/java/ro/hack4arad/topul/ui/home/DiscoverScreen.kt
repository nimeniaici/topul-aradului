package ro.hack4arad.topul.ui.home

import ro.hack4arad.topul.ui.theme.t
import ro.hack4arad.topul.ui.theme.Scrim
import ro.hack4arad.topul.ui.theme.Ink
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.ViewInAr
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ro.hack4arad.topul.data.DiscoverPlace
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.ui.components.ErrorState
import ro.hack4arad.topul.ui.components.Loading
import ro.hack4arad.topul.ui.components.PlacePhoto
import ro.hack4arad.topul.ui.theme.Electric
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.Night
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.util.userMessage

/** „Descoperă Aradul”: locurile cu poze vechi (trecut vs prezent), ghid audio și tur 3D. */
@Composable
fun DiscoverScreen(onBack: () -> Unit, onPlace: (Int) -> Unit) {
    var items by remember { mutableStateOf<List<DiscoverPlace>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        runCatching { Network.api.discover() }.onSuccess { items = it; error = null }.onFailure { error = it.userMessage() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Înapoi", tint = Ink) }
            Column {
                Text(t("Descoperă Aradul"), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(t("Trecut vs prezent · ghid audio · tur 3D"), color = TextMuted, fontSize = 13.sp)
            }
        }
        val list = items
        when {
            list == null && error != null -> ErrorState(error!!, onRetry = { reload++ })
            list == null -> Loading()
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { p -> DiscoverCard(p) { onPlace(p.id) } }
            }
        }
    }
}

@Composable
private fun DiscoverCard(p: DiscoverPlace, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth().height(170.dp).clip(shape).clickable(onClick = onClick)) {
        // dacă avem poză veche, o arătăm pe ea (alb-negru): asta e „momentul wow”
        val old = p.oldPhoto
        if (old != null) {
            AsyncImage(Network.fullUrl(old.url), null, contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }), modifier = Modifier.fillMaxSize())
        } else {
            PlacePhoto(p.id, p.name, p.lat, p.lng, p.category, p.photoUrl, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Scrim.copy(alpha = 0.92f))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(p.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (old != null) Badge(Icons.Rounded.HistoryEdu, "${old.year} vs azi", Gold)
                if (p.hasStory) Badge(Icons.Rounded.Headphones, t("Ghid audio"), Mint)
                if (p.has3d) Badge(Icons.Rounded.ViewInAr, t("Tur 3D"), Electric)
            }
        }
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.22f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(" $text", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
