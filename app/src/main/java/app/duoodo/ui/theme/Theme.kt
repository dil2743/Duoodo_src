package app.duoodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val IndigoDeep = Color(0xFF3A2FB8)

private val Light = lightColorScheme(
    primary = Color(0xFFAD4565),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE0E8),
    onPrimaryContainer = Color(0xFF59172F),
    secondary = Color(0xFF38685A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2F2E9),
    onSecondaryContainer = Color(0xFF214A3D),
    tertiary = Color(0xFF895230),
    tertiaryContainer = Color(0xFFFFE5CF),
    onTertiaryContainer = Color(0xFF582E14),
    background = Color(0xFFFFF0F4),
    onBackground = Color(0xFF402430),
    surface = Color(0xFFFFF0F4),
    onSurface = Color(0xFF402430),
    surfaceVariant = Color(0xFFF7E2E9),
    onSurfaceVariant = Color(0xFF72505D),
    surfaceContainerLowest = Color(0xFFFFF7F2),
    surfaceContainerLow = Color(0xFFFFF3F7),
    surfaceContainer = Color(0xFFFBE8EF),
    surfaceContainerHigh = Color(0xFFF7DEE8),
    surfaceContainerHighest = Color(0xFFF2D4E1),
    outline = Color(0xFFA77687),
    outlineVariant = Color(0xFFDCB3C0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFADC1),
    onPrimary = Color(0xFF55162D),
    primaryContainer = Color(0xFF8B445F),
    onPrimaryContainer = Color(0xFFFFDCE5),
    secondary = Color(0xFFABD9C6),
    onSecondary = Color(0xFF143E34),
    secondaryContainer = Color(0xFF315C4F),
    onSecondaryContainer = Color(0xFFD2F1E1),
    tertiary = Color(0xFFFFC299),
    tertiaryContainer = Color(0xFF754729),
    onTertiaryContainer = Color(0xFFFFE3CD),
    background = Color(0xFF503943),
    onBackground = Color(0xFFFFE4ED),
    surface = Color(0xFF503943),
    onSurface = Color(0xFFFFE4ED),
    surfaceVariant = Color(0xFF785563),
    onSurfaceVariant = Color(0xFFF8E3EB),
    surfaceContainerLowest = Color(0xFF624954),
    surfaceContainerLow = Color(0xFF563C48),
    surfaceContainer = Color(0xFF644652),
    surfaceContainerHigh = Color(0xFF70505E),
    surfaceContainerHighest = Color(0xFF7C5A69),
    outline = Color(0xFFC294A6),
    outlineVariant = Color(0xFFB58295),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Gradient used for the logo badge; matches the launcher icon background. */
val BrandGradientStart = Color(0xFF5B6CFF)
val BrandGradientEnd = IndigoDeep

/** The drawing paper stays white in both themes so a doodle looks the same on both phones. */
val Paper = Color.White

@Composable
fun DuoodoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Text style for small, faded meta lines like the footer. */
val MetaStyle = TextStyle(fontSize = 11.sp, letterSpacing = 0.2.sp)
