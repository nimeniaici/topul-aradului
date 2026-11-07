package ro.hack4arad.topul.ui.place

import ro.hack4arad.topul.ui.theme.t
import ro.hack4arad.topul.ui.theme.Scrim
import ro.hack4arad.topul.ui.theme.Ink
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.OldPhoto
import ro.hack4arad.topul.data.PlaceDetail
import ro.hack4arad.topul.ui.components.PlacePhoto
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Night
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import java.util.Locale

/**
 * Ghidul audio: telefonul citește povestea locului cu vocea românească a sistemului (fără internet,
 * fără costuri). Dacă vocea română nu e instalată, îl trimitem pe utilizator s-o descarce.
 */
@Composable
fun AudioGuide(story: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tts", android.content.Context.MODE_PRIVATE) }
    var ready by remember { mutableStateOf(false) }
    var missingVoice by remember { mutableStateOf(false) }
    var noEngine by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var voices by remember { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
    var voiceName by remember { mutableStateOf(prefs.getString("voice", null)) }
    var rate by remember { mutableFloatStateOf(prefs.getFloat("rate", 0.95f)) }
    var pitch by remember { mutableFloatStateOf(prefs.getFloat("pitch", 1.0f)) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    fun applySettings(e: TextToSpeech) {
        e.setSpeechRate(rate)
        e.setPitch(pitch)
        voices.firstOrNull { it.name == voiceName }?.let { e.voice = it }
    }

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            val e = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) { noEngine = true; ready = true; return@TextToSpeech }
            val result = e.setLanguage(Locale.forLanguageTag("ro-RO"))
            // fără voce română: citim totuși (motorul o poate descărca singur), iar instalarea rămâne opțională
            missingVoice = result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
            // vocile disponibile: întâi cele românești, apoi restul (pentru cine vrea alt accent)
            voices = runCatching { e.voices.orEmpty().toList() }.getOrDefault(emptyList())
                .filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                .sortedWith(compareBy({ it.locale.language != "ro" }, { it.isNetworkConnectionRequired }, { it.name }))
            applySettings(e)
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { speaking = true }
                override fun onDone(id: String?) { speaking = false }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { speaking = false }
                override fun onError(id: String?, errorCode: Int) { speaking = false; if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) missingVoice = true }
            })
            ready = true
        }
        tts.value = engine
        onDispose { engine.stop(); engine.shutdown() }
    }

    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Panel).border(1.dp, Track, shape)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = ready) {
                val engine = tts.value ?: return@clickable
                when {
                    noEngine -> installVoice(context)
                    speaking -> { engine.stop(); speaking = false }
                    else -> {
                        applySettings(engine)
                        speaking = true
                        if (engine.speak(story, TextToSpeech.QUEUE_FLUSH, null, "poveste") != TextToSpeech.SUCCESS) { speaking = false; installVoice(context) }
                    }
                }
            }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Gold), contentAlignment = Alignment.Center) {
                Icon(if (speaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = GoldDark)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        noEngine -> t("Instalează un motor de voce")
                        speaking -> t("Ascultă… (atinge ca să oprești)")
                        else -> t("Ascultă povestea locului")
                    },
                    color = Ink, fontWeight = FontWeight.Black, fontSize = 16.sp,
                )
                Text(
                    if (noEngine) t("Telefonul nu are „Text în vorbire”. Atinge ca să-l instalezi.")
                    else t("Ghid audio în română · ~30 de secunde"),
                    color = TextMuted, fontSize = 13.sp,
                )
                if (missingVoice && !noEngine) Text(t("Vocea română nu e descărcată – atinge aici ca s-o instalezi"), color = Gold, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp).clickable { installVoice(context) })
            }
            if (speaking) SoundBars()
        }
        if (ready && !noEngine) {
            Text(t("Schimbă vocea · viteză · ton"), color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clickable { choosing = true }.padding(start = 76.dp, bottom = 12.dp))
        }
    }

    if (choosing) {
        val engine = tts.value
        fun preview() {
            engine ?: return
            applySettings(engine)
            engine.speak("Bună! Eu te plimb prin poveștile Aradului.", TextToSpeech.QUEUE_FLUSH, null, "proba")
        }
        fun save() {
            prefs.edit().putString("voice", voiceName).putFloat("rate", rate).putFloat("pitch", pitch).apply()
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { save(); choosing = false },
            title = { Text(t("Vocea ghidului"), fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    // stiluri rapide: același motor, alt ton și ritm
                    Text(t("Stil"), color = TextMuted, fontSize = 13.sp)
                    Row(Modifier.padding(vertical = 6.dp)) {
                        listOf(Triple(ro.hack4arad.topul.ui.theme.tr("Calm", "Calm", "Nyugodt"), 0.85f, 0.9f), Triple("Normal", 0.95f, 1.0f), Triple(ro.hack4arad.topul.ui.theme.tr("Vioi", "Lively", "Élénk"), 1.1f, 1.15f), Triple(ro.hack4arad.topul.ui.theme.tr("Grav", "Deep", "Mély"), 0.9f, 0.75f))
                            .forEach { (label, r, p) ->
                                val on = kotlin.math.abs(rate - r) < 0.01f && kotlin.math.abs(pitch - p) < 0.01f
                                Text(label, color = if (on) GoldDark else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50)).background(if (on) Gold else Track)
                                        .clickable { rate = r; pitch = p; preview() }.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                    }
                    Text(ro.hack4arad.topul.ui.theme.tr("Viteză", "Speed", "Sebesség") + ": ${"%.2f".format(rate)}", color = TextMuted, fontSize = 13.sp)
                    androidx.compose.material3.Slider(value = rate, onValueChange = { rate = it }, valueRange = 0.6f..1.5f,
                        onValueChangeFinished = { preview() })
                    Text(ro.hack4arad.topul.ui.theme.tr("Ton", "Pitch", "Hangmagasság") + ": ${"%.2f".format(pitch)}", color = TextMuted, fontSize = 13.sp)
                    androidx.compose.material3.Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.6f..1.5f,
                        onValueChangeFinished = { preview() })
                    Text(t("Voci instalate pe telefon"), color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    if (voices.isEmpty()) Text(t("Telefonul nu raportează alte voci."), color = TextMuted, fontSize = 12.sp)
                    voices.take(25).forEachIndexed { i, v ->
                        val on = v.name == voiceName
                        val lang = if (v.locale.language == "ro") "română" else v.locale.displayName
                        Text(ro.hack4arad.topul.ui.theme.tr("Vocea", "Voice", "Hang") + " ${i + 1} · $lang" + (if (v.isNetworkConnectionRequired) " · online" else ""),
                            color = if (on) GoldDark else Ink, fontSize = 14.sp, fontWeight = if (on) FontWeight.Black else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (on) Gold else Color.Transparent)
                                .clickable { voiceName = v.name; preview() }.padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                    Text(t("Mai multe voci (din setările telefonului)"), color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp).clickable {
                            try {
                                context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: ActivityNotFoundException) { installVoice(context) }
                        })
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { save(); engine?.stop(); choosing = false }) { Text(t("Gata")) } },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { voiceName = null; rate = 0.95f; pitch = 1.0f; save(); preview() }) { Text(t("Implicit")) }
            },
        )
    }
}

@Composable
private fun SoundBars() {
    val t = rememberInfiniteTransition(label = "bars")
    val phases = listOf(0, 180, 360, 90)
    Row(Modifier.height(26.dp), verticalAlignment = Alignment.CenterVertically) {
        phases.forEach { delay ->
            val h by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420, delayMillis = delay), RepeatMode.Reverse), label = "b")
            Box(Modifier.padding(horizontal = 2.dp).width(4.dp).fillMaxHeight(h).clip(RoundedCornerShape(2.dp)).background(Gold))
        }
    }
}

/**
 * Trecut vs prezent: poza veche peste cea de azi; tragi de mâner ca să compari.
 * Stânga = atunci (alb-negru), dreapta = acum.
 */
@Composable
fun ThenAndNow(place: PlaceDetail, old: OldPhoto, modifier: Modifier = Modifier) {
    var split by remember { mutableFloatStateOf(0.5f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val shape = RoundedCornerShape(20.dp)
    val sepia = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape).border(1.dp, Track, shape)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, drag ->
                        change.consume()
                        split = (split + drag / widthPx).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(Unit) { detectTapGestures { split = (it.x / widthPx).coerceIn(0f, 1f) } },
        ) {
            // ACUM (dedesubt, întreaga lățime)
            PlacePhoto(
                place.id, place.name, place.lat, place.lng, place.category,
                place.photoUrl ?: place.recentPhotos.firstOrNull()?.photoUrl, Modifier.fillMaxSize(),
            )
            // ATUNCI (deasupra, tăiată la stânga mânerului)
            AsyncImage(
                model = Network.fullUrl(old.url), contentDescription = "Fotografie din ${old.year}",
                contentScale = ContentScale.Crop, colorFilter = sepia,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    clipRect(right = size.width * split) { this@drawWithContent.drawContent() }
                },
            )
            Text(old.year, color = Night, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Gold).padding(horizontal = 10.dp, vertical = 4.dp))
            Text(t("Azi"), color = Color.White, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Scrim.copy(alpha = 0.8f)).padding(horizontal = 10.dp, vertical = 4.dp))
            // linia + mânerul
            val x = with(density) { (widthPx * split).toDp() }
            Canvas(Modifier.fillMaxSize()) {
                drawLine(Color.White, Offset(size.width * split, 0f), Offset(size.width * split, size.height), strokeWidth = 4f)
            }
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = x - 22.dp).size(44.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.SwapHoriz, null, tint = Night) }
        }
        Text("Trage de mâner: ${old.year} ↔ azi · ${old.credit}", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun installVoice(context: android.content.Context) {
    try {
        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.tts"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {}
    }
}
