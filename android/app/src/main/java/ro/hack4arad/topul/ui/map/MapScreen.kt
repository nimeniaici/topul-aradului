package ro.hack4arad.topul.ui.map

import ro.hack4arad.topul.ui.theme.Track
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import ro.hack4arad.topul.BuildConfig
import ro.hack4arad.topul.R
import ro.hack4arad.topul.data.Category
import ro.hack4arad.topul.data.CheckinResult
import ro.hack4arad.topul.data.Checkins
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Place
import ro.hack4arad.topul.data.Profile
import ro.hack4arad.topul.data.Report
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.components.CategoryChips
import ro.hack4arad.topul.ui.components.CheckinCelebration
import ro.hack4arad.topul.ui.components.PlacePhoto
import ro.hack4arad.topul.ui.components.ProgressBar
import ro.hack4arad.topul.ui.components.categoryColor
import ro.hack4arad.topul.ui.components.categoryIcon
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.Night
import ro.hack4arad.topul.ui.theme.NightDeep
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.PanelHigh
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Violet
import ro.hack4arad.topul.util.Fix
import ro.hack4arad.topul.util.LOCATION_PERMISSIONS
import ro.hack4arad.topul.util.compressPhoto
import ro.hack4arad.topul.util.currentFix
import ro.hack4arad.topul.util.hasLocationPermission
import ro.hack4arad.topul.util.newPhotoUri
import ro.hack4arad.topul.util.userMessage
import kotlin.math.roundToInt

private val ARAD = LatLng(46.1745, 21.3185)

/** Stilul hărții: întunecată (implicit), luminoasă sau satelit. */
enum class MapLook(val label: String) { DARK("Întunecată"), LIGHT("Luminoasă"), SATELLITE("Satelit") }

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
    var reports by mutableStateOf<List<Report>>(emptyList())
        private set
    var profile by mutableStateOf<Profile?>(null)
        private set
    /** Bandă live: ultimele întâmplări din oraș. */
    val ticker = mutableStateListOf<String>()
    var celebration by mutableStateOf<Pair<CheckinResult, String>?>(null)
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var myLocation by mutableStateOf<LatLng?>(null)
        private set

    private var loadJob: Job? = null

    init {
        load()
        loadHeat()
        loadProfile()
        loadReports()
        viewModelScope.launch {
            Network.live.events.collect { e ->
                val lat = e.lat
                val lng = e.lng
                when (e.type) {
                    "checkin" -> {
                        if (lat != null && lng != null) { heat = heat + LatLng(lat, lng); heatVersion++ }
                        updatePlace(e.placeId) { it.copy(checkinCount = it.checkinCount + 1) }
                        pushTicker("${e.userName} a făcut check-in la ${e.placeName}")
                    }
                    "mayor" -> {
                        updatePlace(e.placeId) { it.copy(mayorId = e.newMayorId, mayorName = e.newMayorName) }
                        pushTicker("${e.newMayorName} e noul Primar la ${e.placeName}")
                        if (e.oldMayorId == Network.session.userId || e.newMayorId == Network.session.userId) loadProfile()
                    }
                    "report" -> {
                        pushTicker("Sesizare nouă: ${e.placeName}")
                        loadReports()
                    }
                }
            }
        }
    }

    private fun pushTicker(text: String) {
        ticker.add(0, text)
        while (ticker.size > 5) ticker.removeAt(ticker.lastIndex)
    }

    private fun updatePlace(id: Int?, change: (Place) -> Place) {
        places = places.map { if (it.id == id) change(it) else it }
    }

    /** Cu locația cunoscută, serverul sortează locurile după distanță (caruselul arată întâi ce e aproape). */
    fun setLocation(lat: Double, lng: Double) {
        myLocation = LatLng(lat, lng)
        load()
    }

    fun selectCategory(category: String?) {
        selectedCategory = category
        load()
    }

    fun load() {
        loadJob?.cancel() // un răspuns vechi nu are voie să-l suprascrie pe cel nou
        loadJob = viewModelScope.launch {
            runCatching {
                if (categories.isEmpty()) categories = Network.api.categories()
                places = Network.api.places(category = selectedCategory, lat = myLocation?.latitude, lng = myLocation?.longitude)
            }.onFailure { message = it.userMessage() }
        }
    }

    fun loadHeat() {
        viewModelScope.launch {
            runCatching { Network.api.recentCheckins(minutes = 60) }
                .onSuccess { points -> heat = points.map { LatLng(it.lat, it.lng) }; heatVersion++ }
        }
    }

    fun loadReports() {
        viewModelScope.launch { runCatching { Network.api.reports() }.onSuccess { reports = it } }
    }

    fun loadProfile() {
        viewModelScope.launch { runCatching { Network.api.me() }.onSuccess { profile = it } }
    }

    fun checkIn(place: Place, fix: Fix?) {
        if (fix == null) {
            message = "Nu am putut afla locația. Pornește GPS-ul și încearcă din nou."
            return
        }
        viewModelScope.launch {
            busy = true
            runCatching { Checkins.checkIn(place.id, fix) }
                .onSuccess { celebration = it to place.name; loadProfile(); load() }
                .onFailure { message = it.userMessage() }
            busy = false
        }
    }

    fun sendReport(category: String, description: String, fix: Fix?, photo: ByteArray?, onDone: () -> Unit) {
        if (fix == null) {
            message = "Nu am putut afla locația problemei. Pornește GPS-ul."
            return
        }
        viewModelScope.launch {
            busy = true
            val text = "text/plain".toMediaType()
            runCatching {
                Network.api.createReport(
                    category = category.toRequestBody(text),
                    lat = fix.lat.toString().toRequestBody(text),
                    lng = fix.lng.toString().toRequestBody(text),
                    description = description.toRequestBody(text),
                    isMock = fix.isMock.toString().toRequestBody(text),
                    photo = photo?.let { MultipartBody.Part.createFormData("photo", "sesizare.jpg", it.toRequestBody("image/jpeg".toMediaType())) },
                )
            }.onSuccess {
                message = "Sesizare trimisă. Mulțumim! +${it.xpGained} XP"
                loadReports(); loadProfile(); onDone()
            }.onFailure { message = it.userMessage() }
            busy = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        if (vm.message != null) { kotlinx.coroutines.delay(3500); vm.message = null }
    }

    var look by rememberSaveable { mutableStateOf(MapLook.DARK) }
    var listMode by rememberSaveable { mutableStateOf(false) }
    var showHeat by rememberSaveable { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(vm.places, query) { searchPlaces(vm.places, query) }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(ARAD, 13.6f) }
    val pager = rememberPagerState(pageCount = { shown.size })
    val carouselPlace = shown.getOrNull(pager.currentPage)

    // Tragi caruselul -> harta zboară (scurt, ferm) la locul respectiv
    LaunchedEffect(pager, shown) {
        if (pager.currentPage >= shown.size && shown.isNotEmpty()) pager.scrollToPage(0)
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            val p = shown.getOrNull(page) ?: return@collect
            if (!listMode && pager.pageCount > 0) {
                camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), maxOf(camera.position.zoom, 15.5f)), 350)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (listMode || !BuildConfig.HAS_MAPS_KEY) {
            PlacesFeed(shown, onPlace, topPadding = 270.dp)
        } else {
            PlacesMap(
                vm = vm, places = shown, camera = camera, look = look, hasPermission = hasPermission, showHeat = showHeat,
                highlightedId = carouselPlace?.id,
                onPlaceTap = { p -> shown.indexOfFirst { it.id == p.id }.takeIf { it >= 0 }?.let { scope.launch { pager.animateScrollToPage(it) } } },
            )
        }

        // ---- sus: jucător, bandă live, categorii
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            vm.profile?.let { PlayerHud(it, Modifier.padding(horizontal = 12.dp)) }
            LiveTicker(vm.ticker.firstOrNull(), Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            SearchBar(query, { query = it }, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
            if (query.isNotBlank() && shown.isEmpty()) {
                Text("Niciun loc nu se potrivește cu „$query”", color = Lilac, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            CategoryChips(vm.categories, vm.selectedCategory, vm::selectCategory)
            vm.message?.let {
                Text(
                    it, color = Color.White,
                    modifier = Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Violet.copy(alpha = 0.95f)).padding(12.dp),
                )
            }
        }

        // ---- dreapta: unelte hartă
        Column(
            Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 290.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapTool(if (listMode) Icons.Rounded.Map else Icons.Rounded.ViewAgenda, if (listMode) "Hartă" else "Listă") { listMode = !listMode }
            if (!listMode) {
                MapTool(Icons.Rounded.Layers, look.label) { look = MapLook.entries[(look.ordinal + 1) % MapLook.entries.size] }
                MapTool(Icons.Rounded.LocalFireDepartment, "Agitație", active = showHeat) {
                    showHeat = !showHeat
                    if (showHeat) vm.loadHeat()
                }
                if (hasPermission) MapTool(Icons.Rounded.MyLocation, "Eu") {
                    vm.myLocation?.let { scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(it, 16f), 350) } }
                }
            }
        }

        // ---- buton mare, mereu vizibil: semnalează o groapă / problemă
        ExtendedFloatingActionButton(
            onClick = { reporting = true },
            icon = { Icon(Icons.Rounded.ReportProblem, null) },
            text = { Text("Semnalează o groapă", fontWeight = FontWeight.Black) },
            containerColor = Pink, contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = if (!listMode && shown.isNotEmpty()) 290.dp else 16.dp),
        )

        // ---- jos: carusel cu poze (tragi și harta te urmează)
        if (!listMode && shown.isNotEmpty()) {
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(300.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) { page ->
                val p = shown[page]
                CarouselCard(
                    p, busy = vm.busy,
                    onOpen = { onPlace(p.id) },
                    onCheckIn = {
                        if (!hasLocationPermission(context)) permissionLauncher.launch(LOCATION_PERMISSIONS)
                        else scope.launch { vm.checkIn(p, currentFix(context)) }
                    },
                )
            }
        }

        vm.celebration?.let { (result, name) -> CheckinCelebration(result, name, onDismiss = { vm.celebration = null }) }
    }

    if (reporting) {
        ModalBottomSheet(
            onDismissRequest = { reporting = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Night,
        ) {
            ReportForm(busy = vm.busy) { category, description, photo ->
                scope.launch {
                    val fix = if (hasLocationPermission(context)) currentFix(context) else null
                    if (fix == null && !hasLocationPermission(context)) permissionLauncher.launch(LOCATION_PERMISSIONS)
                    vm.sendReport(category, description, fix, photo) { reporting = false }
                }
            }
        }
    }
}

@Composable
private fun MapTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false,
                    tint: Color = Color.White, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = if (active) Gold else NightDeep.copy(alpha = 0.92f),
        contentColor = if (active) GoldDark else tint,
    ) { Icon(icon, contentDescription = label) }
}

/** Bara de jucător: avatar, nivel, XP. */
@Composable
private fun PlayerHud(p: Profile, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NightDeep.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.name, 38.dp, emoji = p.avatarEmoji, colorKey = p.avatarColor, photoUrl = p.avatarUrl)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nivel ${p.level}", color = Color.White, fontWeight = FontWeight.Black)
                Text("  ${levelTitle(p.level)}", color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(p.xpIntoLevel / p.xpPerLevel.toFloat(), height = 6.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text("${p.xp} XP", color = Gold, fontWeight = FontWeight.Black)
    }
}

/** Banda live: ultima întâmplare din oraș, cu tranziție de jos în sus. */
@Composable
private fun LiveTicker(latest: String?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(NightDeep.copy(alpha = 0.88f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Sensors, contentDescription = null, tint = Mint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = latest ?: "Harta e live: aici apar check-in-urile din oraș",
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
            label = "ticker",
        ) { text -> Text(text, color = Lilac, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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

fun formatDistance(m: Double): String = if (m < 1000) "${m.roundToInt()} m" else "%.1f km".format(m / 1000)

@Composable
private fun CarouselCard(place: Place, busy: Boolean, onOpen: () -> Unit, onCheckIn: () -> Unit) {
    val me = Network.session.userId
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Night)
            .border(1.dp, categoryColor(place.category).copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth().height(118.dp)) {
            PlacePhoto(place, Modifier.fillMaxSize(), iconSize = 40.dp)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Night)))
            Row(
                Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50))
                    .background(Night.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(categoryIcon(place.category), null, tint = categoryColor(place.category), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(place.categoryLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(place.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WorkspacePremium, null, tint = if (place.mayorId == null) Gold else TextMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    listOfNotNull(
                        when (place.mayorId) { null -> "Tron liber"; me -> "Tu ești Primarul"; else -> "Primar: ${place.mayorName}" },
                        place.distanceM?.let(::formatDistance),
                    ).joinToString(" · "),
                    color = if (place.mayorId == null) Gold else TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onCheckIn, enabled = !busy, modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = GoldDark)
                    else Text(if (place.mayorId == null) "Cucerește tronul" else "Fă check-in", fontSize = 14.sp)
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.height(42.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("Detalii", color = Color.White, fontSize = 14.sp)
                    Icon(Icons.Rounded.ChevronRight, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun PlacesMap(
    vm: MapViewModel,
    places: List<Place>,
    camera: CameraPositionState,
    look: MapLook,
    hasPermission: Boolean,
    showHeat: Boolean,
    highlightedId: Int?,
    onPlaceTap: (Place) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkStyle = remember { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_night) }
    val items = remember(places) { places.map(::PlaceItem) }
    var renderer by remember { mutableStateOf<PhotoClusterRenderer?>(null) }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        contentPadding = PaddingValues(top = 200.dp, bottom = 250.dp),
        properties = MapProperties(
            isMyLocationEnabled = hasPermission,
            mapType = if (look == MapLook.SATELLITE) MapType.HYBRID else MapType.NORMAL,
            mapStyleOptions = if (look == MapLook.DARK) darkStyle else null,
        ),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false, compassEnabled = false),
    ) {
        // trebuie creat ÎNĂUNTRUL hărții (are nevoie de obiectul GoogleMap)
        val clusterManager = rememberClusterManager<PlaceItem>()
        // markerele native cu poze (desenate o dată, fără animațiile lente)
        MapEffect(clusterManager) { map ->
            val manager = clusterManager ?: return@MapEffect
            manager.algorithm = NonHierarchicalDistanceBasedAlgorithm<PlaceItem>().apply { maxDistanceBetweenClusteredItems = 90 }
            val r = PhotoClusterRenderer(context, map, manager, scope)
            manager.renderer = r
            renderer = r
            manager.setOnClusterClickListener { cluster ->
                scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(cluster.position, camera.position.zoom + 2f), 300) }
                true
            }
            manager.setOnClusterItemClickListener { item -> onPlaceTap(item.place); true }
        }
        if (clusterManager != null) Clustering(items = items, clusterManager = clusterManager)

        // sesizările (gropi etc.) - markere roșii separate
        vm.reports.forEach { r ->
            key("r${r.id}") {
                Marker(
                    state = remember { MarkerState(LatLng(r.lat, r.lng)) },
                    title = r.categoryLabel,
                    snippet = listOf(r.description, "semnalat de ${r.userName}").filter { it.isNotBlank() }.joinToString(" · "),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                    alpha = 0.9f,
                )
            }
        }

        if (showHeat && vm.heat.isNotEmpty()) {
            key(vm.heatVersion) {
                val provider = remember {
                    HeatmapTileProvider.Builder().data(vm.heat).radius(50)
                        .gradient(Gradient(intArrayOf(0xFF7C5CFF.toInt(), 0xFFFF3D81.toInt(), 0xFFFFD23F.toInt()), floatArrayOf(0.2f, 0.6f, 1f)))
                        .build()
                }
                TileOverlay(tileProvider = provider)
            }
        }
    }

    LaunchedEffect(highlightedId, renderer) { renderer?.highlight(highlightedId, items) }
}

/** Lista de locuri (alternativa la hartă): carduri mari cu poze, cele mai apropiate primele. */
@Composable
private fun PlacesFeed(places: List<Place>, onPlace: (Int) -> Unit, topPadding: androidx.compose.ui.unit.Dp) {
    LazyColumn(
        contentPadding = PaddingValues(top = topPadding, bottom = 24.dp, start = 16.dp, end = 72.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(places, key = { it.id }) { p ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).clickable { onPlace(p.id) }) {
                PlacePhoto(p, Modifier.fillMaxWidth().height(150.dp), iconSize = 44.dp)
                Column(Modifier.padding(14.dp)) {
                    Text(p.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(categoryIcon(p.category), null, tint = categoryColor(p.category), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            listOfNotNull(p.categoryLabel, p.distanceM?.let(::formatDistance), "${p.rating.roundToInt()} puncte").joinToString(" · "),
                            color = TextMuted, fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

private val REPORT_CATEGORIES = listOf(
    "groapa" to "Groapă în drum", "trotuar" to "Trotuar stricat", "iluminat" to "Iluminat stricat",
    "gunoi" to "Gunoi / mizerie", "semnalizare" to "Semn sau semafor", "altele" to "Altceva",
)

/** Formularul de sesizare: categorie, descriere, poză. Poziția = unde ești acum (lângă problemă). */
@Composable
private fun ReportForm(busy: Boolean, onSend: (String, String, ByteArray?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("groapa") }
    var description by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoTaken by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> photoTaken = ok }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ReportProblem, null, tint = Pink, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text("Semnalează o problemă", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Text("Ajunge pe harta orașului, cu poza și locul exact. +10 XP", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(REPORT_CATEGORIES) { (code, label) ->
                FilterChip(
                    selected = category == code, onClick = { category = code }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Panel, labelColor = Color.White, selectedContainerColor = Pink, selectedLabelColor = Color.White,
                    ),
                    border = null, shape = RoundedCornerShape(50),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = description, onValueChange = { if (it.length <= 500) description = it },
            label = { Text("Descriere (opțional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { val u = newPhotoUri(context); photoUri = u; camera.launch(u) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Rounded.CameraAlt, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(if (photoTaken) "Poză adăugată · refă" else "Adaugă poză", color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch {
                    val bytes = if (photoTaken) photoUri?.let { compressPhoto(context, it) } else null
                    onSend(category, description.trim(), bytes)
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else {
                Icon(Icons.Rounded.Flag, null)
                Spacer(Modifier.width(8.dp))
                Text("Trimite sesizarea", fontSize = 16.sp)
            }
        }
    }
}


/** Caută după nume, categorie sau adresă, fără să conteze diacriticele („cafenea” găsește „Cafenele”). */
internal fun searchPlaces(places: List<Place>, query: String): List<Place> {
    val q = plain(query)
    if (q.isBlank()) return places
    val words = q.split(" ").filter { it.isNotBlank() }
    return places.filter { p ->
        val hay = plain("${p.name} ${p.categoryLabel} ${p.address}")
        // „pizza” găsește și „pizzeria”, „cafenea” și „cafenele” (ignorăm ultima literă la cuvinte lungi)
        words.all { w -> w in hay || (w.length >= 5 && w.dropLast(1) in hay) }
    }.sortedByDescending { plain(it.name).startsWith(q) }
}

private fun plain(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .trim()

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(Panel.copy(alpha = 0.96f)).border(1.dp, Track, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Lilac, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Caută un loc, ex. pizza, cafenea, KFC", color = TextMuted, fontSize = 15.sp, maxLines = 1)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Gold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close, "Șterge căutarea", tint = Lilac,
                modifier = Modifier.size(22.dp).clip(CircleShape).clickable { onChange(""); focus.clearFocus() },
            )
        }
    }
}
