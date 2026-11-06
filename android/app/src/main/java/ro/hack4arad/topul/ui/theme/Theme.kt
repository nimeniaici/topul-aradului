package ro.hack4arad.topul.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// „Mod joc”: noapte albastră + auriu + accente neon
val Night = Color(0xFF0F1226)
val NightDeep = Color(0xFF0A0C1C)
val Panel = Color(0xFF1B2045)
val PanelHigh = Color(0xFF262C5C)
val Track = Color(0xFF2D3366)
val Gold = Color(0xFFFFD23F)
val GoldDark = Color(0xFF412402)
val Pink = Color(0xFFFF3D81)
val Mint = Color(0xFF2EE6A6)
val MintDark = Color(0xFF04342C)
val Electric = Color(0xFF4D7CFF)
val Violet = Color(0xFF7C5CFF)
val VioletDeep = Color(0xFF3A1F6E)
val Lilac = Color(0xFFE6DCFF)
val Leaf = Color(0xFF39D353)
val TextMuted = Color(0xFF9AA0D0)

private val GameColors = darkColorScheme(
    primary = Gold,
    onPrimary = GoldDark,
    primaryContainer = PanelHigh,
    onPrimaryContainer = Gold,
    secondary = Pink,
    onSecondary = Color.White,
    secondaryContainer = VioletDeep,
    onSecondaryContainer = Lilac,
    tertiary = Mint,
    onTertiary = MintDark,
    background = Night,
    onBackground = Color.White,
    surface = Night,
    onSurface = Color.White,
    surfaceVariant = Panel,
    onSurfaceVariant = TextMuted,
    surfaceContainerLowest = NightDeep,
    surfaceContainerLow = Panel,
    surfaceContainer = NightDeep,
    surfaceContainerHigh = Panel,
    surfaceContainerHighest = Panel,
    inverseSurface = PanelHigh, // notificări (snackbar)
    inverseOnSurface = Color.White,
    inversePrimary = Gold,
    outline = Track,
    outlineVariant = Track,
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF4A1520),
    onErrorContainer = Color(0xFFFFD9DC),
)

private val GameType = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    )
}

/** Aplicația e mereu în modul joc (întunecat), indiferent de tema telefonului. */
@Composable
fun TopulTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GameColors, typography = GameType, content = content)
}
