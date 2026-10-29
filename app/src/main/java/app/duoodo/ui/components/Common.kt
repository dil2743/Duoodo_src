package app.duoodo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.res.painterResource
import app.duoodo.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.duoodo.BuildConfig
import app.duoodo.ui.theme.BrandGradientEnd
import app.duoodo.ui.theme.BrandGradientStart
import app.duoodo.ui.theme.MetaStyle

/**
 * The app's logo (bird chirping "nizz") on its gradient tile. Uses the launcher icon's own
 * foreground vector, so the in-app logo always matches the icon on the home screen.
 */
@Composable
fun BirdLogo(size: Dp = 36.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd))),
        contentAlignment = Alignment.Center,
    ) {
        // The foreground is a 108-unit canvas whose visible part is the middle 72 units.
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

/** A titled, rounded group of settings or info, like the cards in system Settings. */
@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) { Column(Modifier.padding(vertical = 4.dp), content = content) }
    }
}

/** Faded developer + version line at the very bottom of the app. */
@Composable
fun Footer(modifier: Modifier = Modifier) {
    Text(
        "Duoodo v${BuildConfig.VERSION_NAME} · made by Dilshad",
        style = MetaStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
    )
}

/** Friendly sketch of an empty envelope for empty states. */
@Composable
fun EmptyIllustration(modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(120.dp)) {
        val w = size.width
        val stroke = Stroke(width = w * 0.035f, cap = StrokeCap.Round)
        val env = Path().apply {
            moveTo(w * 0.18f, w * 0.38f); lineTo(w * 0.82f, w * 0.38f); lineTo(w * 0.82f, w * 0.78f)
            lineTo(w * 0.18f, w * 0.78f); close()
            moveTo(w * 0.18f, w * 0.38f); lineTo(w * 0.5f, w * 0.6f); lineTo(w * 0.82f, w * 0.38f)
        }
        drawPath(env, line, style = stroke)
        val squiggle = Path().apply {
            moveTo(w * 0.36f, w * 0.24f)
            cubicTo(w * 0.42f, w * 0.12f, w * 0.5f, w * 0.30f, w * 0.56f, w * 0.18f)
            cubicTo(w * 0.60f, w * 0.10f, w * 0.66f, w * 0.20f, w * 0.68f, w * 0.14f)
        }
        drawPath(squiggle, accent, style = stroke)
    }
}
