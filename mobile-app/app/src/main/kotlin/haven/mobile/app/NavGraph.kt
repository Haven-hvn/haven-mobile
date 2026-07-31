package haven.mobile.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import haven.mobile.feature.community.communityRoute
import haven.mobile.feature.library.libraryRoute
import haven.mobile.feature.onboarding.onboardingRoute
import haven.mobile.feature.settings.settingsRoute
import haven.mobile.feature.watch.watchRoute

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startRoute: AppRoute = AppRoute.Onboarding,
    onNavigate: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startRoute.route(),
    ) {
        composable(AppRoute.Onboarding.route()) {
            onboardingRoute(
                navController = navController,
                onNavigate = onNavigate,
            )
        }
        composable(AppRoute.Library.route()) {
            libraryRoute(navController = navController)
        }
        composable(AppRoute.Watch.route()) {
            watchRoute(navController = navController)
        }
        composable(AppRoute.Community.route()) {
            communityRoute(navController = navController)
        }
        composable(AppRoute.Settings.route()) {
            settingsRoute(
                navController = navController,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        if (BuildConfig.DEBUG) {
            composable(AppRoute.Debug.route()) {
                DebugRoute()
            }
        }
    }
}