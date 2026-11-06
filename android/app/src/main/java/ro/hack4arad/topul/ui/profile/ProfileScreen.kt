package ro.hack4arad.topul.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import ro.hack4arad.topul.BuildConfig
import ro.hack4arad.topul.R
import ro.hack4arad.topul.data.Badge
import ro.hack4arad.topul.data.MyCheckin
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Place
import ro.hack4arad.topul.data.Profile
import ro.hack4arad.topul.data.UpdateMeIn
import ro.hack4arad.topul.ui.components.AVATAR_COLORS
import ro.hack4arad.topul.ui.components.AVATAR_EMOJIS
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.components.ErrorState
import ro.hack4arad.topul.ui.components.GamePanel
import ro.hack4arad.topul.ui.components.Loading
import ro.hack4arad.topul.ui.components.PlacePhoto
import ro.hack4arad.topul.ui.components.ProgressBar
import ro.hack4arad.topul.ui.components.SectionTitle
import ro.hack4arad.topul.ui.components.avatarColor
import ro.hack4arad.topul.ui.map.levelTitle
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.Violet
import ro.hack4arad.topul.ui.theme.VioletDeep
import ro.hack4arad.topul.util.userMessage
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

class ProfileViewModel : ViewModel() {
    var profile by mutableStateOf<Profile?>(null)
        private set
    var visits by mutableStateOf<List<MyCheckin>>(emptyList())
        private set
    var places by mutableStateOf<Map<Int, Place>>(emptyMap())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var actionError by mutableStateOf<String?>(null)

    fun load() {
        viewModelScope.launch {
            runCatching { Network.api.me() }
                .onSuccess { profile = it; error = null }
                .onFailure { error = it.userMessage() }
        }
        viewModelScope.launch { runCatching { Network.api.myCheckins() }.onSuccess { visits = it } }
        if (places.isEmpty()) viewModelScope.launch {
            runCatching { Network.api.places() }.onSuccess { list -> places = list.associateBy { it.id } }
        }
    }

    fun update(name: String, emoji: String?, color: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            saving = true
            actionError = null
            runCatching { Network.api.updateMe(UpdateMeIn(name = name.trim(), avatarEmoji = emoji, avatarColor = color)) }
                .onSuccess { profile = it; Network.session.updateName(it.name); onDone() }
                .onFailure { actionError = it.userMessage() }
            saving = false
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            saving = true
            actionError = null
            runCatching { Network.api.deleteMe() }
                .onSuccess { if (it.isSuccessful) Network.session.logout() else actionError = "Nu am putut șterge contul (${it.code()})." }
                .onFailure { actionError = it.userMessage() }
            saving = false
        }
    }
}

private fun badgeIcon(code: String): ImageVector = when (code) {
    "prim_pas" -> Icons.Rounded.DirectionsWalk
    "explorator" -> Icons.Rounded.Explore
    "cafegiu" -> Icons.Rounded.LocalCafe
    "gurmand" -> Icons.Rounded.Restaurant
    "turist" -> Icons.Rounded.AccountBalance
    "pelerin" -> Icons.Rounded.Church
    "fotograf" -> Icons.Rounded.PhotoCamera
    "primar" -> Icons.Rounded.WorkspacePremium
    "judecator" -> Icons.Rounded.Gavel
    else -> Icons.Rounded.WorkspacePremium
}

/** „acum 5 min”, „ieri”... (serverul trimite ora UTC, fără fus orar). */
private fun relativeTime(iso: String): String = runCatching {
    val then = LocalDateTime.parse(iso.take(19)).toInstant(ZoneOffset.UTC)
    val d = Duration.between(then, java.time.Instant.now())
    when {
        d.toMinutes() < 1 -> "chiar acum"
        d.toMinutes() < 60 -> "acum ${d.toMinutes()} min"
        d.toHours() < 24 -> "acum ${d.toHours()} h"
        d.toDays() == 1L -> "ieri"
        else -> "acum ${d.toDays()} zile"
    }
}.getOrDefault("")

@Composable
fun ProfileScreen(onPlace: (Int) -> Unit, vm: ProfileViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() } // reîncarcă la fiecare intrare pe tab
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val p = vm.profile
    when {
        p == null && vm.error != null -> ErrorState(vm.error!!, onRetry = vm::load)
        p == null -> Loading()
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            // ---- antet
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.name, 64.dp, emoji = p.avatarEmoji, colorKey = p.avatarColor)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Nivel ${p.level} · ${levelTitle(p.level)} · ${p.xp} XP", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                TextButton(onClick = { editing = true }) {
                    Icon(Icons.Rounded.Edit, null, tint = Lilac, modifier = Modifier.size(18.dp))
                    Text(" Editează", color = Lilac)
                }
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(p.xpIntoLevel / p.xpPerLevel.toFloat(), height = 8.dp)
            Text("încă ${p.xpPerLevel - p.xpIntoLevel} XP până la nivelul ${p.level + 1}", color = TextMuted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))

            // ---- harta mea
            val visited = vm.visits.mapNotNull { vm.places[it.placeId] }.distinctBy { it.id }
            val total = vm.places.size
            SectionTitle("Harta mea")
            Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).background(Panel)) {
                if (BuildConfig.HAS_MAPS_KEY) MyMap(visited)
                Text(
                    "Ai explorat ${visited.size} din $total locuri",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(50))
                        .background(Color(0xCC0A0C1C)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(if (total == 0) 0f else visited.size / total.toFloat(), color = Mint, height = 6.dp)

            // ---- jurnalul vizitelor
            SectionTitle("Ultimele vizite")
            if (vm.visits.isEmpty()) {
                Text("Încă nicio vizită. Fă primul check-in și locul apare aici, pe harta ta.", color = Lilac)
            } else {
                vm.visits.take(8).forEach { v ->
                    val place = vm.places[v.placeId]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPlace(v.placeId) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (place != null) PlacePhoto(place, Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)), iconSize = 20.dp)
                        else Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Panel))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.placeName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(place?.categoryLabel, relativeTime(v.createdAt)).joinToString(" · "), color = TextMuted, fontSize = 12.sp)
                        }
                        if (place?.mayorId == p.id) Icon(Icons.Rounded.WorkspacePremium, "Primar", tint = Gold)
                    }
                }
            }

            // ---- misiuni
            if (p.missions.isNotEmpty()) {
                SectionTitle("Misiuni")
                p.missions.take(3).forEach { m ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(VioletDeep).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(badgeIcon(m.code), null, tint = Lilac, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.title, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Recompensă: insigna „${m.reward}”", color = Lilac, fontSize = 12.sp)
                            }
                            Text("${m.progress}/${m.target}", color = Gold, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(m.progress / m.target.toFloat(), color = Mint, height = 6.dp)
                    }
                }
            }

            // ---- insigne
            SectionTitle("Insigne · ${p.badges.size}/${p.allBadges.size}")
            p.allBadges.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { BadgeTile(it, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // ---- cont
            SectionTitle("Cont")
            GamePanel(Modifier.fillMaxWidth()) {
                Column {
                    Text(p.email, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull("conectat cu Google".takeIf { p.googleLinked }, "parolă setată".takeIf { p.hasPassword }).joinToString(" · "),
                        color = TextMuted, fontSize = 13.sp,
                    )
                }
            }
            vm.actionError?.let { Text(it, color = Pink, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { Network.session.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Ieși din cont", color = TextMuted) }
            TextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) { Text("Șterge contul definitiv", color = Pink) }
            Spacer(Modifier.height(12.dp))
        }
    }

    val prof = vm.profile
    if (editing && prof != null) {
        EditProfileDialog(
            prof, saving = vm.saving, error = vm.actionError,
            onSave = { n, e, c -> vm.update(n, e, c) { editing = false } },
            onDismiss = { editing = false; vm.actionError = null },
        )
    }
    if (deleting) DeleteAccountDialog(saving = vm.saving, onConfirm = { vm.deleteAccount() }, onDismiss = { deleting = false })
}

/** Harta mea: locurile vizitate ca puncte aurii (hartă statică, ușoară, „lite mode”). */
@Composable
private fun MyMap(visited: List<Place>) {
    val context = LocalContext.current
    val style = remember { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_night) }
    val center = if (visited.isNotEmpty()) LatLng(visited.map { it.lat }.average(), visited.map { it.lng }.average()) else LatLng(46.1745, 21.3185)
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(center, 12.8f) }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        properties = MapProperties(mapStyleOptions = style),
        uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
    ) {
        visited.forEach { p ->
            key(p.id) {
                Marker(
                    state = remember { MarkerState(LatLng(p.lat, p.lng)) },
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW),
                    title = p.name,
                )
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.alpha(if (badge.earned) 1f else 0.45f).clip(shape)
            .background(if (badge.earned) Violet else Panel)
            .border(1.5.dp, if (badge.earned) Gold else Track, shape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (badge.earned) badgeIcon(badge.code) else Icons.Rounded.Lock, null,
            tint = if (badge.earned) Gold else TextMuted, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(4.dp))
        Text(badge.title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 13.sp)
        Text(badge.description, color = Lilac, textAlign = TextAlign.Center, fontSize = 11.sp)
    }
}

@Composable
private fun EditProfileDialog(
    p: Profile,
    saving: Boolean,
    error: String?,
    onSave: (String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(p.name) }
    var emoji by remember { mutableStateOf(p.avatarEmoji) }
    var color by remember { mutableStateOf(p.avatarColor ?: "violet") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profilul tău") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Avatar(name, 72.dp, emoji = emoji, colorKey = color) }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Nume") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Avatar", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                AVATAR_EMOJIS.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { e ->
                            Text(
                                e, fontSize = 24.sp,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (e == emoji) Gold.copy(alpha = 0.35f) else Color.Transparent)
                                    .clickable { emoji = e }.padding(6.dp),
                            )
                        }
                    }
                }
                Text("Culoare", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AVATAR_COLORS.forEach { k ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(avatarColor(k))
                                .border(3.dp, if (k == color) Color.White else Color.Transparent, CircleShape)
                                .clickable { color = k },
                        )
                    }
                }
                error?.let { Text(it, color = Pink, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, emoji, color) }, enabled = !saving && name.trim().length >= 2) { Text("Salvează") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Renunță", color = TextMuted) } },
    )
}

@Composable
private fun DeleteAccountDialog(saving: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ștergi contul?") },
        text = {
            Column {
                Text("Se șterg definitiv: check-in-urile, pozele, voturile, sesizările, insignele și tronurile tale. Nu se poate anula.", color = Lilac)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it },
                    label = { Text("Scrie STERGE ca să confirmi") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm, enabled = !saving && typed.trim().uppercase() == "STERGE",
                colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Color.White),
            ) { Text("Șterge definitiv") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Renunță", color = TextMuted) } },
    )
}
