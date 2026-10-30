package app.duoodo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import app.duoodo.model.Mark
import app.duoodo.render.DoodleRenderer
import app.duoodo.ui.theme.Paper

/**
 * Editable canvas. Drawing tools take drags; text tools report a tap position;
 * emoji and image tools allow dynamic drag, pinch-to-scale, and rotation.
 */
@Composable
fun DoodleCanvas(state: DrawingState, onTap: (x: Float, y: Float) -> Unit, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(state, state.tool) {
                if (state.tool == DrawingState.Tool.EMOJI || state.tool == DrawingState.Tool.IMAGE) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        state.startPlacement(down.position.x / w, down.position.y / h)
                        down.consume()

                        var active = true
                        while (active) {
                            val event = awaitPointerEvent()
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val rotation = event.calculateRotation()

                            state.updatePlacement(pan.x / w, pan.y / h, zoom, rotation)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }

                            if (event.changes.all { !it.pressed }) {
                                active = false
                            }
                        }
                        state.endPlacement()
                    }
                    return@pointerInput
                }
                if (!state.tool.usesWidth) {
                    detectTapGestures { onTap(it.x / size.width, it.y / size.height) }
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
                        // A second finger means the user isn't drawing (e.g. resting a palm): drop the stroke.
                        if (event.changes.size > 1) {
                            state.cancel()
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        state.move(change.position.x / w, change.position.y / h)
                        change.consume()
                    }
                    state.end()
                }
            }
    ) {
        drawDoodle(state.marks + state.preview)
    }
}

/** Read-only rendering of a doodle. */
@Composable
fun DoodleView(marks: List<Mark>, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) { drawDoodle(marks) }
}

private fun DrawScope.drawDoodle(marks: List<Mark>) {
    drawRect(Paper)
    drawIntoCanvas { DoodleRenderer.drawMarks(it.nativeCanvas, marks, size.minDimension) }
}
