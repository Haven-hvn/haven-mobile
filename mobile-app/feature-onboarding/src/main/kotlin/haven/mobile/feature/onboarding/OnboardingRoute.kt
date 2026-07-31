package haven.mobile.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.onboardingRoute(
    navController: NavController,
    onNavigate: () -> Unit,
) {
    composable("onboarding") {
        OnboardingScreen(
            navController = navController,
            onNavigate = onNavigate,
        )
    }
}