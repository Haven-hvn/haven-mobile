package haven.mobile.core.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type for a phone.
 *
 * Three voices, matching the brand, but sized for a device held at arm's length rather than a
 * 2560px page: the sans face does the talking, the mono face is reserved for evidence
 * (addresses, piece CIDs, byte counts, error codes), and the serif appears exactly once — the
 * onboarding brand line — so it stays a signature instead of a texture.
 *
 * Faces are the platform families on purpose. Bundling the brand's licensed faces would add
 * ~400KB to the APK and a font-loading frame to cold start, for text that is mostly 12-16sp
 * UI copy where a system grotesk is indistinguishable at a glance. If the brand faces are
 * later bundled, only [Institution]/[Ledger]/[Editorial] change and nothing else moves.
 *
 * Sizes follow the Material 3 scale so every stock component (list items, chips, dialogs,
 * navigation) lands on the right role without per-call overrides. Tracking is tightened on
 * the display/headline roles, which is what large grotesk needs and what the default scale
 * does not do.
 */
object HavenFaces {
    val Institution: FontFamily = FontFamily.SansSerif
    val Ledger: FontFamily = FontFamily.Monospace
    val Editorial: FontFamily = FontFamily.Serif
}

/** Tabular figures, so counts and sizes stop dancing as they update. */
private const val TABULAR = "tnum"

val HavenTypography: Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Medium,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.032).em,
    ),
    displayMedium = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.028).em,
    ),
    displaySmall = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.024).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Medium,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.022).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Medium,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.020).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.018).em,
    ),
    titleLarge = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.016).em,
    ),
    titleMedium = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.012).em,
    ),
    titleSmall = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.008).em,
    ),
    bodyLarge = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.010).em,
    ),
    bodyMedium = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.006).em,
    ),
    bodySmall = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.008.em,
    ),
    labelSmall = TextStyle(
        fontFamily = HavenFaces.Institution,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.010.em,
    ),
)

/**
 * Roles Material 3 has no slot for. Screens reference these instead of hand-building
 * `TextStyle`s inline, which is how the mono voice stayed consistent across five screens.
 */
@Immutable
data class HavenTextStyles(
    /** Wallet addresses, piece CIDs, canister ids. Never wraps mid-token. */
    val mono: TextStyle,
    /** Same voice, caption size — subtitles under a title. */
    val monoSmall: TextStyle,
    /** Section eyebrow: uppercase, wide, quiet. The one print idiom worth keeping. */
    val overline: TextStyle,
    /** Numbers that update in place: quotas, counts, durations. */
    val figure: TextStyle,
    /** The single serif moment — onboarding hero. */
    val editorial: TextStyle,
) {
    companion object {
        val Default: HavenTextStyles = HavenTextStyles(
            mono = TextStyle(
                fontFamily = HavenFaces.Ledger,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = (-0.010).em,
            ),
            monoSmall = TextStyle(
                fontFamily = HavenFaces.Ledger,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            ),
            overline = TextStyle(
                fontFamily = HavenFaces.Ledger,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.14.em,
            ),
            figure = TextStyle(
                fontFamily = HavenFaces.Ledger,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontFeatureSettings = TABULAR,
            ),
            editorial = TextStyle(
                fontFamily = HavenFaces.Editorial,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.024).em,
                textAlign = TextAlign.Start,
            ),
        )
    }
}

internal val LocalHavenTextStyles = staticCompositionLocalOf { HavenTextStyles.Default }
