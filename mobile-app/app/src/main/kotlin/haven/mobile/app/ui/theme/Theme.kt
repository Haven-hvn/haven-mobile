package haven.mobile.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tokens from design/tokens.md — dark default, Material3 baseline, no purple/cream/glass.

private val HavenDarkBackground = Color(0xFF0B0E14)
private val HavenDarkSurface = Color(0xFF141A26)
private val HavenDarkSurfaceVariant = Color(0xFF1E2636)
private val HavenDarkOutline = Color(0xFF2A3447)
private val HavenDarkOnSurface = Color(0xFFE6E8EC)
private val HavenDarkOnSurfaceVariant = Color(0xFF9AA3B5)

private val HavenLightBackground = Color(0xFFF6F7F9)
private val HavenLightSurface = Color(0xFFFFFFFF)
private val HavenLightOutline = Color(0xFFD6DDE8)
private val HavenLightOnSurface = Color(0xFF0B0E14)

private val HavenAmberDark = Color(0xFFC8932A)
private val HavenAmberLight = Color(0xFF8A6318)
private val HavenAmberContainerDark = Color(0xFF3A2E12)
private val HavenAmberContainerLight = Color(0xFFFFE8B8)

private val Verified = Color(0xFF2E7D32)
private val Unverified = Color(0xFF616161)
private val Failed = Color(0xFFC62828)

private val HavenDarkScheme = darkColorScheme(
    primary = HavenAmberDark,
    onPrimary = Color.White,
    primaryContainer = HavenAmberContainerDark,
    onPrimaryContainer = Color(0xFFFFE8B8),
    secondary = Color(0xFF8EA0B8),
    background = HavenDarkBackground,
    onBackground = HavenDarkOnSurface,
    surface = HavenDarkSurface,
    onSurface = HavenDarkOnSurface,
    surfaceVariant = HavenDarkSurfaceVariant,
    onSurfaceVariant = HavenDarkOnSurfaceVariant,
    outline = HavenDarkOutline,
    error = Failed,
)

private val HavenLightScheme = lightColorScheme(
    primary = HavenAmberLight,
    onPrimary = Color.White,
    primaryContainer = HavenAmberContainerLight,
    onPrimaryContainer = Color(0xFF3A2600),
    secondary = Color(0xFF4A607A),
    background = HavenLightBackground,
    onBackground = HavenLightOnSurface,
    surface = HavenLightSurface,
    onSurface = HavenLightOnSurface,
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF4A607A),
    outline = HavenLightOutline,
    error = Failed,
)

private val HavenTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 32.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 22.sp, lineHeight = 28.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 16.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 14.sp),
)

@Composable
fun HavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) HavenDarkScheme else HavenLightScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = HavenTypography,
        content = content,
    )
}

object HavenSemanticColors {
    val verified = Verified
    val unverified = Unverified
    val failed = Failed
    val cached = Color(0xFF1B5E20)
    val fetching = Color(0xFF1565C0)
    val expired = Color(0xFF6D4C41)
    val missing = Color(0xFF424242)
}
