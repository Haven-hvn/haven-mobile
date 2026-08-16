package haven.mobile.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import haven.mobile.core.design.HavenTheme as DesignSystemTheme

/**
 * The app's theme entry point is now a delegate.
 *
 * Colour, type, shape, spacing and motion tokens moved to `:core-design` so the five feature
 * modules can reach them — an app-module theme is invisible to `feature-library`, which is why
 * every screen had been hand-rolling its own styles. This wrapper stays so `MainActivity` and
 * any Compose preview keep one obvious entry point.
 *
 * See `:core-design` `HavenTheme`, `HavenPalette`, `HavenTypography`, `HavenTokens`.
 */
@Composable
fun HavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    DesignSystemTheme(darkTheme = darkTheme, content = content)
}
