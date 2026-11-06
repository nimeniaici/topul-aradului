package ro.hack4arad.topul.ui.duels

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
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
import ro.hack4arad.topul.ui.components.PlacePhoto
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
    var streak by mutableIntStateOf(0)
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
                    message = "${r.winner.name} +$delta puncte  ·  +${r.xpGained} XP" +
                        r.newBadges.joinToString("") { "  ·  Insignă nouă: ${it.title}" }
                }
                .onFailure { message = it.userMessage() }
            delay(1000) // lăsăm animația să se vadă
            next()
        }
    }
}

@Composable
fun DuelScreen(vm: DuelViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        CategoryChips(vm.categories, vm.category, vm::pick, allLabel = "Aleatoriu")
        val duel = vm.duel
        when {
            duel == null && vm.error != null -> ErrorState(vm.error!!, onRetry = vm::next)
            duel == null -> Loading()
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DUEL · ${duel.categoryLabel.uppercase()}", color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                        Text("Care e mai tare?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    if (vm.streak > 0) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FF8A3D)).padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color(0xFFFF8A3D), modifier = Modifier.size(16.dp))
                            Text(" ${vm.streak}", color = Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ecran împărțit: stânga contra dreapta, ca un meci
                key(duel.a.id, duel.b.id) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DuelHalf(duel.a, vm.winnerId, fromLeft = true) { vm.vote(duel.a, duel.b) }
                            DuelHalf(duel.b, vm.winnerId, fromLeft = false) { vm.vote(duel.b, duel.a) }
                        }
                        VsBadge(Modifier.align(Alignment.Center))
                    }
                }

                Text(
                    vm.message ?: "Atinge partea pe care o preferi",
                    color = if (vm.message != null) Mint else TextMuted,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    maxLines = 2,
                )
                TextButton(onClick = vm::next, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Rounded.SkipNext, null, tint = Lilac)
                    Text(" Sari peste", color = Lilac)
                }
            }
        }
    }
}

@Composable
private fun VsBadge(modifier: Modifier) {
    // pulsează ușor, ca să atragă privirea
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { pulse = !pulse; delay(900) } }
    val scale by animateFloatAsState(if (pulse) 1.08f else 1f, tween(900), label = "vs")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .size(66.dp).clip(CircleShape).background(Gold).border(5.dp, Night, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("VS", color = GoldDark, fontWeight = FontWeight.Black, fontSize = 22.sp) }
}

@Composable
private fun RowScope.DuelHalf(place: Place, winnerId: Int?, fromLeft: Boolean, onClick: () -> Unit) {
    val won = winnerId == place.id
    val lost = winnerId != null && !won
    // intrare din lateral; la vot, câștigătorul ocupă mai mult loc, pierzătorul se stinge
    val enter = remember { Animatable(if (fromLeft) -500f else 500f) }
    LaunchedEffect(Unit) { enter.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) }
    val weight by animateFloatAsState(if (won) 1.6f else if (lost) 0.6f else 1f, spring(dampingRatio = 0.8f), label = "w")
    val alpha by animateFloatAsState(if (lost) 0.35f else 1f, tween(350), label = "a")
    val color = categoryColor(place.category)

    Box(
        Modifier.weight(weight).fillMaxHeight()
            .graphicsLayer { translationX = enter.value; this.alpha = alpha }
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, if (won) Gold else color.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            .clickable(enabled = winnerId == null, onClick = onClick),
    ) {
        PlacePhoto(place, Modifier.fillMaxSize(), iconSize = 56.dp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Night.copy(alpha = 0.97f))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
            Text(place.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, tint = Gold, modifier = Modifier.size(14.dp))
                Text(" ${place.rating.roundToInt()}", color = Lilac, fontSize = 13.sp)
            }
        }
        if (won) {
            Icon(
                Icons.Rounded.EmojiEvents, null, tint = Gold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(44.dp),
            )
        }
    }
}
