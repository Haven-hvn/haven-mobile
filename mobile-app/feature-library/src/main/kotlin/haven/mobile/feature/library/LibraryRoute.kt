package haven.mobile.feature.library

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.libraryRoute(
    navController: NavController,
) {
    composable("library") {
        LibraryScreen(
            navController = navController,
        )
    }
}