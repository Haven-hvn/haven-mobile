package haven.mobile.core.design.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import haven.mobile.core.design.color.Oklch.color as ok

/**
 * Haven brand palette, expressed for mobile.
 *
 * On-brand, not a port of the web design language. What carries over is the brand itself:
 * a single ember accent used sparingly, a near-black "void" ground and a warm "paper" light
 * ground, a neutral ramp with a faint cool cast, and fixed hues for the four public
 * networks. What does *not* carry over is the web's print apparatus (folios, crop marks,
 * engraved rules, letterpress offsets, grain). Phones are held, scrolled and tapped; the
 * mobile surface follows Material 3 structure and elevation instead.
 *
 * Everything is authored in OKLCH (see [Oklch]) so the dark and light ramps stay two ends of
 * one hue geometry rather than two palettes maintained by hand.
 */
object HavenPalette {

    // ── Hue anchors ──────────────────────────────────────────────────────────────────────
    /** Neutral cast: a cool blue-violet, kept far below the point where greys look tinted. */
    private const val NEUTRAL_HUE = 266f
    /** Warm cast for paper stock and near-white ink — never pure white, never pure grey. */
    private const val WARM_HUE = 85f
    /** The seal. One accent for the whole product. */
    private const val EMBER_HUE = 44f

    // ── Ember (brand accent) ─────────────────────────────────────────────────────────────
    /** Ember on dark: bright enough to survive the void, carries dark text when filled. */
    val EmberBright: Color = ok(0.715f, 0.190f, EMBER_HUE)
    /** Ember on light: deep enough to carry near-white text at AA when filled. */
    val EmberDeep: Color = ok(0.575f, 0.203f, 33f)
    val EmberLift: Color = ok(0.800f, 0.170f, 54f)
    val EmberInk: Color = ok(0.160f, 0.018f, 60f)

    // ── Network hues ─────────────────────────────────────────────────────────────────────
    // These encode data (which chain / which store), so they appear only on marks that mean
    // a network. Two variants because a hue that reads on void is muddy on paper.
    val ArkivOnDark: Color = ok(0.830f, 0.200f, 152f)
    val ArkivOnLight: Color = ok(0.600f, 0.160f, 152f)
    val IcpOnDark: Color = ok(0.750f, 0.170f, 274f)
    val IcpOnLight: Color = ok(0.530f, 0.190f, 284f)
    val EvmOnDark: Color = ok(0.860f, 0.170f, 78f)
    val EvmOnLight: Color = ok(0.630f, 0.150f, 66f)
    val FilecoinOnDark: Color = ok(0.840f, 0.135f, 208f)
    val FilecoinOnLight: Color = ok(0.590f, 0.130f, 220f)

    // ── Void ramp (dark, the mobile default) ─────────────────────────────────────────────
    val VoidSunk: Color = ok(0.078f, 0.013f, NEUTRAL_HUE)
    val VoidBase: Color = ok(0.132f, 0.017f, NEUTRAL_HUE)
    val VoidLow: Color = ok(0.158f, 0.018f, NEUTRAL_HUE)
    val VoidContainer: Color = ok(0.186f, 0.020f, NEUTRAL_HUE)
    val VoidHigh: Color = ok(0.216f, 0.021f, NEUTRAL_HUE)
    val VoidHighest: Color = ok(0.248f, 0.022f, NEUTRAL_HUE)
    val VoidBright: Color = ok(0.290f, 0.022f, NEUTRAL_HUE)

    // ── Paper ramp (light) ───────────────────────────────────────────────────────────────
    val PaperRaised: Color = ok(0.995f, 0.004f, 88f)
    val PaperBase: Color = ok(0.974f, 0.0075f, WARM_HUE)
    val PaperLow: Color = ok(0.960f, 0.009f, 83f)
    val PaperContainer: Color = ok(0.947f, 0.011f, 82f)
    val PaperHigh: Color = ok(0.933f, 0.013f, 81f)
    val PaperHighest: Color = ok(0.918f, 0.0145f, 80f)
    val PaperDim: Color = ok(0.888f, 0.016f, 80f)

    // ── Ink ──────────────────────────────────────────────────────────────────────────────
    val InkOnVoid: Color = ok(0.968f, 0.005f, WARM_HUE)
    val InkOnVoidMuted: Color = ok(0.735f, 0.014f, NEUTRAL_HUE)
    val InkOnVoidFaint: Color = ok(0.560f, 0.016f, NEUTRAL_HUE)
    val InkOnPaper: Color = ok(0.190f, 0.013f, NEUTRAL_HUE)
    val InkOnPaperMuted: Color = ok(0.455f, 0.014f, NEUTRAL_HUE)
    val InkOnPaperFaint: Color = ok(0.600f, 0.013f, NEUTRAL_HUE)

    // ── Status ───────────────────────────────────────────────────────────────────────────
    val AlertOnDark: Color = ok(0.680f, 0.190f, 25f)
    val AlertOnLight: Color = ok(0.505f, 0.195f, 27f)
}

/**
 * Semantic roles Material 3 has no slot for. Everything a screen needs to colour state —
 * cache residency, attestation trust, which network a piece lives on — resolves here so no
 * feature module ever hard-codes a hex.
 */
@Immutable
data class HavenAccents(
    val cacheCached: Color,
    val cacheFetching: Color,
    val cacheExpired: Color,
    val cacheMissing: Color,
    val attestVerified: Color,
    val attestUnverified: Color,
    val attestFailed: Color,
    val protocolArkiv: Color,
    val protocolIcp: Color,
    val protocolEvm: Color,
    val protocolFilecoin: Color,
    /** Ember at low alpha — chip and badge backgrounds that must not read as buttons. */
    val sealWash: Color,
    /** Ember at mid alpha — hairline edges on ember-tinted surfaces. */
    val sealEdge: Color,
    /** Skeleton base and highlight for loading placeholders. */
    val skeleton: Color,
    val skeletonSheen: Color,
) {
    companion object {
        val Void: HavenAccents = HavenAccents(
            cacheCached = HavenPalette.ArkivOnDark,
            cacheFetching = HavenPalette.FilecoinOnDark,
            cacheExpired = HavenPalette.EvmOnDark,
            cacheMissing = HavenPalette.InkOnVoidFaint,
            attestVerified = HavenPalette.ArkivOnDark,
            attestUnverified = HavenPalette.InkOnVoidFaint,
            attestFailed = HavenPalette.AlertOnDark,
            protocolArkiv = HavenPalette.ArkivOnDark,
            protocolIcp = HavenPalette.IcpOnDark,
            protocolEvm = HavenPalette.EvmOnDark,
            protocolFilecoin = HavenPalette.FilecoinOnDark,
            sealWash = HavenPalette.EmberBright.copy(alpha = 0.14f),
            sealEdge = HavenPalette.EmberBright.copy(alpha = 0.42f),
            skeleton = HavenPalette.VoidHigh,
            skeletonSheen = HavenPalette.VoidBright,
        )

        val Paper: HavenAccents = HavenAccents(
            cacheCached = HavenPalette.ArkivOnLight,
            cacheFetching = HavenPalette.FilecoinOnLight,
            cacheExpired = HavenPalette.EvmOnLight,
            cacheMissing = HavenPalette.InkOnPaperFaint,
            attestVerified = HavenPalette.ArkivOnLight,
            attestUnverified = HavenPalette.InkOnPaperFaint,
            attestFailed = HavenPalette.AlertOnLight,
            protocolArkiv = HavenPalette.ArkivOnLight,
            protocolIcp = HavenPalette.IcpOnLight,
            protocolEvm = HavenPalette.EvmOnLight,
            protocolFilecoin = HavenPalette.FilecoinOnLight,
            sealWash = HavenPalette.EmberDeep.copy(alpha = 0.10f),
            sealEdge = HavenPalette.EmberDeep.copy(alpha = 0.34f),
            skeleton = HavenPalette.PaperHighest,
            skeletonSheen = HavenPalette.PaperRaised,
        )
    }
}

internal val LocalHavenAccents = staticCompositionLocalOf { HavenAccents.Void }

/**
 * Dark scheme — the default. A media app is used in the dark, and the archive should feel
 * like a room with the lights down rather than a document.
 */
internal val HavenVoidScheme: ColorScheme = darkColorScheme(
    primary = HavenPalette.EmberBright,
    onPrimary = HavenPalette.EmberInk,
    primaryContainer = Oklch.color(0.330f, 0.105f, 40f),
    onPrimaryContainer = Oklch.color(0.900f, 0.070f, 62f),
    inversePrimary = HavenPalette.EmberDeep,

    secondary = Oklch.color(0.760f, 0.030f, 250f),
    onSecondary = Oklch.color(0.180f, 0.015f, 260f),
    secondaryContainer = Oklch.color(0.260f, 0.025f, 258f),
    onSecondaryContainer = Oklch.color(0.900f, 0.015f, 254f),

    tertiary = HavenPalette.FilecoinOnDark,
    onTertiary = Oklch.color(0.160f, 0.020f, 215f),
    tertiaryContainer = Oklch.color(0.280f, 0.055f, 212f),
    onTertiaryContainer = Oklch.color(0.900f, 0.045f, 208f),

    background = HavenPalette.VoidBase,
    onBackground = HavenPalette.InkOnVoid,
    surface = HavenPalette.VoidBase,
    onSurface = HavenPalette.InkOnVoid,
    surfaceVariant = HavenPalette.VoidHigh,
    onSurfaceVariant = HavenPalette.InkOnVoidMuted,
    surfaceTint = HavenPalette.EmberBright,
    surfaceBright = HavenPalette.VoidBright,
    surfaceDim = HavenPalette.VoidSunk,
    surfaceContainerLowest = HavenPalette.VoidSunk,
    surfaceContainerLow = HavenPalette.VoidLow,
    surfaceContainer = HavenPalette.VoidContainer,
    surfaceContainerHigh = HavenPalette.VoidHigh,
    surfaceContainerHighest = HavenPalette.VoidHighest,

    inverseSurface = HavenPalette.PaperBase,
    inverseOnSurface = HavenPalette.InkOnPaper,

    error = HavenPalette.AlertOnDark,
    onError = Oklch.color(0.160f, 0.020f, 25f),
    errorContainer = Oklch.color(0.300f, 0.110f, 25f),
    onErrorContainer = Oklch.color(0.910f, 0.060f, 25f),

    outline = Oklch.color(0.480f, 0.016f, 266f),
    outlineVariant = Oklch.color(0.300f, 0.014f, 266f),
    scrim = Color.Black,
)

/** Light scheme — warm stock, not white. Same hue geometry, inverted lightness. */
internal val HavenPaperScheme: ColorScheme = lightColorScheme(
    primary = HavenPalette.EmberDeep,
    onPrimary = HavenPalette.PaperRaised,
    primaryContainer = Oklch.color(0.920f, 0.050f, 58f),
    onPrimaryContainer = Oklch.color(0.350f, 0.140f, 32f),
    inversePrimary = HavenPalette.EmberBright,

    secondary = Oklch.color(0.470f, 0.035f, 258f),
    onSecondary = HavenPalette.PaperRaised,
    secondaryContainer = Oklch.color(0.905f, 0.020f, 256f),
    onSecondaryContainer = Oklch.color(0.280f, 0.030f, 260f),

    tertiary = HavenPalette.FilecoinOnLight,
    onTertiary = HavenPalette.PaperRaised,
    tertiaryContainer = Oklch.color(0.905f, 0.040f, 210f),
    onTertiaryContainer = Oklch.color(0.300f, 0.080f, 216f),

    background = HavenPalette.PaperBase,
    onBackground = HavenPalette.InkOnPaper,
    surface = HavenPalette.PaperBase,
    onSurface = HavenPalette.InkOnPaper,
    surfaceVariant = HavenPalette.PaperHighest,
    onSurfaceVariant = HavenPalette.InkOnPaperMuted,
    surfaceTint = HavenPalette.EmberDeep,
    surfaceBright = HavenPalette.PaperRaised,
    surfaceDim = HavenPalette.PaperDim,
    surfaceContainerLowest = HavenPalette.PaperRaised,
    surfaceContainerLow = HavenPalette.PaperLow,
    surfaceContainer = HavenPalette.PaperContainer,
    surfaceContainerHigh = HavenPalette.PaperHigh,
    surfaceContainerHighest = HavenPalette.PaperHighest,

    inverseSurface = HavenPalette.VoidBase,
    inverseOnSurface = HavenPalette.InkOnVoid,

    error = HavenPalette.AlertOnLight,
    onError = HavenPalette.PaperRaised,
    errorContainer = Oklch.color(0.905f, 0.045f, 27f),
    onErrorContainer = Oklch.color(0.330f, 0.150f, 27f),

    outline = Oklch.color(0.585f, 0.012f, 266f),
    outlineVariant = Oklch.color(0.815f, 0.008f, 266f),
    scrim = Color.Black,
)
