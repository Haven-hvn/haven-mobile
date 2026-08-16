package haven.mobile.feature.collections

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Route id shared with the app module's route table and the Maestro flows. */
const val COLLECTIONS_ROUTE: String = "collections"

fun NavGraphBuilder.collectionsRoute(
    navController: NavController,
) {
    composable(COLLECTIONS_ROUTE) {
        CollectionsScreen(navController = navController)
    }
}
