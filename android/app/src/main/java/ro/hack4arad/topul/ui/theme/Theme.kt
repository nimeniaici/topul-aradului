package ro.hack4arad.topul.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * O paletă = o singură familie de culori (nuanțe vecine), de la închis la deschis,
 * pe un fundal deschis cu gradient circular (alb în centru, nuanță la margini).
 */
data class AppPalette(
    val id: Int,
    val name: String,
    val deep: Color,   // cea mai închisă: text, bara de jos, carduri „hero”
    val a1: Color,     // închis: alerte, butoane importante
    val a2: Color,     // mediu: accent principal (butoane)
    val a3: Color,     // mediu-deschis: evidențieri
    val a4: Color,     // foarte deschis: chips, borduri, fundaluri subtile
    val center: Color, // centrul fundalului
    val edge: Color,   // marginea fundalului (gradient circular)
)

val PALETTES = listOf(
    AppPalette(2, "Amurg violet", Color(0xFF1B1240), Color(0xFF4A2C99), Color(0xFF6C4BD1), Color(0xFF9C7FEA), Color(0xFFE3D7FF), Color.White, Color(0xFFD9D3EA)),
    AppPalette(3, "Mureș", Color(0xFF0B2E33), Color(0xFF145C66), Color(0xFF1F8A8A), Color(0xFF5DB7A4), Color(0xFFB8E0D2), Color.White, Color(0xFFCFDDDA)),
    AppPalette(4, "Cărămidă și nisip", Color(0xFF3A1E12), Color(0xFF9C3D22), Color(0xFFC2622D), Color(0xFFE0A04A), Color(0xFFF3D3A2), Color(0xFFFFFDF8), Color(0xFFE9DCC8)),
    AppPalette(5, "Trandafir și prună", Color(0xFF3A1230), Color(0xFF7A2457), Color(0xFFA8407A), Color(0xFFD67BA3), Color(0xFFF6D3E0), Color.White, Color(0xFFEAD7DF)),
    AppPalette(6, "Argint pur", Color(0xFF0E0E40), Color(0xFF2A2D5C), Color(0xFF4B4F7A), Color(0xFF8A8DA6), Color(0xFFD5D7E0), Color.White, Color(0xFFA6A6A6)),
)
const val DEFAULT_PALETTE = 6

/** Setările de aspect (paleta), ținute minte pe telefon. */
object ThemeState {
    var palette by mutableStateOf(PALETTES.first { it.id == DEFAULT_PALETTE })
        private set

    fun init(context: Context) {
        val id = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("palette", DEFAULT_PALETTE)
        palette = PALETTES.firstOrNull { it.id == id } ?: palette
    }

    fun select(context: Context, id: Int) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("palette", id).apply()
        palette = PALETTES.firstOrNull { it.id == id } ?: palette
    }
}

private val P get() = ThemeState.palette

// Numele vechi rămân (sunt folosite peste tot), dar acum vin din paleta aleasă.
val Night: Color get() = P.center            // fundalul ecranelor
val NightDeep: Color get() = P.deep          // bara de jos, panouri închise
val Panel: Color get() = Color.White         // carduri
val PanelHigh: Color get() = P.a4
val Track: Color get() = P.a4
val Gold: Color get() = P.a2                 // accentul principal (butoane, evidențieri)
val GoldDark: Color get() = Color.White      // text pe accent
val Pink: Color get() = P.a1                 // alerte, sesizări
val Mint: Color get() = P.a3
val MintDark: Color get() = P.deep
val Electric: Color get() = P.a2
val Violet: Color get() = P.a1
val VioletDeep: Color get() = P.deep
val Lilac: Color get() = P.deep.copy(alpha = 0.72f)   // text secundar
val Leaf: Color get() = P.a3
val TextMuted: Color get() = P.deep.copy(alpha = 0.5f)
val Ink: Color get() = P.deep                // textul principal
val Scrim: Color get() = P.deep              // umbră peste poze (text alb deasupra)
val BackgroundBrush: Brush get() = Brush.radialGradient(0.0f to P.center, 0.5f to P.center, 1.0f to P.edge)

private val AppType = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    )
}

@Composable
fun TopulTheme(content: @Composable () -> Unit) {
    val p = ThemeState.palette
    val colors = lightColorScheme(
        primary = p.a2, onPrimary = Color.White,
        primaryContainer = p.a4, onPrimaryContainer = p.deep,
        secondary = p.a1, onSecondary = Color.White,
        secondaryContainer = p.a4, onSecondaryContainer = p.deep,
        tertiary = p.a3, onTertiary = p.deep,
        background = p.center, onBackground = p.deep,
        surface = p.center, onSurface = p.deep,
        surfaceVariant = Color.White, onSurfaceVariant = p.deep.copy(alpha = 0.6f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
        surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = p.a4,
        inverseSurface = p.deep, inverseOnSurface = Color.White, inversePrimary = p.a4,
        outline = p.a4, outlineVariant = p.a4,
        error = Color(0xFFB3261E),
    )
    MaterialTheme(colorScheme = colors, typography = AppType) {
        // fundalul „argintiu”: alb în centru, nuanța paletei spre margini
        Box(Modifier.fillMaxSize().background(BackgroundBrush)) { content() }
    }
}
