package ro.hack4arad.topul.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.MapsComposeExperimentalApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ro.hack4arad.topul.BuildConfig
import ro.hack4arad.topul.R
import ro.hack4arad.topul.data.Category
import ro.hack4arad.topul.data.CheckinResult
import ro.hack4arad.topul.data.Checkins
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Place
import ro.hack4arad.topul.data.Profile
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.components.CategoryChips
import ro.hack4arad.topul.ui.components.CheckinCelebration
import ro.hack4arad.topul.ui.components.GamePanel
import ro.hack4arad.topul.ui.components.Pill
import ro.hack4arad.topul.ui.components.PlaceImage
import ro.hack4arad.topul.ui.components.ProgressBar
import ro.hack4arad.topul.ui.components.categoryColor
import ro.hack4arad.topul.ui.components.categoryEmoji
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.NightDeep
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Violet
import ro.hack4arad.topul.ui.theme.VioletDeep
import ro.hack4arad.topul.util.LOCATION_PERMISSIONS
import ro.hack4arad.topul.util.currentFix
import ro.hack4arad.topul.util.hasLocationPermission
import ro.hack4arad.topul.util.userMessage
import kotlin.math.roundToInt

private val ARAD = LatLng(46.1745, 21.3185)

class MapViewModel : ViewModel() {
    var places by mutableStateOf<List<Place>>(emptyList())
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var selectedCategory by mutableStateOf<String?>(null)
        private set
    var heat by mutableStateOf<List<LatLng>>(emptyList())
        private set
    /** Crește la fiecare schimbare a heatmap-ului, ca harta să-l redeseneze. */
    var heatVersion by mutableIntStateOf(0)
        private set
    var profile by mutableStateOf<Profile?>(null)
        private set
    var selectedPlace by mutableStateOf<Place?>(null)
        private set
    var celebration by mutableStateOf<Pair<CheckinResult, String>?>(null)
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    private var myLat: Double? = null
    private var myLng: Double? = null
    private var loadJob: Job? = null

    init {
        load()
        loadHeat()
        loadProfile()
        viewModelScope.launch {
            Network.live.events.collect { e ->
                val lat = e.lat
                val lng = e.lng
                if (e.type == "checkin" && lat != null && lng != null) {
                    heat = heat + LatLng(lat, lng)
                    heatVersion++
                    updatePlace(e.placeId) { it.copy(checkinCount = it.checkinCount + 1) }
                }
                if (e.type == "mayor") {
                    updatePlace(e.placeId) { it.copy(mayorId = e.newMayorId, mayorName = e.newMayorName) }
                    if (e.oldMayorId == Network.session.userId || e.newMayorId == Network.session.userId) loadProfile()
                }
            }
        }
    }

    private fun updatePlace(id: Int?, change: (Place) -> Place) {
        places = places.map { if (it.id == id) change(it) else it }
        selectedPlace = selectedPlace?.let { if (it.id == id) change(it) else it }
    }

    /** Cu locația cunoscută, serverul sortează locurile după distanță. */
    fun setLocation(lat: Double, lng: Double) {
        myLat = lat
        myLng = lng
        load()
    }

    fun selectCategory(category: String?) {
        selectedCategory = category
        selectedPlace = null
        load()
    }

    fun selectPlace(place: Place?) { selectedPlace = place }

    fun load() {
        loadJob?.cancel() // un răspuns vechi nu are voie să-l suprascrie pe cel nou
        loadJob = viewModelScope.launch {
            runCatching {
                if (categories.isEmpty()) categories = Network.api.categories()
                places = Network.api.places(category = selectedCategory, lat = myLat, lng = myLng)
            }.onFailure { message = it.userMessage() }
        }
    }

    fun loadHeat() {
        viewModelScope.launch {
            runCatching { Network.api.recentCheckins(minutes = 60) }
                .onSuccess { points ->
                    heat = points.map { LatLng(it.lat, it.lng) }
                    heatVersion++
                }
        }
    }

    fun loadProfile() {
        viewModelScope.launch { runCatching { Network.api.me() }.onSuccess { profile = it } }
    }

    fun checkIn(place: Place, fix: ro.hack4arad.topul.util.Fix?) {
        if (fix == null) {
            message = "Nu am putut afla locația. Pornește GPS-ul și încearcă din nou."
            return
        }
        viewModelScope.launch {
            busy = true
            runCatching { Checkins.checkIn(place.id, fix) }
                .onSuccess {
                    celebration = it to place.name
                    selectedPlace = null
                    loadProfile()
                    load()
                }
                .onFailure { message = it.userMessage() }
            busy = false
        }
    }
}

@Composable
fun MapScreen(onPlace: (Int) -> Unit, vm: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPermission = result.values.any { it } }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(LOCATION_PERMISSIONS) }
    LaunchedEffect(hasPermission) {
        if (hasPermission) currentFix(context)?.let { vm.setLocation(it.lat, it.lng) }
    }
    LaunchedEffect(vm.message) {
        if (vm.message != null) {
            kotlinx.coroutines.delay(3500)
            vm.message = null
        }
    }

    var showHeat by rememberSaveable { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        if (BuildConfig.HAS_MAPS_KEY) {
            PlacesMap(vm, hasPermission, showHeat)
        } else {
            PlacesList(vm.places, onPlace)
        }

        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            vm.profile?.let { PlayerHud(it, Modifier.padding(horizontal = 12.dp)) }
            Spacer(Modifier.height(8.dp))
            CategoryChips(vm.categories, vm.selectedCategory, vm::selectCategory)
            vm.message?.let {
                Text(
                    it,
                    color = Color.White,
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Pink.copy(alpha = 0.9f))
                        .padding(12.dp),
                )
            }
        }

        if (BuildConfig.HAS_MAPS_KEY && vm.selectedPlace == null) {
            SmallFloatingActionButton(
                onClick = {
                    showHeat = !showHeat
                    if (showHeat) vm.loadHeat()
                },
                containerColor = if (showHeat) Pink else Panel,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Text("🔥", fontSize = 20.sp) }
        }

        AnimatedVisibility(
            visible = vm.selectedPlace != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            vm.selectedPlace?.let { place ->
                PlaceCard(
                    place = place,
                    busy = vm.busy,
                    onDetails = { onPlace(place.id) },
                    onClose = { vm.selectPlace(null) },
                    onCheckIn = {
                        if (!hasLocationPermission(context)) permissionLauncher.launch(LOCATION_PERMISSIONS)
                        else scope.launch { vm.checkIn(place, currentFix(context)) }
                    },
                )
            }
        }

        vm.celebration?.let { (result, name) ->
            CheckinCelebration(result, name, onDismiss = { vm.celebration = null })
        }
    }
}

/** Bara de jucător de sus: avatar, nivel, XP și misiunea curentă. */
@Composable
private fun PlayerHud(p: Profile, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NightDeep.copy(alpha = 0.92f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, 40.dp, emoji = p.avatarEmoji, colorKey = p.avatarColor)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text("Nivel ${p.level}", color = Color.White, fontWeight = FontWeight.Black)
                    Text(" · ${levelTitle(p.level)}", color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(p.xpIntoLevel / p.xpPerLevel.toFloat())
            }
            Spacer(Modifier.width(10.dp))
            Text("${p.xp} XP", color = Gold, fontWeight = FontWeight.Black)
        }
        p.missions.firstOrNull()?.let { m ->
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VioletDeep)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🎯", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(m.title, color = Lilac, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${m.progress}/${m.target}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun levelTitle(level: Int) = when {
    level <= 1 -> "Turist"
    level == 2 -> "Localnic"
    level <= 4 -> "Explorator"
    level <= 6 -> "Cunoscător"
    level <= 9 -> "Ambasador"
    else -> "Legenda Aradului"
}

@Composable
private fun PlaceCard(place: Place, busy: Boolean, onDetails: () -> Unit, onClose: () -> Unit, onCheckIn: () -> Unit) {
    val me = Network.session.userId
    val (status, action) = when (place.mayorId) {
        null -> "👑 Tron liber" to "Cucerește tronul"
        me -> "👑 Tu ești Primarul" to "Apără-ți tronul"
        else -> "👑 Primar: ${place.mayorName}" to "Fură tronul"
    }
    GamePanel(
        Modifier.fillMaxWidth().padding(12.dp),
        borderColor = if (place.mayorId == null) Gold else categoryColor(place.category),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlaceImage(place.photoUrl, place.category, place.id, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)), emojiSize = 28)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(place.categoryLabel, place.distanceM?.let(::formatDistance), "★ ${place.rating.roundToInt()}")
                            .joinToString(" · "),
                        color = TextMuted, fontSize = 13.sp,
                    )
                }
                Text("✕", color = TextMuted, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Pill(
                status,
                background = if (place.mayorId == null) Gold else Violet,
                textColor = if (place.mayorId == null) GoldDark else Color.White,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCheckIn, enabled = !busy, modifier = Modifier.weight(1f).height(50.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = GoldDark)
                    else Text("$action · +XP")
                }
                OutlinedButton(onClick = onDetails, modifier = Modifier.height(50.dp)) { Text("Detalii", color = Color.White) }
            }
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun PlacesMap(vm: MapViewModel, hasPermission: Boolean, showHeat: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(ARAD, 14f) }
    val style = remember { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_night) }
    val me = Network.session.userId
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        contentPadding = PaddingValues(top = 190.dp, bottom = 12.dp),
        properties = MapProperties(isMyLocationEnabled = hasPermission, mapStyleOptions = style),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
        onMapClick = { vm.selectPlace(null) },
    ) {
        // locurile apropiate se grupează într-o bulă cu număr; la zoom se desfac
        val items = remember(vm.places) { vm.places.map(::PlaceItem) }
        Clustering(
            items = items,
            onClusterClick = { cluster ->
                scope.launch {
                    camera.animate(CameraUpdateFactory.newLatLngZoom(cluster.position, camera.position.zoom + 2f), 500)
                }
                true
            },
            onClusterItemClick = { item ->
                vm.selectPlace(item.place)
                true // nu deschide fereastra standard Google
            },
            clusterContent = { cluster -> ClusterBubble(cluster.size) },
            clusterItemContent = { item ->
                val p = item.place
                PlaceMarker(categoryEmoji(p.category), categoryColor(p.category), mine = p.mayorId == me, noMayor = p.mayorId == null)
            },
        )
        if (showHeat && vm.heat.isNotEmpty()) {
            key(vm.heatVersion) {
                val provider = remember {
                    HeatmapTileProvider.Builder()
                        .data(vm.heat)
                        .radius(50)
                        .gradient(
                            Gradient(
                                intArrayOf(0xFF7C5CFF.toInt(), 0xFFFF3D81.toInt(), 0xFFFFD23F.toInt()),
                                floatArrayOf(0.2f, 0.6f, 1f),
                            )
                        )
                        .build()
                }
                TileOverlay(tileProvider = provider)
            }
        }
    }
}

/** Un loc pe hartă, în formatul cerut de gruparea markerelor. */
private class PlaceItem(val place: Place) : ClusterItem {
    override fun getPosition() = LatLng(place.lat, place.lng)
    override fun getTitle() = place.name
    override fun getSnippet(): String? = null
    override fun getZIndex(): Float = if (place.mayorId == Network.session.userId) 2f else 1f
}

/** Marker: pătrat rotunjit în culoarea categoriei, cu emoji; auriu cu coroană unde ești Primar. */
@Composable
private fun PlaceMarker(emoji: String, color: Color, mine: Boolean, noMayor: Boolean) {
    Box {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (mine) Gold else color)
                    .border(2.dp, if (mine) Color.White else NightDeep, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(if (mine) "👑" else emoji, fontSize = 18.sp) }
            Box(
                Modifier
                    .size(10.dp, 6.dp)
                    .clip(TriangleShape)
                    .background(if (mine) Gold else color)
            )
        }
        if (noMayor && !mine) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Gold)
                    .border(2.dp, NightDeep, CircleShape)
            )
        }
    }
}

private val TriangleShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width / 2f, size.height)
    close()
}

/** Bulă pentru mai multe locuri apropiate. */
@Composable
private fun ClusterBubble(count: Int) {
    Box(
        Modifier
            .size(if (count >= 10) 52.dp else 44.dp)
            .clip(CircleShape)
            .background(Violet)
            .border(3.dp, Gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("$count", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp) }
}

private fun formatDistance(m: Double): String =
    if (m < 1000) "${m.roundToInt()} m" else "%.1f km".format(m / 1000)

/** Rezervă când nu există cheie Google Maps: locurile ca listă, cele mai apropiate primele. */
@Composable
private fun PlacesList(places: List<Place>, onPlace: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = 200.dp, bottom = 88.dp)) {
        items(places, key = { it.id }) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPlace(p.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaceImage(p.photoUrl, p.category, p.id, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), emojiSize = 26)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        listOfNotNull(p.categoryLabel, p.distanceM?.let(::formatDistance)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1,
                    )
                }
                Text("★ ${p.rating.roundToInt()}", fontWeight = FontWeight.Black, color = Gold)
            }
            HorizontalDivider(color = Panel)
        }
    }
}
