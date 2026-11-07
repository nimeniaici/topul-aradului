package ro.hack4arad.topul.ui.place

import ro.hack4arad.topul.ui.theme.Scrim
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.Mint
import androidx.compose.material.icons.rounded.Directions
import ro.hack4arad.topul.util.navigateTo
import androidx.compose.material.icons.rounded.Share
import ro.hack4arad.topul.util.ShareCard
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.WorkspacePremium
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.CheckinResult
import ro.hack4arad.topul.data.Checkins
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.PlaceDetail
import ro.hack4arad.topul.ui.components.CheckinCelebration
import ro.hack4arad.topul.ui.components.ErrorState
import ro.hack4arad.topul.ui.components.GamePanel
import ro.hack4arad.topul.ui.components.Loading
import ro.hack4arad.topul.ui.components.Pill
import ro.hack4arad.topul.ui.components.PlacePhoto
import ro.hack4arad.topul.ui.components.SectionTitle
import ro.hack4arad.topul.ui.components.categoryColor
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Electric
import androidx.compose.material3.ButtonDefaults
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Night
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
import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember

private fun formatMb(mb: Double) = if (mb < 1) "<1 MB" else "${mb.roundToInt()} MB"

/** true pe date mobile (sau orice rețea „cu contor”), unde descărcările mari costă. */
private fun isOnMeteredNetwork(context: Context): Boolean =
    (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered

class PlaceViewModel(handle: SavedStateHandle) : ViewModel() {
    private val placeId: Int = checkNotNull(handle["id"])

    var place by mutableStateOf<PlaceDetail?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var actionError by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var result by mutableStateOf<CheckinResult?>(null)

    init { load() }

    fun load() {
        viewModelScope.launch {
            runCatching { Network.api.place(placeId) }
                .onSuccess { place = it; loadError = null }
                .onFailure { loadError = it.userMessage() }
        }
    }

    fun markBusy(value: Boolean) { busy = value }

    fun checkIn(fix: Fix, photo: ByteArray?) {
        viewModelScope.launch {
            busy = true
            actionError = null
            runCatching { Checkins.checkIn(placeId, fix, photo) }
                .onSuccess { result = it; load() }
                .onFailure { actionError = it.userMessage() }
            busy = false
        }
    }
}

@Composable
fun PlaceScreen(onBack: () -> Unit, onTour3D: (Int) -> Unit = {}, vm: PlaceViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var confirm3D by remember { mutableStateOf(false) }

    fun doCheckIn(photo: Uri?) {
        if (!hasLocationPermission(context)) {
            vm.actionError = "Dă permisiunea de locație și apasă din nou."
            return
        }
        scope.launch {
            vm.markBusy(true)
            val bytes = photo?.let { compressPhoto(context, it) }
            val fix = currentFix(context)
            vm.markBusy(false)
            if (fix == null) vm.actionError = "Nu am putut afla locația. Pornește GPS-ul și încearcă din nou."
            else vm.checkIn(fix, bytes)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.any { it }) doCheckIn(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) doCheckIn(photoUri)
    }

    val place = vm.place
    Box(Modifier.fillMaxSize()) {
        when {
            place == null && vm.loadError != null -> ErrorState(vm.loadError!!, onRetry = vm::load)
            place == null -> Loading()
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // copertă cu poza reală (Google sau comunitate) și gradient spre fundal
                Box(Modifier.fillMaxWidth().height(250.dp)) {
                    PlacePhoto(
                        place.id, place.name, place.lat, place.lng, place.category,
                        place.photoUrl ?: place.recentPhotos.firstOrNull()?.photoUrl,
                        Modifier.fillMaxSize(), iconSize = 72.dp,
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0.5f to Color.Transparent, 1f to Night)
                        )
                    )
                    Pill(
                        "#${place.rankInCategory} la ${place.categoryLabel}",
                        background = categoryColor(place.category), textColor = Night,
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    )
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(place.name, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    if (place.address.isNotBlank()) Text(place.address, color = TextMuted)

                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("${place.rating.roundToInt()}", "rating", Gold, Modifier.weight(1f))
                        Stat("${place.checkinCount}", "check-in-uri", Ink, Modifier.weight(1f))
                        Stat("${place.wins}/${place.duels}", "dueluri", Pink, Modifier.weight(1f))
                    }

                    GamePanel(Modifier.fillMaxWidth(), borderColor = if (place.mayorId == null) Gold else Violet) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WorkspacePremium, null, tint = Gold, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                if (place.mayorName == null) {
                                    Text("Tron liber", color = Gold, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                    Text("Primul check-in te face Primar.", color = Lilac)
                                } else if (place.mayorId == Network.session.userId) {
                                    Text("Tu ești Primarul aici", color = Gold, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                    Text("Revino des ca să nu-ți ia nimeni tronul.", color = Lilac)
                                } else {
                                    Text("Primar: @${place.mayorName}", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                    Text("Fă mai multe check-in-uri ca să-i iei tronul.", color = Lilac)
                                }
                            }
                        }
                    }

                    if (place.description.isNotBlank()) {
                        Text(place.description, color = Lilac, modifier = Modifier.padding(top = 14.dp))
                    }

                    // ghid audio în română (doar la obiective, mănăstiri, natură)
                    place.story?.let { AudioGuide(it, Modifier.padding(top = 14.dp)) }

                    // trecut vs prezent: poza veche peste cea de azi
                    place.oldPhoto?.let { old ->
                        SectionTitle("Trecut vs prezent")
                        ThenAndNow(place, old)
                    }

                    if (place.splatUrl != null) {
                        // Scena 3D se descarcă abia acum (lazy loading); pe date mobile cerem confirmare.
                        Button(
                            onClick = { if (isOnMeteredNetwork(context)) confirm3D = true else onTour3D(place.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Electric, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                        ) {
                            Icon(Icons.Rounded.ViewInAr, null)
                            Text(
                                "  Explorează în 3D" + (place.splatMb?.let { " · ${formatMb(it)}" } ?: ""),
                                fontSize = 17.sp,
                            )
                        }
                    }

                    vm.actionError?.let {
                        Text(
                            it, color = Color.White,
                            modifier = Modifier.padding(top = 14.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp)).background(Pink.copy(alpha = 0.85f)).padding(12.dp),
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            if (hasLocationPermission(context)) doCheckIn(null)
                            else permissionLauncher.launch(LOCATION_PERMISSIONS)
                        },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = GoldDark)
                        else Text(if (place.mayorId == null) "Cucerește tronul" else "Fac check-in aici", fontSize = 17.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            if (!hasLocationPermission(context)) {
                                permissionLauncher.launch(LOCATION_PERMISSIONS)
                            } else {
                                val uri = newPhotoUri(context)
                                photoUri = uri
                                cameraLauncher.launch(uri)
                            }
                        },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                    ) { Icon(Icons.Rounded.PhotoCamera, null, tint = Ink); Text("  Check-in cu poză · +5 XP bonus", color = Ink) }

                    // navigare până la loc, ca în Google Maps
                    val navContext = LocalContext.current
                    Button(
                        onClick = { navigateTo(navContext, place.lat, place.lng) },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Night),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                    ) { Icon(Icons.Rounded.Directions, null); Text("  Du-mă acolo", fontWeight = FontWeight.Bold) }

                    // ești Primar aici? arată-le prietenilor (card pentru Instagram Story)
                    if (place.mayorId != null && place.mayorId == Network.session.userId) {
                        val shareContext = LocalContext.current
                        val shareScope = rememberCoroutineScope()
                        OutlinedButton(
                            onClick = { shareScope.launch { ShareCard.share(shareContext, place.id, mayor = true) } },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                        ) { Icon(Icons.Rounded.Share, null, tint = Gold); Text("  Distribuie: sunt Primarul aici", color = Gold) }
                    }

                    if (place.recentPhotos.isNotEmpty()) {
                        SectionTitle("Poze de la comunitate")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(place.recentPhotos) { photo ->
                                Column(Modifier.width(140.dp)) {
                                    AsyncImage(
                                        model = Network.fullUrl(photo.photoUrl),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)),
                                    )
                                    Text(photo.userName, color = TextMuted, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }

        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Scrim.copy(alpha = 0.7f), contentColor = Color.White),
            modifier = Modifier.padding(12.dp).clip(CircleShape),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Înapoi") }

        val p3d = vm.place
        if (confirm3D && p3d != null) {
            AlertDialog(
                onDismissRequest = { confirm3D = false },
                title = { Text("Tur 3D pe date mobile") },
                text = {
                    Text(
                        "Ești pe date mobile. Scena 3D are " +
                            (p3d.splatMb?.let { formatMb(it) } ?: "câțiva zeci de MB") +
                            ". O descarci acum?",
                    )
                },
                confirmButton = { Button(onClick = { confirm3D = false; onTour3D(p3d.id) }) { Text("Descarcă") } },
                dismissButton = { TextButton(onClick = { confirm3D = false }) { Text("Mai târziu", color = TextMuted) } },
            )
        }

        vm.result?.let { r ->
            CheckinCelebration(r, place?.name ?: "", onDismiss = { vm.result = null })
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    GamePanel(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(label, color = TextMuted, fontSize = 12.sp)
        }
    }
}
