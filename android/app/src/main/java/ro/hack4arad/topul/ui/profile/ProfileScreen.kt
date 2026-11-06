package ro.hack4arad.topul.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.Badge
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Profile
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.components.ErrorState
import ro.hack4arad.topul.ui.components.GamePanel
import ro.hack4arad.topul.ui.components.Loading
import ro.hack4arad.topul.ui.components.Pill
import ro.hack4arad.topul.ui.components.ProgressBar
import ro.hack4arad.topul.ui.components.SectionTitle
import ro.hack4arad.topul.ui.components.categoryEmoji
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import ro.hack4arad.topul.data.UpdateMeIn
import ro.hack4arad.topul.ui.components.AVATAR_COLORS
import ro.hack4arad.topul.ui.components.AVATAR_EMOJIS
import ro.hack4arad.topul.ui.components.avatarColor

class ProfileViewModel : ViewModel() {
    var profile by mutableStateOf<Profile?>(null)
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

private fun badgeEmoji(code: String) = when (code) {
    "prim_pas" -> "👣"
    "explorator" -> "🧭"
    "cafegiu" -> "☕"
    "gurmand" -> "🍽️"
    "turist" -> "🏛️"
    "pelerin" -> "⛪"
    "fotograf" -> "📸"
    "primar" -> "👑"
    "judecator" -> "⚖️"
    else -> "🏅"
}

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
            // antet de jucător
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(p.name, 96.dp, emoji = p.avatarEmoji, colorKey = p.avatarColor)
                Spacer(Modifier.height(10.dp))
                Text(p.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Pill("Nivel ${p.level} · ${levelTitle(p.level)}", Gold, GoldDark)
                TextButton(onClick = { editing = true }) { Text("✏️ Editează profilul", color = Lilac) }
            }
            Spacer(Modifier.height(16.dp))
            GamePanel(Modifier.fillMaxWidth()) {
                Column {
                    Row {
                        Text("${p.xp} XP", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Spacer(Modifier.weight(1f))
                        Text("încă ${p.xpPerLevel - p.xpIntoLevel} XP → nivel ${p.level + 1}", color = TextMuted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(p.xpIntoLevel / p.xpPerLevel.toFloat(), height = 12.dp)
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${p.checkins}", "check-in-uri", Pink, Modifier.weight(1f))
                Stat("${p.placesVisited}", "locuri", Mint, Modifier.weight(1f))
                Stat("${p.mayorOf.size}", "tronuri", Gold, Modifier.weight(1f))
            }

            if (p.missions.isNotEmpty()) {
                SectionTitle("🎯 Misiuni")
                p.missions.take(4).forEach { m ->
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(16.dp)).background(VioletDeep).padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(badgeEmoji(m.code), fontSize = 22.sp)
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

            SectionTitle("🏅 Insigne · ${p.badges.size}/${p.allBadges.size}")
            p.allBadges.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { BadgeTile(it, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            SectionTitle("👑 Tronurile tale")
            if (p.mayorOf.isEmpty()) {
                Text("Încă niciunul. Fă cele mai multe check-in-uri într-un loc și tronul e al tău!", color = Lilac)
            } else {
                p.mayorOf.forEach { place ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(16.dp)).background(Panel)
                            .border(1.5.dp, Gold, RoundedCornerShape(16.dp))
                            .clickable { onPlace(place.id) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(categoryEmoji(place.category), fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(place.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("👑", fontSize = 18.sp)
                    }
                }
            }

            SectionTitle("⚙️ Cont")
            GamePanel(Modifier.fillMaxWidth()) {
                Column {
                    Text(p.email, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            "conectat cu Google".takeIf { p.googleLinked },
                            "parolă setată".takeIf { p.hasPassword },
                        ).joinToString(" · "),
                        color = TextMuted, fontSize = 13.sp,
                    )
                }
            }
            vm.actionError?.let { Text(it, color = Pink, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { Network.session.logout() }, modifier = Modifier.fillMaxWidth()) {
                Text("Ieși din cont", color = TextMuted)
            }
            TextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Șterge contul definitiv", color = Pink)
            }
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
    if (deleting) {
        DeleteAccountDialog(saving = vm.saving, onConfirm = { vm.deleteAccount() }, onDismiss = { deleting = false })
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
        title = { Text("✏️ Profilul tău") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Avatar(name, 72.dp, emoji = emoji, colorKey = color)
                }
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
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (e == emoji) Gold.copy(alpha = 0.35f) else Color.Transparent)
                                    .clickable { emoji = e }
                                    .padding(6.dp),
                            )
                        }
                    }
                }
                Text("Culoare", color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AVATAR_COLORS.forEach { key ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(avatarColor(key))
                                .border(3.dp, if (key == color) Color.White else Color.Transparent, CircleShape)
                                .clickable { color = key },
                        )
                    }
                }
                error?.let { Text(it, color = Pink, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, emoji, color) }, enabled = !saving && name.trim().length >= 2) { Text("Salvează") }
        },
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
                Text(
                    "Se șterg definitiv: check-in-urile, pozele, voturile, insignele și tronurile tale. Nu se poate anula.",
                    color = Lilac,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it },
                    label = { Text("Scrie STERGE ca să confirmi") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !saving && typed.trim().uppercase() == "STERGE",
                colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Color.White),
            ) { Text("Șterge definitiv") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Renunță", color = TextMuted) } },
    )
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    GamePanel(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(label, color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .alpha(if (badge.earned) 1f else 0.45f)
            .clip(shape)
            .background(if (badge.earned) Violet else Panel)
            .border(1.5.dp, if (badge.earned) Gold else Track, shape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (badge.earned) badgeEmoji(badge.code) else "🔒", fontSize = 30.sp)
        Text(badge.title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 13.sp)
        Text(badge.description, color = Lilac, textAlign = TextAlign.Center, fontSize = 11.sp)
    }
}
