package ro.hack4arad.topul.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.Category
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Place
import ro.hack4arad.topul.data.UserRank
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.components.CategoryChips
import ro.hack4arad.topul.ui.components.ErrorState
import ro.hack4arad.topul.ui.components.PlaceImage
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Night
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.PanelHigh
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.util.userMessage
import kotlin.math.roundToInt

class LeaderboardViewModel : ViewModel() {
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var category by mutableStateOf<String?>(null)
        private set
    var places by mutableStateOf<List<Place>>(emptyList())
        private set
    var users by mutableStateOf<List<UserRank>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { runCatching { categories = Network.api.duelCategories() } }
        refresh()
    }

    fun pick(cat: String?) {
        category = cat
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                places = Network.api.topPlaces(category)
                users = Network.api.topUsers()
            }.onSuccess { error = null }.onFailure { error = it.userMessage() }
        }
    }
}

private val Silver = Color(0xFFC9D1E8)
private val Bronze = Color(0xFFE09B5A)

private class PodiumEntry(val title: String, val subtitle: String, val score: String, val visual: @Composable () -> Unit, val onClick: () -> Unit)

@Composable
fun LeaderboardScreen(onPlace: (Int) -> Unit, vm: LeaderboardViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Night,
            contentColor = Color.White,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = Gold, height = 3.dp) },
            divider = {},
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0; vm.refresh() }, text = { Text("🏆 Locuri", fontWeight = FontWeight.Black) })
            Tab(selected = tab == 1, onClick = { tab = 1; vm.refresh() }, text = { Text("🧭 Arădeni", fontWeight = FontWeight.Black) })
        }
        if (vm.error != null) {
            ErrorState(vm.error!!, onRetry = vm::refresh)
            return@Column
        }
        if (tab == 0) {
            CategoryChips(vm.categories, vm.category, vm::pick, Modifier.padding(vertical = 10.dp))
            val podium = vm.places.take(3).map { p ->
                PodiumEntry(p.name, p.categoryLabel, "★ ${p.rating.roundToInt()}", {
                    PlaceImage(p.photoUrl, p.category, p.id, Modifier.size(56.dp).clip(CircleShape), emojiSize = 26)
                }) { onPlace(p.id) }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item { Podium(podium) }
                items(vm.places.drop(3), key = { it.id }) { p ->
                    RankRow(rank = vm.places.indexOf(p) + 1, onClick = { onPlace(p.id) }, score = "★ ${p.rating.roundToInt()}",
                        title = p.name, subtitle = p.categoryLabel + (p.mayorName?.let { " · 👑 $it" } ?: "")) {
                        PlaceImage(p.photoUrl, p.category, p.id, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), emojiSize = 22)
                    }
                }
            }
        } else {
            val me = Network.session.userId
            val podium = vm.users.take(3).map { u ->
                PodiumEntry(u.name, "Nivel ${u.level}", "${u.xp} XP", { Avatar(u.name, 56.dp, ring = if (u.id == me) Gold else Panel, emoji = u.avatarEmoji, colorKey = u.avatarColor) }) {}
            }
            LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
                item { Podium(podium) }
                items(vm.users.drop(3), key = { it.id }) { u ->
                    RankRow(rank = u.rank, onClick = {}, score = "${u.xp} XP",
                        title = u.name + if (u.id == me) " (tu)" else "", subtitle = "Nivel ${u.level}",
                        highlight = u.id == me) { Avatar(u.name, 44.dp, ring = if (u.id == me) Gold else Panel, emoji = u.avatarEmoji, colorKey = u.avatarColor) }
                }
            }
        }
    }
}

/** Podiumul: locul 2 stânga, 1 la mijloc (cel mai înalt), 3 dreapta. */
@Composable
private fun Podium(entries: List<PodiumEntry>) {
    if (entries.isEmpty()) return
    val order = listOfNotNull(entries.getOrNull(1)?.let { it to 2 }, entries[0] to 1, entries.getOrNull(2)?.let { it to 3 })
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        order.forEach { (e, rank) ->
            val (color, height) = when (rank) {
                1 -> Gold to 120.dp
                2 -> Silver to 88.dp
                else -> Bronze to 66.dp
            }
            PodiumColumn(e, rank, color, height, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PodiumColumn(e: PodiumEntry, rank: Int, color: Color, height: Dp, modifier: Modifier) {
    Column(modifier.clickable(onClick = e.onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        if (rank == 1) Text("👑", fontSize = 28.sp)
        e.visual()
        Spacer(Modifier.height(6.dp))
        Text(e.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(e.score, color = color, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(height)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(color),
            contentAlignment = Alignment.TopCenter,
        ) { Text("$rank", color = GoldDark, fontSize = 34.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun RankRow(
    rank: Int, onClick: () -> Unit, score: String, title: String, subtitle: String,
    highlight: Boolean = false, visual: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) PanelHigh else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#$rank", color = TextMuted, fontWeight = FontWeight.Black, modifier = Modifier.width(40.dp))
        visual()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(score, color = Gold, fontWeight = FontWeight.Black)
    }
}
