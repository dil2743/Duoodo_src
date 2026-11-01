package app.duoodo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import app.duoodo.model.ImageMark
import app.duoodo.model.TextMark
import app.duoodo.model.Mark
import app.duoodo.render.DoodleRenderer
import app.duoodo.ui.theme.Paper

/**
 * Editable canvas. Drawing tools take drags; text tools report a tap position;
 * emoji and image tools allow dynamic drag, pinch-to-scale, and rotation.
 */
@Composable
fun DoodleCanvas(state: DrawingState, onTap: (x: Float, y: Float) -> Unit, modifier: Modifier = Modifier) {
    val selectionColor = if (MaterialTheme.colorScheme.primary.luminance() > 0.5f) {
        MaterialTheme.colorScheme.onPrimary
    } else MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(state, state.tool) {
                if (state.tool == DrawingState.Tool.SELECT) {
                    try {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val side = size.width.toFloat()
                            val resizing = state.selectedMark?.let {
                                isResizeHandleHit(it, down.position, side, 24.dp.toPx())
                            } == true
                            val index = if (resizing) state.selectedIndex else DoodleRenderer.hitTest(
                                state.marks, down.position.x, down.position.y, side, 8.dp.toPx(),
                            )
                            down.consume()
                            if (index == null) {
                                state.select(null)
                                return@awaitEachGesture
                            }
                            state.startTransform(index)
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.isConsumed }) {
                                    state.cancel()
                                    break
                                }
                                if (event.changes.all { !it.pressed }) {
                                    state.endTransform()
                                    break
                                }
                                if (resizing && event.changes.count { it.pressed } == 1) {
                                    val change = event.changes.first { it.pressed }
                                    val center = state.selectedMark?.let { markCenter(it, side) } ?: break
                                    val previousDistance = (change.previousPosition - center).getDistance()
                                    val distance = (change.position - center).getDistance()
                                    if (previousDistance > 1f) state.updateTransform(0f, 0f, distance / previousDistance, 0f)
                                } else {
                                    val pan = event.calculatePan()
                                    state.updateTransform(pan.x / side, pan.y / side, event.calculateZoom(), event.calculateRotation())
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } finally {
                        state.cancel()
                    }
                    return@pointerInput
                }
                if (state.tool == DrawingState.Tool.EMOJI || state.tool == DrawingState.Tool.IMAGE) {
                    try {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        state.startPlacement(down.position.x / w, down.position.y / h)
                        down.consume()

                        var active = true
                        while (active) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) {
                                state.cancel()
                                break
                            }
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
                    } finally {
                        state.cancel()
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
        state.selectedMark?.let { mark ->
            val bounds = DoodleRenderer.markBounds(mark, size.minDimension) ?: return@let
            val center = markCenter(mark, size.minDimension)
            rotate(markRotation(mark), center) {
                drawRect(
                    selectionColor, Offset(bounds.left, bounds.top), Size(bounds.width(), bounds.height()),
                    style = Stroke(1.5.dp.toPx()),
                )
                val handle = Offset(bounds.right, bounds.bottom)
                drawCircle(Color.White, 9.dp.toPx(), handle)
                drawCircle(selectionColor, 9.dp.toPx(), handle, style = Stroke(2.dp.toPx()))
                drawCircle(selectionColor, 3.dp.toPx(), handle)
            }
        }
    }
}

private fun markCenter(mark: Mark, side: Float): Offset = when (mark) {
    is ImageMark -> Offset(mark.x * side, mark.y * side)
    is TextMark -> Offset(mark.x * side, mark.y * side)
    else -> Offset.Zero
}

private fun markRotation(mark: Mark): Float = when (mark) {
    is ImageMark -> mark.rotation
    is TextMark -> mark.rotation
    else -> 0f
}

private fun selectionHandle(mark: Mark, side: Float): Offset? {
    val bounds = DoodleRenderer.markBounds(mark, side) ?: return null
    val center = markCenter(mark, side)
    val point = floatArrayOf(bounds.right, bounds.bottom)
    android.graphics.Matrix().apply { setRotate(markRotation(mark), center.x, center.y) }.mapPoints(point)
    return Offset(point[0], point[1])
}

internal fun isResizeHandleHit(mark: Mark, position: Offset, side: Float, radius: Float): Boolean {
    val handle = selectionHandle(mark, side) ?: return false
    val distance = (position - handle).getDistance()
    return distance <= radius && distance < (position - markCenter(mark, side)).getDistance()
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
