package ro.hack4arad.topul.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Deck
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.material.icons.rounded.TakeoutDining
import androidx.compose.material3.Icon
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import ro.hack4arad.topul.data.Category
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Place
import ro.hack4arad.topul.data.PlacePhotos
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Electric
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Leaf
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.Violet

/** Iconița (Material Symbols) a fiecărei categorii, folosită acolo unde nu avem încă poză. */
fun categoryIcon(code: String): ImageVector = when (code) {
    "fastfood" -> Icons.Rounded.TakeoutDining
    "restaurant" -> Icons.Rounded.Restaurant
    "cafenea" -> Icons.Rounded.LocalCafe
    "cofetarie" -> Icons.Rounded.Cake
    "gelaterie" -> Icons.Rounded.Icecream
    "terasa" -> Icons.Rounded.Deck
    "bar" -> Icons.Rounded.SportsBar
    "obiectiv" -> Icons.Rounded.AccountBalance
    "manastire" -> Icons.Rounded.Church
    "natura" -> Icons.Rounded.Park
    "mall" -> Icons.Rounded.LocalMall
    "eveniment" -> Icons.Rounded.Celebration
    else -> Icons.Rounded.Place
}

/** Culoarea neon a fiecărei categorii (markere, carduri, chenare). */
fun categoryColor(code: String): Color = when (code) {
    "fastfood" -> Color(0xFFFF8A3D)
    "restaurant" -> Pink
    "cafenea" -> Color(0xFFC08A5B)
    "cofetarie" -> Color(0xFFFF9ECF)
    "gelaterie" -> Color(0xFF7FE3FF)
    "terasa" -> Mint
    "bar" -> Violet
    "obiectiv" -> Electric
    "manastire" -> Lilac
    "natura" -> Leaf
    "mall" -> Gold
    "eveniment" -> Gold
    else -> Violet
}

/**
 * Poza reală a locului: întâi cea din aplicație (dacă există), altfel cea de pe Google Maps,
 * cerută abia când locul apare pe ecran (lazy loading). Până se încarcă: gradient + iconița categoriei.
 */
@Composable
fun PlacePhoto(
    id: Int, name: String, lat: Double, lng: Double, category: String, photoUrl: String?,
    modifier: Modifier = Modifier, iconSize: Dp = 28.dp,
) {
    val own = Network.fullUrl(photoUrl)
    val google by produceState<String?>(initialValue = null, id) {
        if (own == null) value = PlacePhotos.url(id, name, lat, lng)
    }
    val url = own ?: google
    val color = categoryColor(category)
    Box(
        modifier.background(Brush.linearGradient(listOf(color.copy(alpha = 0.55f), Panel))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(category), contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(iconSize))
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(250).build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun PlacePhoto(p: Place, modifier: Modifier = Modifier, iconSize: Dp = 28.dp) =
    PlacePhoto(p.id, p.name, p.lat, p.lng, p.category, p.photoUrl, modifier, iconSize)

@Composable
fun CategoryChips(
    categories: List<Category>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "Toate",
) {
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = Panel,
        labelColor = Color.White,
        iconColor = TextMuted,
        selectedContainerColor = Gold,
        selectedLabelColor = GoldDark,
        selectedLeadingIconColor = GoldDark,
    )
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null, onClick = { onSelect(null) },
                label = { Text(allLabel, fontWeight = FontWeight.Bold) },
                colors = colors, border = null, shape = RoundedCornerShape(50),
            )
        }
        items(categories, key = { it.code }) { c ->
            FilterChip(
                selected = selected == c.code, onClick = { onSelect(c.code) },
                label = { Text(c.label, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(categoryIcon(c.code), contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = if (selected == c.code) GoldDark else categoryColor(c.code))
                },
                colors = colors, border = null, shape = RoundedCornerShape(50),
            )
        }
    }
}

/** Bară de progres rotunjită, animată (XP, misiuni). */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Gold, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(color))
    }
}

/** Card de panou, cu chenar colorat opțional. */
@Composable
fun GamePanel(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(Panel)
            .then(if (borderColor != null) Modifier.border(1.5.dp, borderColor, shape) else Modifier)
            .padding(14.dp),
    ) { content() }
}

/** Etichetă mică colorată ("Tron liber", "Nivel 4"). */
@Composable
fun Pill(text: String, background: Color, textColor: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Emoji-urile și culorile de avatar (aceleași ca pe server). */
val AVATAR_EMOJIS = listOf(
    "😎", "🦊", "🐻", "🐼", "🦁", "🐯", "🐸", "🐙", "🦄", "🐝", "🍕", "☕",
    "🚲", "🎸", "⚽", "🎮", "👑", "🌟", "🔥", "🌊", "🍷", "🏰", "🦉", "🚀",
)
val AVATAR_COLORS = listOf("violet", "pink", "mint", "blue", "gold", "green")

fun avatarColor(key: String?): Color = when (key) {
    "pink" -> Pink
    "mint" -> Mint
    "blue" -> Electric
    "gold" -> Gold
    "green" -> Leaf
    else -> Violet
}

/** Avatar rotund: emoji-ul ales (dacă există) sau inițialele, pe culoarea aleasă. */
@Composable
fun Avatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Color = Gold,
    emoji: String? = null,
    colorKey: String? = null,
    photoUrl: String? = null,
) {
    val initials = name.split(" ").filter { it.isNotBlank() && it.first().isLetter() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        modifier
            .size(size)
            .border(3.dp, ring, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(avatarColor(colorKey))
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val photo = Network.fullUrl(photoUrl)
        if (photo != null) AsyncImage(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (emoji != null) Text(emoji, fontSize = (size.value / 2.2).sp)
        else Text(initials, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value / 2.6).sp)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
        Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp))
        Button(onClick = onRetry) { Text("Încearcă din nou") }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}
