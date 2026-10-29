package app.duoodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.duoodo.model.Doodle
import app.duoodo.model.DoodleCodec
import app.duoodo.model.Mark
import app.duoodo.model.Stroke
import app.duoodo.model.TextMark

/** Editable doodle being drawn. Positions are kept normalized (0..1). */
class DrawingState {
    enum class Tool { PEN, TEXT }

    val marks = mutableStateListOf<Mark>()
    var tool by mutableStateOf(Tool.PEN)
    var current by mutableStateOf<FloatArray?>(null)
        private set
    var color by mutableStateOf(PALETTE.first())
    var width by mutableStateOf(WIDTHS[1])
    var textSize by mutableStateOf(TEXT_SIZES[1])

    private var pending = FloatArrayBuilder()
    private var totalPoints = 0
    private var lastX = 0f
    private var lastY = 0f

    val canUndo: Boolean get() = marks.isNotEmpty()

    fun start(x: Float, y: Float) {
        if (marks.size >= DoodleCodec.MAX_MARKS) return
        pending = FloatArrayBuilder()
        add(x, y)
    }

    fun move(x: Float, y: Float) {
        if (current == null) return
        val dx = x - lastX
        val dy = y - lastY
        if (dx * dx + dy * dy < MIN_STEP * MIN_STEP) return
        add(x, y)
    }

    fun end() {
        val pts = current ?: return
        marks += Stroke(pts, color, width)
        current = null
    }

    /** Remaining bytes for one more text, or 0 if no more text fits. */
    val textBudget: Int
        get() {
            if (marks.size >= DoodleCodec.MAX_MARKS) return 0
            val used = marks.filterIsInstance<TextMark>().sumOf { it.text.toByteArray().size }
            return minOf(DoodleCodec.MAX_TEXT_BYTES, DoodleCodec.MAX_TOTAL_TEXT_BYTES - used).coerceAtLeast(0)
        }

    /** Adds text centered at (x, y); returns false if it doesn't fit the limits. */
    fun addText(text: String, x: Float, y: Float): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.toByteArray().size > textBudget) return false
        marks += TextMark(t, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), color, textSize)
        return true
    }

    fun undo() {
        when (val m = marks.removeLastOrNull()) {
            is Stroke -> totalPoints -= m.pointCount
            else -> Unit
        }
    }

    fun clear() {
        marks.clear()
        current = null
        totalPoints = 0
    }

    fun snapshot(nowMillis: Long): Doodle = Doodle(marks.toList(), nowMillis)

    private fun add(x: Float, y: Float) {
        if (totalPoints >= DoodleCodec.MAX_TOTAL_POINTS) return
        lastX = x
        lastY = y
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
        /** Minimum distance between recorded points, as a fraction of the canvas side. */
        private const val MIN_STEP = 0.003f
        val WIDTHS = listOf(0.006f, 0.014f, 0.03f)
        val TEXT_SIZES = listOf(0.045f, 0.07f, 0.11f)
    }
}
