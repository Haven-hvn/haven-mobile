package haven.mobile.feature.community

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.communityRoute(
    navController: NavController,
) {
    composable("community") {
        CommunityScreen(
            navController = navController,
        )
    }
}