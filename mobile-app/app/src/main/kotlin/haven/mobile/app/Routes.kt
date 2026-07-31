package haven.mobile.app

sealed interface AppRoute {
    data object Onboarding : AppRoute
    data object Debug : AppRoute
}

fun AppRoute.route(): String = when (this) {
    AppRoute.Onboarding -> "onboarding"
    AppRoute.Debug -> "debug"
}