package app.duoodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.duoodo.model.Doodle
import app.duoodo.model.DoodleCodec
import app.duoodo.model.Stroke

/** Editable doodle being drawn. Points are kept normalized (0..1). */
class DrawingState {
    val strokes = mutableStateListOf<Stroke>()
    var current by mutableStateOf<FloatArray?>(null)
        private set
    var color by mutableStateOf(PALETTE.first())
    var width by mutableStateOf(WIDTHS[1])

    private var pending = FloatArrayBuilder()
    private var totalPoints = 0

    val canUndo: Boolean get() = strokes.isNotEmpty()

    fun start(x: Float, y: Float) {
        if (strokes.size >= DoodleCodec.MAX_STROKES) return
        pending = FloatArrayBuilder()
        add(x, y)
    }

    fun move(x: Float, y: Float) {
        if (current == null) return
        add(x, y)
    }

    fun end() {
        val pts = current ?: return
        strokes += Stroke(pts, color, width)
        current = null
    }

    fun undo() {
        strokes.removeLastOrNull()?.let { totalPoints -= it.pointCount }
    }

    fun clear() {
        strokes.clear()
        current = null
        totalPoints = 0
    }

    fun snapshot(nowMillis: Long): Doodle = Doodle(strokes.toList(), nowMillis)

    private fun add(x: Float, y: Float) {
        if (totalPoints >= DoodleCodec.MAX_TOTAL_POINTS) return
        pending.add(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        totalPoints++
        current = pending.toArray()
    }

    private class FloatArrayBuilder {
        private var data = FloatArray(64)
        private var size = 0
        fun add(x: Float, y: Float) {
            if (size + 2 > data.size) data = data.copyOf(data.size * 2)
            data[size++] = x
            data[size++] = y
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }

    companion object {
        val PALETTE = listOf(
            0xFF1F1F1F.toInt(), 0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(),
            0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFEC407A.toInt(),
        )
        val WIDTHS = listOf(0.006f, 0.014f, 0.03f)
    }
}
