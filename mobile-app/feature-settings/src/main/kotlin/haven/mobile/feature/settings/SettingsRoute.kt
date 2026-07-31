package haven.mobile.feature.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.settingsRoute(
    navController: NavController,
    onNavigateBack: () -> Unit,
) {
    composable("settings") {
        SettingsScreen(
            navController = navController,
            onNavigateBack = onNavigateBack,
        )
    }
}