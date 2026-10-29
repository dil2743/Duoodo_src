package app.duoodo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import app.duoodo.model.Mark
import app.duoodo.model.Stroke
import app.duoodo.render.DoodleRenderer

/**
 * Editable canvas. With the pen tool, drags draw strokes; with the text tool, a tap reports the
 * normalized position through [onTextTap] so the caller can ask for the text.
 */
@Composable
fun DoodleCanvas(state: DrawingState, onTextTap: (x: Float, y: Float) -> Unit, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(state, state.tool) {
                if (state.tool == DrawingState.Tool.TEXT) {
                    detectTapGestures { onTextTap(it.x / size.width, it.y / size.height) }
                    return@pointerInput
                }
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
        val inProgress = state.current?.let { Stroke(it, state.color, state.width) }
        drawDoodle(if (inProgress == null) state.marks else state.marks + inProgress)
    }
}

/** Read-only rendering of a doodle. */
@Composable
fun DoodleView(marks: List<Mark>, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) { drawDoodle(marks) }
}

private fun DrawScope.drawDoodle(marks: List<Mark>) {
    drawRect(Color.White)
    drawIntoCanvas { DoodleRenderer.drawMarks(it.nativeCanvas, marks, size.minDimension) }
}
