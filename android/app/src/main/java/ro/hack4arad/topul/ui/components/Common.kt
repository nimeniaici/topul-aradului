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
import ro.hack4arad.topul.data.Category
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.ui.theme.Electric
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Leaf
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.Violet

/** Emoji per categorie - arată bine și nu cere iconițe extra. */
fun categoryEmoji(code: String): String = when (code) {
    "pizza" -> "🍕"
    "burger" -> "🍔"
    "restaurant" -> "🍽️"
    "cafenea" -> "☕"
    "desert" -> "🍰"
    "terasa" -> "🌿"
    "bar" -> "🍸"
    "producator" -> "🍯"
    "obiectiv" -> "🏛️"
    "natura" -> "🌳"
    "eveniment" -> "🎉"
    else -> "📍"
}

/** Culoarea neon a fiecărei categorii (markere, carduri, chenare). */
fun categoryColor(code: String): Color = when (code) {
    "pizza", "burger", "restaurant", "desert" -> Pink
    "cafenea", "bar", "terasa" -> Mint
    "obiectiv" -> Electric
    "natura" -> Leaf
    "producator", "eveniment" -> Gold
    else -> Violet
}

/** Poza locului sau, dacă nu are, un pătrat în culoarea categoriei cu emoji-ul ei. */
@Composable
fun PlaceImage(photoUrl: String?, category: String, id: Int, modifier: Modifier = Modifier, emojiSize: Int = 40) {
    val url = Network.fullUrl(photoUrl)
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(
            modifier.background(categoryColor(category).copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) { Text(categoryEmoji(category), fontSize = emojiSize.sp) }
    }
}

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
        selectedContainerColor = Gold,
        selectedLabelColor = GoldDark,
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
                label = { Text("${categoryEmoji(c.code)} ${c.label}", fontWeight = FontWeight.Bold) },
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
        if (emoji != null) Text(emoji, fontSize = (size.value / 2.2).sp)
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
        Text("😵", fontSize = 48.sp)
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
