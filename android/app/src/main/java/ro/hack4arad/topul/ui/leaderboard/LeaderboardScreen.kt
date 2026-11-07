package ro.hack4arad.topul.ui.leaderboard

import ro.hack4arad.topul.ui.theme.NightDeep
import ro.hack4arad.topul.ui.theme.Scrim
import ro.hack4arad.topul.ui.theme.Ink
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.Category
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Place
import ro.hack4arad.topul.data.UserRank
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.components.CategoryChips
import ro.hack4arad.topul.ui.components.ErrorState
import ro.hack4arad.topul.ui.components.PlacePhoto
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.Night
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.PanelHigh
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Violet
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
    /** se încarcă următoarea pagină (lazy loading la derulare) */
    var loadingMore by mutableStateOf(false)
        private set
    private var placesEnd = false
    private var usersEnd = false
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch { runCatching { categories = Network.api.duelCategories() } }
        refresh()
    }

    fun pick(cat: String?) {
        category = cat
        refresh()
    }

    /** Prima pagină (20 de rezultate); restul se cer abia când derulezi spre final. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            runCatching {
                val p = Network.api.topPlaces(category, PAGE, 0)
                val u = Network.api.topUsers(PAGE, 0)
                places = p
                users = u
                placesEnd = p.size < PAGE
                usersEnd = u.size < PAGE
            }.onSuccess { error = null }.onFailure { error = it.userMessage() }
        }
    }

    fun loadMorePlaces() {
        if (loadingMore || placesEnd || places.isEmpty()) return
        loadingMore = true
        viewModelScope.launch {
            runCatching { Network.api.topPlaces(category, PAGE, places.size) }
                .onSuccess { page -> places = (places + page).distinctBy { it.id }; placesEnd = page.size < PAGE }
            loadingMore = false
        }
    }

    fun loadMoreUsers() {
        if (loadingMore || usersEnd || users.isEmpty()) return
        loadingMore = true
        viewModelScope.launch {
            runCatching { Network.api.topUsers(PAGE, users.size) }
                .onSuccess { page -> users = (users + page).distinctBy { it.id }; usersEnd = page.size < PAGE }
            loadingMore = false
        }
    }

    private companion object { const val PAGE = 20 }
}

/**
 * Săgețile de trend: comparăm locul din clasament cu cel de la vizita ta anterioară
 * (ținut minte pe telefon). Săgeată sus = a urcat, jos = a coborât, linie = la fel / nou.
 */
private class RankMemory(context: Context, scope: String) {
    private val prefs = context.getSharedPreferences("ranks_$scope", Context.MODE_PRIVATE)
    private val previous: Map<String, Int> = prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
    fun trend(id: Int, rank: Int): Int = previous[id.toString()]?.let { it - rank } ?: 0
    fun save(ranks: List<Pair<Int, Int>>) = prefs.edit().apply { ranks.forEach { (id, r) -> putInt(id.toString(), r) } }.apply()
}

@Composable
fun LeaderboardScreen(onPlace: (Int) -> Unit, onUser: (Int) -> Unit = {}, vm: LeaderboardViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Night,
            contentColor = Ink,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = Gold, height = 3.dp) },
            divider = {},
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0; vm.refresh() }, text = { Text("Locuri", fontWeight = FontWeight.Black) })
            Tab(selected = tab == 1, onClick = { tab = 1; vm.refresh() }, text = { Text("Arădeni", fontWeight = FontWeight.Black) })
        }
        if (vm.error != null) {
            ErrorState(vm.error!!, onRetry = vm::refresh)
            return@Column
        }
        if (tab == 0) {
            CategoryChips(vm.categories, vm.category, vm::pick, Modifier.padding(vertical = 10.dp))
            val memory = remember(vm.category) { RankMemory(context, "places_${vm.category ?: "toate"}") }
            LaunchedEffect(vm.places) {
                delay(1500)
                memory.save(vm.places.mapIndexed { i, p -> p.id to i + 1 })
            }
            val listState = rememberLazyListState()
            LoadMoreWhenNearEnd(listState, vm::loadMorePlaces)
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
                vm.places.firstOrNull()?.let { champ ->
                    item(key = "champ") { ChampionPlace(champ, vm.category == null) { onPlace(champ.id) } }
                }
                itemsIndexed(vm.places.drop(1), key = { _, p -> p.id }) { i, p ->
                    val rank = i + 2
                    RankRow(
                        rank = rank, trend = memory.trend(p.id, rank), onClick = { onPlace(p.id) },
                        title = p.name, subtitle = p.categoryLabel + " · ${p.checkinCount} check-in-uri",
                        score = p.rating.roundToInt().toString(),
                    ) { PlacePhoto(p, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), iconSize = 22.dp) }
                }
                item { LoadingMoreRow(vm.loadingMore) }
            }
        } else {
            val me = Network.session.userId
            val memory = remember { RankMemory(context, "users") }
            LaunchedEffect(vm.users) {
                delay(1500)
                memory.save(vm.users.map { it.id to it.rank })
            }
            val listState = rememberLazyListState()
            LoadMoreWhenNearEnd(listState, vm::loadMoreUsers)
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
                vm.users.firstOrNull()?.let { champ -> item(key = "champ") { ChampionUser(champ, champ.id == me) { onUser(champ.id) } } }
                itemsIndexed(vm.users.drop(1), key = { _, u -> u.id }) { _, u ->
                    RankRow(
                        rank = u.rank, trend = memory.trend(u.id, u.rank), onClick = { onUser(u.id) },
                        title = u.name + if (u.id == me) " (tu)" else "", subtitle = "Nivel ${u.level}",
                        score = "${u.xp} XP", highlight = u.id == me,
                    ) { Avatar(u.name, 48.dp, ring = if (u.id == me) Gold else Panel, emoji = u.avatarEmoji, colorKey = u.avatarColor, photoUrl = u.avatarUrl) }
                }
                item { LoadingMoreRow(vm.loadingMore) }
            }
        }
    }
}

/** Cardul mare pentru locul 1: poza pe toată lățimea. */
@Composable
private fun ChampionPlace(p: Place, mixed: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).height(210.dp).clip(RoundedCornerShape(24.dp))
            .border(2.dp, Gold, RoundedCornerShape(24.dp)).clickable(onClick = onClick),
    ) {
        PlacePhoto(p, Modifier.fillMaxSize(), iconSize = 64.dp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Scrim.copy(alpha = 0.92f))))
        Row(
            Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(50)).background(Gold)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.EmojiEvents, null, tint = Night, modifier = Modifier.size(16.dp))
            Text(" #1 ${if (mixed) "ÎN ARAD" else p.categoryLabel.uppercase()}", color = Night, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(p.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, tint = Gold, modifier = Modifier.size(16.dp))
                Text(" ${p.rating.roundToInt()} · ${p.checkinCount} check-in-uri", color = Lilac, fontSize = 13.sp)
                p.mayorName?.let {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.WorkspacePremium, null, tint = Gold, modifier = Modifier.size(16.dp))
                    Text(" $it", color = Lilac, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ChampionUser(u: UserRank, isMe: Boolean, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Violet, NightDeep))).border(2.dp, Gold, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(u.name, 72.dp, emoji = u.avatarEmoji, colorKey = u.avatarColor, photoUrl = u.avatarUrl)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = Gold, modifier = Modifier.size(16.dp))
                Text(" #1 EXPLORATOR AL ARADULUI", color = Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
            Text(u.name + if (isMe) " (tu)" else "", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Nivel ${u.level} · ${u.xp} XP", color = Lilac, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RankRow(
    rank: Int, trend: Int, onClick: () -> Unit, title: String, subtitle: String, score: String,
    highlight: Boolean = false, visual: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (highlight) PanelHigh else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", color = if (rank <= 3) Gold else TextMuted, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.width(30.dp))
        visual()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            trend > 0 -> Icon(Icons.Rounded.ArrowDropUp, "a urcat", tint = Mint, modifier = Modifier.size(26.dp))
            trend < 0 -> Icon(Icons.Rounded.ArrowDropDown, "a coborât", tint = Pink, modifier = Modifier.size(26.dp))
            else -> Icon(Icons.Rounded.Remove, null, tint = TextMuted.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
        Text(score, color = Gold, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 4.dp))
    }
    HorizontalDivider(color = Panel)
}

/** Cere pagina următoare când ultimele rânduri devin vizibile. */
@Composable
private fun LoadMoreWhenNearEnd(state: LazyListState, onLoadMore: () -> Unit) {
    LaunchedEffect(state) {
        snapshotFlow {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 4
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
}

@Composable
private fun LoadingMoreRow(visible: Boolean) {
    if (!visible) return
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), color = Gold, strokeWidth = 2.dp)
    }
}
