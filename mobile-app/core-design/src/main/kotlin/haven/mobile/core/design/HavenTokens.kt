package haven.mobile.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii. Softer than the web's squared keylines because a rounded rectangle is what
 * Android's own surfaces do, and a hard-cornered card sitting next to a system sheet looks
 * like a web view. Still restrained — nothing here is a pill except deliberate chips.
 */
val HavenShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * One spacing scale. Every gap in the app is one of these; "16.dp because it looked right"
 * is how five screens end up with five different gutters.
 */
object HavenSpacing {
    /** 1dp — the divider/border weight used everywhere. */
    val hairline: Dp = 1.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    /** Horizontal screen margin. */
    val gutter: Dp = 16.dp

    /** Minimum interactive size (Material accessibility floor). */
    val touchTarget: Dp = 48.dp

    /** Height of a media row in a list. */
    val rowHeight: Dp = 76.dp

    /** Leading glyph container in rows and headers. */
    val glyph: Dp = 40.dp
}

/** Elevation steps. Dark surfaces lean on tonal elevation; shadow is used sparingly. */
object HavenElevation {
    val flat: Dp = 0.dp
    val raised: Dp = 1.dp
    val floating: Dp = 3.dp
    val overlay: Dp = 6.dp
}

/**
 * Motion tokens — Material 3 easing, not the web's expo curves. Phone motion is short and
 * spatial: things enter from where they came from and leave the same way.
 */
object HavenMotion {
    /** Standard: most transitions. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Emphasised entrance for content that carries the screen. */
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Exits: leave quickly, don't linger. */
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val DURATION_INSTANT: Int = 100
    const val DURATION_SHORT: Int = 180
    const val DURATION_MEDIUM: Int = 280
    const val DURATION_LONG: Int = 420

    /** One loop of the loading skeleton sheen. */
    const val DURATION_SKELETON: Int = 1_150
}
