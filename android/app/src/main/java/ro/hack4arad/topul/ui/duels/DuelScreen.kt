package ro.hack4arad.topul.ui.duels

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.Category
import ro.hack4arad.topul.data.Duel
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Place
import ro.hack4arad.topul.data.VoteIn
import ro.hack4arad.topul.ui.components.CategoryChips
import ro.hack4arad.topul.ui.components.ErrorState
import ro.hack4arad.topul.ui.components.Loading
import ro.hack4arad.topul.ui.components.PlaceImage
import ro.hack4arad.topul.ui.components.categoryColor
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.Night
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.util.userMessage
import kotlin.math.roundToInt

class DuelViewModel : ViewModel() {
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var category by mutableStateOf<String?>(null)
        private set
    var duel by mutableStateOf<Duel?>(null)
        private set
    /** id-ul locului ales, cât timp rulează animația de victorie */
    var winnerId by mutableStateOf<Int?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var streak by mutableStateOf(0)
        private set

    init {
        viewModelScope.launch { runCatching { categories = Network.api.duelCategories() } }
        next()
    }

    fun pick(cat: String?) {
        category = cat
        message = null
        next()
    }

    fun next() {
        viewModelScope.launch {
            runCatching { Network.api.nextDuel(category) }
                .onSuccess { duel = it; winnerId = null; error = null }
                .onFailure { error = it.userMessage() }
        }
    }

    fun vote(winner: Place, loser: Place) {
        if (winnerId != null) return
        winnerId = winner.id
        viewModelScope.launch {
            runCatching { Network.api.vote(VoteIn(winner.id, loser.id)) }
                .onSuccess { r ->
                    streak++
                    val delta = (r.winner.rating - winner.rating).roundToInt()
                    message = "${r.winner.name} +$delta ★   ·   +${r.xpGained} XP" +
                        r.newBadges.joinToString("") { "\n🏅 Insignă nouă: ${it.title}" }
                }
                .onFailure { message = it.userMessage() }
            delay(900) // lăsăm animația să se vadă
            next()
        }
    }
}

@Composable
fun DuelScreen(vm: DuelViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        CategoryChips(vm.categories, vm.category, vm::pick, allLabel = "🎲 Aleatoriu")
        val duel = vm.duel
        when {
            duel == null && vm.error != null -> ErrorState(vm.error!!, onRetry = vm::next)
            duel == null -> Loading()
            else -> Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "DUEL · ${duel.categoryLabel.uppercase()}",
                    color = Gold, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp,
                )
                Text(
                    "Care e mai tare?",
                    color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                )
                if (vm.streak > 0) Text("🔥 Serie: ${vm.streak} voturi", color = TextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))

                // key: animațiile pornesc de la zero la fiecare duel nou
                key(duel.a.id, duel.b.id) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxSize()) {
                            DuelCard(duel.a, vm.winnerId, fromLeft = true) { vm.vote(duel.a, duel.b) }
                            Spacer(Modifier.height(14.dp))
                            DuelCard(duel.b, vm.winnerId, fromLeft = false) { vm.vote(duel.b, duel.a) }
                        }
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Gold)
                                .border(4.dp, Night, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("VS", color = GoldDark, fontWeight = FontWeight.Black, fontSize = 22.sp) }
                    }
                }

                Text(
                    vm.message ?: "Atinge-ți favoritul",
                    color = if (vm.message != null) Mint else TextMuted,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                TextButton(onClick = vm::next) { Text("Nu știu, sari peste →", color = Lilac) }
            }
        }
    }
}

@Composable
private fun ColumnScope.DuelCard(place: Place, winnerId: Int?, fromLeft: Boolean, onClick: () -> Unit) {
    // intrare din lateral + reacție la vot (câștigătorul crește, pierzătorul se stinge)
    val enter = remember { Animatable(if (fromLeft) -600f else 600f) }
    val scale = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) { enter.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(winnerId) {
        when (winnerId) {
            null -> Unit
            place.id -> scale.animateTo(1.05f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            else -> {
                launch { fade.animateTo(0.25f, tween(400)) }
                scale.animateTo(0.92f, tween(400))
            }
        }
    }
    val color = categoryColor(place.category)
    val won = winnerId == place.id
    Box(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .graphicsLayer {
                translationX = enter.value
                scaleX = scale.value
                scaleY = scale.value
                alpha = fade.value
            }
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, if (won) Gold else color, RoundedCornerShape(24.dp))
            .clickable(enabled = winnerId == null, onClick = onClick),
    ) {
        PlaceImage(place.photoUrl, place.category, place.id, Modifier.fillMaxSize(), emojiSize = 64)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Night.copy(alpha = 0.95f))))
                .padding(16.dp),
        ) {
            Text(place.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("★ ${place.rating.roundToInt()} · ${place.checkinCount} check-in-uri", color = Lilac)
        }
        if (won) {
            Text(
                "👑", fontSize = 44.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).offset(y = (-4).dp),
            )
        }
    }
}
