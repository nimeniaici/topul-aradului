package ro.hack4arad.topul.ui.home

import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Recycling
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Tram
import ro.hack4arad.topul.ui.components.AppLogo
import ro.hack4arad.topul.ui.iasmi.IasmiOrb
import androidx.compose.material.icons.rounded.Settings
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.ui.theme.NightDeep
import ro.hack4arad.topul.ui.theme.Ink
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.Thunderstorm
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.Profile
import ro.hack4arad.topul.ui.components.Avatar
import ro.hack4arad.topul.ui.components.ProgressBar
import ro.hack4arad.topul.ui.map.levelTitle
import ro.hack4arad.topul.ui.theme.Electric
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.Lilac
import ro.hack4arad.topul.ui.theme.Mint
import ro.hack4arad.topul.ui.theme.Night
import ro.hack4arad.topul.ui.theme.Panel
import ro.hack4arad.topul.ui.theme.Pink
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.theme.Track
import ro.hack4arad.topul.ui.theme.Violet

/** Ce poate face fiecare buton din meniu. */
sealed interface HomeTarget {
    data class Tab(val route: String) : HomeTarget
    data class Map(val request: MapRequest) : HomeTarget
    data class Screen(val route: String) : HomeTarget
}

private data class Tile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val target: HomeTarget?,
    val onClick: (() -> Unit)? = null,
)

/**
 * „Acasă”: meniul mare din care ajungi la tot ce face aplicația.
 * Sus: profilul tău și progresul; dedesubt: o placă mare pentru hartă și plăci pentru fiecare funcție.
 */
@Composable
fun HomeScreen(onOpen: (HomeTarget) -> Unit) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<Profile?>(null) }
    LaunchedEffect(Unit) { runCatching { Network.api.me() }.onSuccess { profile = it } }

    val share = {
        val link = Network.baseUrl + "app"
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Hai în CityConnect Arad: semnalezi probleme, vezi transportul și evenimentele, iar în Topul Aradului devii Primar! $link")
        context.startActivity(Intent.createChooser(send, "Invită prieteni"))
    }
    val about = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Network.baseUrl + "despre"))) }

    val sections = listOf(
        tr("Orașul tău", "Your city", "A városod") to listOf(
            Tile(tr("Semnalează o problemă", "Report a problem", "Hiba bejelentése"), tr("Groapă, furtună, vandalism…", "Pothole, storm, vandalism…", "Kátyú, vihar, rongálás…"), Icons.Rounded.ReportProblem, Pink, HomeTarget.Map(MapRequest(report = true))),
            Tile(tr("Transport CTP", "CTP transport", "CTP közlekedés"), tr("Tramvaie și autobuze live", "Live trams and buses", "Élő villamos, busz"),
                Icons.Rounded.Tram, Electric, HomeTarget.Screen("transit")),
            Tile(tr("Calendar gunoi", "Waste calendar", "Hulladéknaptár"), tr("Când vine mașina pe strada ta", "Pickup days on your street", "Szállítási napok"),
                Icons.Rounded.Recycling, Mint, HomeTarget.Screen("gunoi")),
            Tile(tr("Sesizările mele", "My reports", "Bejelentéseim"), tr("Ce a răspuns Primăria", "City Hall replies", "A városháza válaszai"), Icons.Rounded.Assignment, Lilac, HomeTarget.Screen("reports")),
            Tile(tr("Alerte urgente", "Urgent alerts", "Sürgős riasztás"), tr("Furtună, inundație, canal", "Storm, flood, manhole", "Vihar, árvíz, csatorna"), Icons.Rounded.Thunderstorm, Pink, HomeTarget.Map(MapRequest(report = true))),
        ),
        tr("Comunitate", "Community", "Közösség") to listOf(
            Tile(tr("Comunitatea Aradului", "Arad community", "Aradi közösség"), tr("Chat, anunțuri, evenimente", "Chat, news, events", "Chat, események"),
                Icons.Rounded.Forum, Violet, HomeTarget.Screen("community")),
            Tile(tr("Prieteni", "Friends", "Barátok"), tr("Pe hartă, chat, tronurile lor", "On the map, chat, thrones", "Térkép, chat"),
                Icons.Rounded.Group, Mint, HomeTarget.Screen("friends")),
        ),
        tr("Joacă", "Play", "Játék") to listOf(
            Tile(tr("Dueluri", "Duels", "Párbaj"), tr("Care loc e mai tare?", "Which place is better?", "Melyik hely jobb?"), Icons.Rounded.SportsMma, Electric, HomeTarget.Tab("duels")),
            Tile("Topul Aradului", tr("Clasamente locuri și arădeni", "Places and players ranking", "Helyek és játékosok"), Icons.Rounded.EmojiEvents, Gold, HomeTarget.Tab("top")),
            Tile(tr("Misiuni și insigne", "Missions and badges", "Küldetések"), tr("Ce ai de cucerit", "What to conquer", "Mit kell meghódítani"), Icons.Rounded.MilitaryTech, Violet, HomeTarget.Tab("profile")),
            Tile(tr("Lista locurilor", "All places", "Helyek listája"), tr("Toate locurile, cu poze", "Every place, with photos", "Minden hely fotóval"), Icons.AutoMirrored.Rounded.ViewList, Mint, HomeTarget.Map(MapRequest(list = true))),
        ),
        tr("Descoperă Aradul", "Discover Arad", "Fedezd fel Aradot") to listOf(
            Tile(tr("Trecut vs prezent", "Then and now", "Akkor és most"), tr("Arad 1874–1931 vs azi", "Arad 1874–1931 vs today", "Arad 1874–1931 és ma"), Icons.Rounded.HistoryEdu, Gold, HomeTarget.Screen("discover")),
            Tile(tr("Ghid audio", "Audio guide", "Audio idegenvezető"), tr("Povestea locurilor, în română", "Stories of places (Romanian)", "Helyek története (románul)"), Icons.Rounded.Headphones, Mint, HomeTarget.Screen("discover")),
            Tile(tr("Turism în județ", "Tourism in Arad county", "Turizmus"), tr("325 de locuri și 30 de trasee", "325 places, 30 trails", "325 hely, 30 túra"),
                Icons.Rounded.Landscape, Gold, HomeTarget.Screen("turism")),
            Tile(tr("Obiective turistice", "Landmarks", "Látnivalók"), tr("Cetăți, palate, mănăstiri", "Fortresses, palaces, monasteries", "Várak, paloták, kolostorok"), Icons.Rounded.AccountBalance, Electric,
                HomeTarget.Map(MapRequest(list = true, category = "obiectiv"))),
        ),
        tr("Mai mult", "More", "Továbbiak") to listOf(
            Tile(tr("Invită prieteni", "Invite friends", "Barátok meghívása"), tr("Trimite-le aplicația", "Send them the app", "Küldd el nekik"), Icons.Rounded.PersonAdd, Mint, null, onClick = share),
            Tile(tr("Setări", "Settings", "Beállítások"), tr("Cont, limbă, temă, ajutor", "Account, language, theme", "Fiók, nyelv, téma"),
                Icons.Rounded.Settings, Ink, HomeTarget.Screen("settings")),
            Tile(tr("Despre aplicație", "About", "Névjegy"), "Hack4Arad 2026", Icons.Rounded.Info, TextMuted, null, onClick = about),
        ),
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            AppLogo(40.dp)
            Spacer(Modifier.width(10.dp))
            Text("CityConnect Arad", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Header(profile)
        Spacer(Modifier.height(14.dp))
        // bara „Întreab-o pe Iasmi” (asistentul AI), mereu sus
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Panel).border(1.dp, Track, RoundedCornerShape(28.dp))
                .clickable { onOpen(HomeTarget.Screen("iasmi")) }.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IasmiOrb(36)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Întreab-o pe Iasmi", "Ask Iasmi", "Kérdezd Iasmit"), color = Ink, fontWeight = FontWeight.Black)
                Text(tr("„Cum plătesc taxa?” · „Unde mănânc?”", "\"How do I pay?\" · \"Where to eat?\"", "„Hol egyek?”"), color = TextMuted, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted)
        }
        Spacer(Modifier.height(14.dp))
        MapHero { onOpen(HomeTarget.Tab("map")) }

        var index = 0
        sections.forEach { (title, tiles) ->
            Text(title.uppercase(), color = Gold, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tile ->
                        val order = index++
                        TileCard(tile, order, Modifier.weight(1f)) {
                            tile.onClick?.invoke() ?: tile.target?.let(onOpen)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Header(p: Profile?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(p?.name ?: "?", 56.dp, emoji = p?.avatarEmoji, colorKey = p?.avatarColor, photoUrl = p?.avatarUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (p != null) tr("Salut, ", "Hi, ", "Szia, ") + "@${p.name}" else tr("Salut!", "Hi!", "Szia!"), color = Ink, fontWeight = FontWeight.Black,
                fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p != null) {
                val lvl = p.level
                Text("Nivel $lvl · ${levelTitle(lvl)} · ${p.xp} XP", color = Lilac, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                ProgressBar(p.xpIntoLevel.toFloat() / p.xpPerLevel.coerceAtLeast(1), Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MapHero(onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(shape)
            .background(Brush.linearGradient(listOf(NightDeep, Violet, Electric)))
            .clickable(onClick = onClick).padding(20.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text(tr("Harta live a Aradului", "Live map of Arad", "Arad élő térképe"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text(tr("Fă check-in, cucerește tronuri, vezi unde e agitație", "Check in, conquer thrones, see what is busy", "Jelentkezz be, hódíts trónokat"), color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Gold).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Deschide harta", "Open map", "Térkép megnyitása"), color = GoldDark, fontWeight = FontWeight.Black)
                Icon(Icons.Rounded.ChevronRight, null, tint = GoldDark)
            }
        }
        Icon(Icons.Rounded.Map, null, tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.align(Alignment.CenterEnd).size(110.dp))
    }
}

@Composable
private fun TileCard(tile: Tile, order: Int, modifier: Modifier, onClick: () -> Unit) {
    // apar pe rând, cu un mic „pop” (meniul arată viu, nu static)
    val pop = remember { Animatable(0.85f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(40L * order)
        alpha.animateTo(1f)
    }
    LaunchedEffect(Unit) {
        delay(40L * order)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.height(132.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; this.alpha = alpha.value }
            .clip(shape).background(Panel).border(1.dp, Track, shape).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(tile.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(tile.icon, null, tint = tile.color, modifier = Modifier.size(26.dp))
        }
        Column {
            Text(tile.title, color = Ink, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tile.subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Suppress("unused") private val keepNight = Night
