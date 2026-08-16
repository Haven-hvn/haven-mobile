package haven.mobile.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import haven.mobile.core.design.component.HavenDestination
import haven.mobile.core.design.component.HavenNavigationBar
import haven.mobile.core.design.component.HavenScreen

/**
 * App shell: the nav graph plus persistent bottom navigation.
 *
 * The bar is hidden on the two routes that own the whole screen — onboarding (a gate, before
 * there is anything to navigate to) and watch (a viewer, where chrome competes with the content
 * and video wants the full height). Everywhere else it stays, so Library, Community and Settings
 * are one thumb-tap apart instead of unreachable.
 */
@Composable
fun HavenApp(
    navController: NavHostController,
    isDebugBuild: Boolean,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination: NavDestination? = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val destinations = remember(isDebugBuild) {
        if (isDebugBuild) bottomDestinations + debugDestination else bottomDestinations
    }
    val showNavigationBar = destinations.any { it.route == currentRoute }

    HavenScreen(
        bottomBar = {
            if (showNavigationBar) {
                HavenNavigationBar(
                    destinations = destinations,
                    currentRoute = currentRoute,
                    onSelect = { destination -> navController.switchTab(destination) },
                )
            }
        },
    ) { innerPadding ->
        // One Scaffold for the whole app (the Now-in-Android pattern). Screens draw their own
        // top bar inside this area rather than nesting a second Scaffold — `consumeWindowInsets`
        // is what stops those top bars from adding the status-bar inset a second time.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            AppNavGraph(
                navController = navController,
                onConnected = {
                    // Connecting promotes the session: land on the library and drop onboarding
                    // from the back stack so system-back does not return to the gate.
                    navController.navigate(AppRoute.Library.route()) {
                        popUpTo(AppRoute.Onboarding.route()) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

/**
 * Tab switching semantics: one instance per tab, per-tab state preserved, and a single entry
 * above the graph's start destination — the standard Material bottom-nav contract, which a bare
 * `navigate()` does not give you.
 */
private fun NavHostController.switchTab(destination: HavenDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
