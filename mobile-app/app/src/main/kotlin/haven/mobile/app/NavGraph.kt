package haven.mobile.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import haven.mobile.feature.onboarding.onboardingRoute

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
        if (BuildConfig.DEBUG) {
            composable(AppRoute.Debug.route()) {
                DebugRoute()
            }
        }
    }
}