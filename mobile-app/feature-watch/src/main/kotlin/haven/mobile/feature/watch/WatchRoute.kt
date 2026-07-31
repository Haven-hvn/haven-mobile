package haven.mobile.feature.watch

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.watchRoute(
    navController: NavController,
) {
    composable("watch/{itemId}") {
        val itemId = it.arguments?.getString("itemId") ?: return@composable
        WatchScreen(
            navController = navController,
            itemId = itemId,
        )
    }
}
