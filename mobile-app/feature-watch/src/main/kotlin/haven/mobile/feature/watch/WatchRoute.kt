package haven.mobile.feature.watch

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** Route id shared with the app module's route table and the Maestro flows. */
const val WATCH_ROUTE: String = "watch/{itemId}"
const val WATCH_ARG_ITEM_ID: String = "itemId"

fun NavGraphBuilder.watchRoute(
    navController: NavController,
) {
    composable(
        route = WATCH_ROUTE,
        arguments = listOf(
            navArgument(WATCH_ARG_ITEM_ID) {
                type = NavType.StringType
                nullable = false
            },
        ),
    ) { entry ->
        // A watch destination without an id is a programming error, not a user-facing state, so it
        // fails closed by popping rather than rendering an empty viewer.
        val itemId = entry.arguments?.getString(WATCH_ARG_ITEM_ID)
        if (itemId.isNullOrBlank()) {
            navController.popBackStack()
            return@composable
        }
        WatchScreen(
            navController = navController,
            itemId = itemId,
        )
    }
}
