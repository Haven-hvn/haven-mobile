package haven.mobile.core.design.color

import androidx.compose.ui.graphics.Color
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * OKLCH -> sRGB.
 *
 * The Haven brand palette is authored in OKLCH (perceptual lightness, chroma, hue) rather
 * than hex. Two reasons this matters here rather than being decoration:
 *
 *  1. The dark ("void") and light ("paper") ramps are the same hue geometry at inverted
 *     lightness. Expressed as hex they look like two unrelated palettes and drift apart the
 *     first time somebody nudges one value; expressed as OKLCH the relationship is visible
 *     in the numbers.
 *  2. Equal lightness steps are equal *perceived* steps, so a surface ramp built by
 *     stepping L reads as evenly spaced elevation instead of bunching in the shadows the
 *     way an HSL ramp does.
 *
 * Conversion is Björn Ottosson's Oklab matrix pair plus the sRGB transfer function.
 * Out-of-gamut results are clamped per channel — every token in [HavenPalette] is inside
 * sRGB, so clamping is a guard, not a color-management strategy.
 */
object Oklch {

    /**
     * @param l perceptual lightness, 0f (black) .. 1f (white)
     * @param c chroma, 0f (grey) .. ~0.37f at maximum sRGB saturation
     * @param hueDegrees hue angle in degrees
     * @param alpha opacity, 0f..1f
     */
    fun color(l: Float, c: Float, hueDegrees: Float, alpha: Float = 1f): Color {
        val hueRadians = hueDegrees * (Math.PI / 180.0).toFloat()
        val a = c * cos(hueRadians)
        val b = c * sin(hueRadians)
        return oklab(l, a, b, alpha)
    }

    fun oklab(l: Float, a: Float, b: Float, alpha: Float = 1f): Color {
        // Oklab -> non-linear LMS
        val lRoot = l + 0.3963377774f * a + 0.2158037573f * b
        val mRoot = l - 0.1055613458f * a - 0.0638541728f * b
        val sRoot = l - 0.0894841775f * a - 1.2914855480f * b

        val lms0 = lRoot * lRoot * lRoot
        val lms1 = mRoot * mRoot * mRoot
        val lms2 = sRoot * sRoot * sRoot

        // LMS -> linear sRGB
        val rLinear = 4.0767416621f * lms0 - 3.3077115913f * lms1 + 0.2309699292f * lms2
        val gLinear = -1.2684380046f * lms0 + 2.6097574011f * lms1 - 0.3413193965f * lms2
        val bLinear = -0.0041960863f * lms0 - 0.7034186147f * lms1 + 1.7076147010f * lms2

        return Color(
            red = encode(rLinear),
            green = encode(gLinear),
            blue = encode(bLinear),
            alpha = alpha.coerceIn(0f, 1f),
        )
    }

    /** Linear-light channel -> sRGB-encoded channel, clamped to the display gamut. */
    private fun encode(linear: Float): Float {
        val v = linear.coerceIn(0f, 1f)
        val encoded = if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
        return encoded.coerceIn(0f, 1f)
    }

    /**
     * Perceptual lightness of an sRGB color — the inverse direction, used to decide whether
     * a foreground should be the light or dark member of a pair.
     */
    fun lightnessOf(color: Color): Float {
        val r = decode(color.red)
        val g = decode(color.green)
        val b = decode(color.blue)
        val lms0 = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val lms1 = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val lms2 = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        return 0.2104542553f * lms0 + 0.7936177850f * lms1 - 0.0040720468f * lms2
    }

    private fun decode(encoded: Float): Float =
        if (encoded <= 0.04045f) encoded / 12.92f else ((encoded + 0.055f) / 1.055f).pow(2.4f)
}
