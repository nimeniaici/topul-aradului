package ro.hack4arad.topul.ui

import ro.hack4arad.topul.ui.transit.TransitScreen
import ro.hack4arad.topul.ui.iasmi.IasmiScreen
import ro.hack4arad.topul.ui.theme.tr
import ro.hack4arad.topul.ui.settings.SettingsScreen
import ro.hack4arad.topul.ui.theme.PanelHigh
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Home
import ro.hack4arad.topul.ui.reports.MyReportsScreen
import ro.hack4arad.topul.ui.home.DiscoverScreen
import ro.hack4arad.topul.ui.home.MapRequest
import ro.hack4arad.topul.ui.home.HomeActions
import ro.hack4arad.topul.ui.home.HomeTarget
import ro.hack4arad.topul.ui.home.HomeScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import ro.hack4arad.topul.data.Network
import androidx.compose.material3.NavigationBarItemDefaults
import ro.hack4arad.topul.ui.theme.Gold
import ro.hack4arad.topul.ui.theme.GoldDark
import ro.hack4arad.topul.ui.theme.NightDeep
import ro.hack4arad.topul.ui.theme.TextMuted
import ro.hack4arad.topul.ui.auth.AuthScreen
import ro.hack4arad.topul.ui.duels.DuelScreen
import ro.hack4arad.topul.ui.leaderboard.LeaderboardScreen
import ro.hack4arad.topul.ui.map.MapScreen
import ro.hack4arad.topul.ui.place.PlaceScreen
import ro.hack4arad.topul.ui.place.Tour3DScreen
import ro.hack4arad.topul.ui.profile.ProfileScreen

private data class Tab(val route: String, val label: () -> String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", { tr("Acasă", "Home", "Kezdőlap") }, Icons.Default.Home),
    Tab("map", { tr("Hartă", "Map", "Térkép") }, Icons.Default.Place),
    Tab("duels", { tr("Dueluri", "Duels", "Párbaj") }, Icons.Default.ThumbUp),
    Tab("top", { "Top" }, Icons.Default.Star),
    Tab("profile", { tr("Profil", "Profile", "Profil") }, Icons.Default.Person),
)

@Composable
fun AppRoot() {
    val token by Network.session.token.collectAsState()
    if (token == null) AuthScreen() else MainScaffold()
}

@Composable
private fun MainScaffold() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // Notificări live: cineva a făcut check-in / ți-a luat titlul de Primar
    LaunchedEffect(Unit) {
        Network.live.events.collect { e ->
            val me = Network.session
            val text = when {
                e.type == "mayor" && e.oldMayorId != null && e.oldMayorId == me.userId ->
                    "${e.newMayorName} ți-a luat titlul de Primar la ${e.placeName}"
                e.type == "mayor" && e.newMayorId == me.userId ->
                    "Ești noul Primar la ${e.placeName}"
                e.type == "report_status" && e.userId == me.userId && e.status == "rezolvat" ->
                    "Primăria a rezolvat sesizarea ta (${e.placeName})! +${e.xpGained ?: 0} XP"
                e.type == "report_status" && e.userId == me.userId ->
                    "Sesizarea ta (${e.placeName}) e acum: ${e.statusLabel}" + (e.note?.let { ". Primăria: $it" } ?: "")
                e.type == "checkin" && e.userName != me.userName ->
                    "${e.userName} a făcut check-in la ${e.placeName}"
                else -> null
            }
            if (text != null) scope.launch { snackbar.showSnackbar(text, duration = if (e.type == "report_status") SnackbarDuration.Long else SnackbarDuration.Short) }
        }
    }

    // schimbă tab-ul păstrând starea fiecăruia (harta rămâne unde ai lăsat-o)
    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = Color.Transparent, // se vede fundalul cu gradient circular al temei
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = NightDeep) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NightDeep,
                                selectedTextColor = Color.White,
                                indicatorColor = PanelHigh,
                                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                unselectedTextColor = Color.White.copy(alpha = 0.6f),
                            ),
                            selected = route == tab.route,
                            onClick = { goTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label()) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            val openPlace: (Int) -> Unit = { id -> nav.navigate("place/$id") }
            val newReport = { HomeActions.pendingMap = MapRequest(report = true); goTab("map") }
            composable("home") {
                HomeScreen(onOpen = { target ->
                    when (target) {
                        is HomeTarget.Tab -> goTab(target.route)
                        is HomeTarget.Map -> { HomeActions.pendingMap = target.request; goTab("map") }
                        is HomeTarget.Screen -> nav.navigate(target.route)
                    }
                })
            }
            composable("map") { MapScreen(onPlace = openPlace, onOpenReports = { nav.navigate("reports") }) }
            composable("reports") { MyReportsScreen(onBack = { nav.popBackStack() }, onNew = newReport) }
            composable("transit") { TransitScreen(onBack = { nav.popBackStack() }) }
            composable("iasmi") { IasmiScreen(onBack = { nav.popBackStack() }, onPlace = openPlace) }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable("discover") { DiscoverScreen(onBack = { nav.popBackStack() }, onPlace = openPlace) }
            composable("duels") { DuelScreen() }
            composable("top") { LeaderboardScreen(onPlace = openPlace) }
            composable("profile") { ProfileScreen(onPlace = openPlace) }
            composable(
                "place/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { PlaceScreen(onBack = { nav.popBackStack() }, onTour3D = { id -> nav.navigate("tour/$id") }) }
            composable(
                "tour/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { entry -> Tour3DScreen(placeId = entry.arguments?.getInt("id") ?: 0, onBack = { nav.popBackStack() }) }
        }
    }
}
