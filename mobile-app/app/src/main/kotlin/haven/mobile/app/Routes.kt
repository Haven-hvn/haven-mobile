package haven.mobile.app

sealed interface AppRoute {
    data object Onboarding : AppRoute
    data object Library : AppRoute
    data object Watch : AppRoute
    data object Community : AppRoute
    data object Settings : AppRoute
    data object Debug : AppRoute
}

fun AppRoute.route(): String = when (this) {
    AppRoute.Onboarding -> "onboarding"
    AppRoute.Library -> "library"
    AppRoute.Watch -> "watch/{itemId}"
    AppRoute.Community -> "community"
    AppRoute.Settings -> "settings"
    AppRoute.Debug -> "debug"
}