package haven.mobile.core.design

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import haven.mobile.core.design.color.HavenAccents
import haven.mobile.core.design.color.HavenPaperScheme
import haven.mobile.core.design.color.HavenVoidScheme
import haven.mobile.core.design.color.LocalHavenAccents

/**
 * The app theme. Wraps `MaterialTheme` so every stock Material 3 component inherits the
 * brand automatically, and layers on the roles Material has no slot for
 * ([HavenTheme.accents], [HavenTheme.text], [HavenTheme.spacing]).
 *
 * Dark is the default when the system has no preference: Haven is a viewer, and a media
 * library reads better on the void ground than on paper.
 *
 * Deliberately no dynamic color (Material You). Cache residency, attestation trust and the
 * four network hues all *encode meaning*; letting the wallpaper repaint them would make a
 * failed attestation badge indistinguishable from a verified one on some devices.
 */
@Composable
fun HavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) HavenVoidScheme else HavenPaperScheme
    val accents = if (darkTheme) HavenAccents.Void else HavenAccents.Paper

    CompositionLocalProvider(
        LocalHavenAccents provides accents,
        LocalHavenTextStyles provides HavenTextStyles.Default,
        // One ripple definition for the whole app, tinted by the scheme rather than grey.
        LocalIndication provides ripple(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HavenTypography,
            shapes = HavenShapes,
            content = content,
        )
    }
}

/**
 * Token accessors. `HavenTheme.accents.cacheCached` reads the same way as
 * `MaterialTheme.colorScheme.primary`, which is the point — one obvious place to look.
 */
object HavenTheme {
    val accents: HavenAccents
        @Composable @ReadOnlyComposable get() = LocalHavenAccents.current

    val text: HavenTextStyles
        @Composable @ReadOnlyComposable get() = LocalHavenTextStyles.current

    val spacing: HavenSpacing get() = HavenSpacing

    val elevation: HavenElevation get() = HavenElevation

    /** Convenience: the mono style already coloured for secondary emphasis. */
    val monoMuted: TextStyle
        @Composable @ReadOnlyComposable
        get() = LocalHavenTextStyles.current.mono.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
}
