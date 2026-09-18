package haven.mobile.feature.launches

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.launchesRoute(
    navController: NavController,
) {
    composable("launches") {
        LaunchesScreen(
            navController = navController,
        )
    }
}
