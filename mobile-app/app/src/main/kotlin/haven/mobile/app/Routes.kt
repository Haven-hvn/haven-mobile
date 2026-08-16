package haven.mobile.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import haven.mobile.core.design.component.HavenDestination

/**
 * Route table.
 *
 * The strings are a contract shared with the feature modules and the Maestro flows
 * (tasking plan §4.8), so they are declared once here and referenced — never retyped at a
 * call site.
 */
sealed interface AppRoute {
    data object Onboarding : AppRoute
    data object Library : AppRoute
    data object Collections : AppRoute
    data object Watch : AppRoute
    data object Community : AppRoute
    data object Settings : AppRoute
    data object Debug : AppRoute
}

/** Route pattern, including the argument placeholder where there is one. */
fun AppRoute.route(): String = when (this) {
    AppRoute.Onboarding -> "onboarding"
    AppRoute.Library -> "library"
    AppRoute.Collections -> "collections"
    AppRoute.Watch -> "watch/{$ARG_ITEM_ID}"
    AppRoute.Community -> "community"
    AppRoute.Settings -> "settings"
    AppRoute.Debug -> "debug"
}

const val ARG_ITEM_ID: String = "itemId"

/** Concrete navigation target for one item. */
fun watchRouteFor(itemId: String): String = "watch/$itemId"

/**
 * Bottom navigation: four destinations.
 *
 * Watch is not among them — it is a detail screen reached by choosing an item, and a nav slot that
 * needs an argument it does not have can only guess. Onboarding is a gate rather than a destination.
 *
 * Communities earns a permanent slot because joining one is how reading works at all, and the question
 * "why is my library empty" arrives at the worst possible moment.
 */
val bottomDestinations: List<HavenDestination> = listOf(
    HavenDestination(route = "library", label = "Library", icon = Icons.Default.VideoLibrary),
    HavenDestination(route = "community", label = "Feed", icon = Icons.Default.Groups),
    HavenDestination(route = "collections", label = "Communities", icon = Icons.Default.Explore),
    HavenDestination(route = "settings", label = "Settings", icon = Icons.Default.Settings),
)

/** Appended only in debug builds — see AppScaffold. */
val debugDestination: HavenDestination =
    HavenDestination(route = "debug", label = "Debug", icon = Icons.Default.BugReport)
