package app.duoodo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import app.duoodo.model.Stroke

@Composable
fun DoodleCanvas(state: DrawingState, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    state.start(down.position.x / w, down.position.y / h)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        state.move(change.position.x / w, change.position.y / h)
                        change.consume()
                    }
                    state.end()
                }
            }
    ) {
        drawRect(Color.White)
        state.strokes.forEach { drawDoodleStroke(it.points, it.color, it.width) }
        state.current?.let { drawDoodleStroke(it, state.color, state.width) }
    }
}

/** Read-only rendering of a doodle's strokes. */
@Composable
fun DoodleView(strokes: List<Stroke>, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        drawRect(Color.White)
        strokes.forEach { drawDoodleStroke(it.points, it.color, it.width) }
    }
}

private fun DrawScope.drawDoodleStroke(points: FloatArray, color: Int, width: Float) {
    if (points.size < 2) return
    val side = size.minDimension
    val c = Color(color)
    if (points.size == 2) {
        drawCircle(c, radius = width * side / 2, center = Offset(points[0] * side, points[1] * side))
        return
    }
    val path = Path().apply {
        moveTo(points[0] * side, points[1] * side)
        var i = 2
        while (i < points.size) {
            lineTo(points[i] * side, points[i + 1] * side)
            i += 2
        }
    }
    drawPath(path, c, style = DrawStroke(width * side, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
