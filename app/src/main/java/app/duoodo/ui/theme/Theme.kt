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

/** Brand indigo from the app icon, with a warm yellow accent (the bird's chirp). */
private val Indigo = Color(0xFF4F55E8)
private val IndigoDeep = Color(0xFF3A2FB8)
private val Chirp = Color(0xFFFFC93C)

private val Light = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E2FF),
    onPrimaryContainer = Color(0xFF14146B),
    secondary = Color(0xFF5C5D7E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E0FA),
    onSecondaryContainer = Color(0xFF191A36),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFE08F),
    onTertiaryContainer = Color(0xFF2B1B00),
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF1B1B23),
    surface = Color(0xFFF7F6FC),
    onSurface = Color(0xFF1B1B23),
    surfaceVariant = Color(0xFFE3E1EF),
    onSurfaceVariant = Color(0xFF5E5D6E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F0F9),
    surfaceContainer = Color(0xFFECEBF6),
    surfaceContainerHigh = Color(0xFFE6E5F1),
    surfaceContainerHighest = Color(0xFFE0DFEC),
    outline = Color(0xFF8C8B9C),
    outlineVariant = Color(0xFFD6D4E3),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFBFC1FF),
    onPrimary = Color(0xFF1F1F8A),
    primaryContainer = IndigoDeep,
    onPrimaryContainer = Color(0xFFE2E2FF),
    secondary = Color(0xFFC5C4E3),
    onSecondary = Color(0xFF2E2F4C),
    secondaryContainer = Color(0xFF444564),
    onSecondaryContainer = Color(0xFFE1E0FA),
    tertiary = Chirp,
    tertiaryContainer = Color(0xFF5E4100),
    onTertiaryContainer = Color(0xFFFFE08F),
    background = Color(0xFF121218),
    onBackground = Color(0xFFE5E3EE),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE5E3EE),
    surfaceVariant = Color(0xFF46454F),
    onSurfaceVariant = Color(0xFFC8C6D4),
    surfaceContainerLowest = Color(0xFF0D0D12),
    surfaceContainerLow = Color(0xFF1A1A21),
    surfaceContainer = Color(0xFF1F1F27),
    surfaceContainerHigh = Color(0xFF292932),
    surfaceContainerHighest = Color(0xFF34343D),
    outline = Color(0xFF928F9F),
    outlineVariant = Color(0xFF46454F),
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
