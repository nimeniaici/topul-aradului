package ro.hack4arad.topul.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.CheckinResult
import ro.hack4arad.topul.ui.theme.Electric
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.Violet
import kotlin.math.sin
import kotlin.random.Random

private val confettiColors = listOf(Gold, Pink, Mint, Electric, Violet, Color.White)

private class Piece(
    val x: Float, val delay: Float, val speed: Float, val drift: Float,
    val size: Float, val spin: Float, val color: Color,
)

/** Ploaie de confetti care cade o dată, pe tot ecranul. */
@Composable
fun Confetti(modifier: Modifier = Modifier, count: Int = 120) {
    val pieces = remember {
        List(count) {
            Piece(
                x = Random.nextFloat(), delay = Random.nextFloat() * 0.35f,
                speed = 0.7f + Random.nextFloat() * 0.6f, drift = Random.nextFloat() * 2f - 1f,
                size = 6f + Random.nextFloat() * 10f, spin = Random.nextFloat() * 720f - 360f,
                color = confettiColors.random(),
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2800, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val p = t.value
        pieces.forEach { c ->
            val local = ((p - c.delay) / (1f - c.delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val y = -40f + local * c.speed * size.height * 1.3f
            val x = c.x * size.width + sin(local * 10f + c.x * 6f) * 40f * c.drift
            rotate(c.spin * local, pivot = Offset(x, y)) {
                drawRect(c.color.copy(alpha = 1f - local * 0.5f), Offset(x, y), Size(c.size, c.size * 0.55f))
            }
        }
    }
}

/**
 * Ecranul de sărbătoare după check-in: confetti, coroana care cade (dacă devii Primar),
 * XP-ul care urcă numărând și insignele noi.
 */
@Composable
fun CheckinCelebration(result: CheckinResult, placeName: String, onDismiss: () -> Unit) {
    var xpShown by remember { mutableIntStateOf(0) }
    val xp by animateIntAsState(xpShown, tween(1200), label = "xp")
    val drop = remember { Animatable(if (result.becameMayor) -400f else 0f) }
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { drop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessVeryLow)) }
        xpShown = result.xpGained
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE60A0C1C))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Confetti()
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (result.becameMayor) Icons.Rounded.WorkspacePremium else Icons.Rounded.Place,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(110.dp).graphicsLayer {
                    translationY = drop.value
                    scaleX = pop.value
                    scaleY = pop.value
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (result.becameMayor) "Ești noul Primar!" else "Check-in reușit!",
                color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            )
            Text(placeName, color = Lilac, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Text("+$xp XP", color = Gold, fontSize = 56.sp, fontWeight = FontWeight.Black)
            Text("Nivel ${result.level} · ${result.totalXp} XP în total", color = Lilac)
            result.newBadges.forEach { b ->
                Spacer(Modifier.height(12.dp))
                Pill("Insignă nouă: ${b.title}", Violet, Color.White)
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(if (result.becameMayor) "Stăpânesc orașul" else "Mai departe")
            }
        }
    }
}
