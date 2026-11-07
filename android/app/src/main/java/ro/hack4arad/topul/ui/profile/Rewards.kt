package ro.hack4arad.topul.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.RewardsOut
import ro.hack4arad.topul.ui.components.ProgressBar
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.Ink
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.OnInk
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.tr

/** Recompensele (demo): transport gratuit pentru check-in-uri în stații, reducere la localul preferat. */
@Composable
fun RewardsSection() {
    var data by remember { mutableStateOf<RewardsOut?>(null) }
    LaunchedEffect(Unit) { runCatching { Network.api.rewards() }.onSuccess { data = it } }
    val d = data ?: return
    Text(tr("RECOMPENSE", "REWARDS", "JUTALMAK"), color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    d.rewards.forEach { r ->
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(if (r.unlocked) Ink else Panel).border(1.dp, if (r.unlocked) Gold else Track, RoundedCornerShape(16.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (r.kind == "transport") Icons.Rounded.Tram else Icons.Rounded.Restaurant, null,
                    tint = if (r.unlocked) Gold else Ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, color = if (r.unlocked) OnInk else Ink, fontWeight = FontWeight.Bold)
                    Text(r.description, color = if (r.unlocked) OnInk.copy(alpha = 0.7f) else TextMuted, fontSize = 12.sp)
                }
                Text("${r.progress}/${r.goal}", color = Gold, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(r.progress / r.goal.toFloat(), color = Mint, height = 6.dp)
            r.voucher?.let { v ->
                Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Gold).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CardGiftcard, null, tint = ro.hack4arad.topul.ui.theme.GoldDark)
                    Text("  " + tr("Codul tău: ", "Your code: ", "Kódod: ") + v + "  (demo)", color = ro.hack4arad.topul.ui.theme.GoldDark, fontWeight = FontWeight.Black)
                }
            }
        }
    }
    Text(d.note + " " + tr("Check-in în stații: Transport CTP → „Sunt în stație”.", "Stop check-ins: CTP transport → „I'm at the stop”.", ""),
        color = TextMuted, fontSize = 11.sp)
}
