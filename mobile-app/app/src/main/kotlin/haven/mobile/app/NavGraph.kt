package haven.mobile.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.reown.appkit.ui.appKitGraph
import haven.mobile.feature.collections.collectionsRoute
import haven.mobile.feature.community.communityRoute
import haven.mobile.feature.library.libraryRoute
import haven.mobile.feature.onboarding.onboardingRoute
import haven.mobile.feature.settings.settingsRoute
import haven.mobile.feature.watch.watchRoute

/**
 * The navigation graph.
 *
 * Each feature exposes a `NavGraphBuilder.xxxRoute(...)` extension that registers its own
 * destination, so they are called here directly on the `NavHost` builder.
 *
 * This previously read `composable(route) { libraryRoute(navController) }` — which compiles,
 * because the outer `NavGraphBuilder` receiver is still in scope inside the content lambda, but
 * means the destination body's only effect was to register *another* destination while
 * composing. The result was a blank screen for every feature route: nothing was ever drawn.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startRoute: AppRoute = AppRoute.Onboarding,
    onConnected: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startRoute.route(),
    ) {
        onboardingRoute(navController = navController, onNavigate = onConnected)
        libraryRoute(navController = navController)
        collectionsRoute(navController = navController)
        watchRoute(navController = navController)
        communityRoute(navController = navController)
        settingsRoute(
            navController = navController,
            onNavigateBack = { navController.popBackStack() },
        )
        composable(AppRoute.Debug.route()) { DebugRoute() }

        // Reown's own modal destinations (wallet chooser, QR, account sheet).
        appKitGraph(navController)
    }
}
