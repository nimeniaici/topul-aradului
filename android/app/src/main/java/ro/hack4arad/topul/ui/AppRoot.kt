package ro.hack4arad.topul.ui

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
import ro.hack4arad.topul.ui.profile.ProfileScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("map", "Hartă", Icons.Default.Place),
    Tab("duels", "Dueluri", Icons.Default.ThumbUp),
    Tab("top", "Top", Icons.Default.Star),
    Tab("profile", "Profil", Icons.Default.Person),
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
                    "👑 ${e.newMayorName} ți-a luat titlul de Primar la ${e.placeName}!"
                e.type == "mayor" && e.newMayorId == me.userId ->
                    "👑 Ești noul Primar la ${e.placeName}!"
                e.type == "checkin" && e.userName != me.userName ->
                    "📍 ${e.userName} a făcut check-in la ${e.placeName}"
                else -> null
            }
            if (text != null) scope.launch { snackbar.showSnackbar(text, duration = SnackbarDuration.Short) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = NightDeep) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GoldDark,
                                selectedTextColor = Gold,
                                indicatorColor = Gold,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                            ),
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "map", modifier = Modifier.padding(padding)) {
            val openPlace: (Int) -> Unit = { id -> nav.navigate("place/$id") }
            composable("map") { MapScreen(onPlace = openPlace) }
            composable("duels") { DuelScreen() }
            composable("top") { LeaderboardScreen(onPlace = openPlace) }
            composable("profile") { ProfileScreen(onPlace = openPlace) }
            composable(
                "place/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { PlaceScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
