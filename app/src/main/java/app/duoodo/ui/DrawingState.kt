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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Editable doodle being drawn. Positions are normalized (0..1).
 *
 * Every tool produces plain [Stroke]s and [TextMark]s, so the wire format and the partner's app
 * don't need to know about tools. One user action (e.g. an arrow, or a rainbow stroke made of
 * many coloured segments) can add several marks; undo/redo treat it as a single step.
 */
class DrawingState {
    /** Ordered by how often they're reached for, so the first screenful holds the essentials. */
    enum class Tool(val label: String) {
        PEN("Pen"), TEXT("Text"), EMOJI("Emoji"), ARROW("Arrow"), ERASER("Eraser"),
        MARKER("Marker"), RAINBOW("Rainbow"), LINE("Line"), BOX("Box"), CIRCLE("Circle");

        val isFreehand get() = this == PEN || this == MARKER || this == RAINBOW || this == ERASER
        val isShape get() = this == LINE || this == ARROW || this == BOX || this == CIRCLE
        val usesWidth get() = isFreehand || isShape
        val usesColor get() = this != ERASER && this != EMOJI && this != RAINBOW
    }

    /** Committed marks, flattened from [steps] for rendering and sending. */
    val marks = mutableStateListOf<Mark>()

    /** Marks being drawn right now (live preview), not yet committed. */
    var preview by mutableStateOf<List<Mark>>(emptyList())
        private set

    var tool by mutableStateOf(Tool.PEN)
    var color by mutableStateOf(PALETTE.first())
    var width by mutableStateOf(WIDTHS[1])
    var textSize by mutableStateOf(TEXT_SIZES[1])
    var emoji by mutableStateOf(EMOJIS.first())

    private val steps = mutableListOf<Int>()        // number of marks added per undo step
    private val redoStack = mutableListOf<List<Mark>>()
    var redoCount by mutableStateOf(0)
        private set

    private var points = FloatArrayBuilder()
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var travelled = 0f
    private var drawing = false

    val canUndo: Boolean get() = marks.isNotEmpty()
    val canRedo: Boolean get() = redoCount > 0
    val isEmpty: Boolean get() = marks.isEmpty()

    private val totalPoints: Int get() = marks.sumOf { (it as? Stroke)?.pointCount ?: 0 }

    // --- Gestures (normalized coordinates) ---

    fun start(x: Float, y: Float) {
        if (!tool.usesWidth || marks.size >= DoodleCodec.MAX_MARKS) return
        drawing = true
        startX = x; startY = y; lastX = x; lastY = y; travelled = 0f
        points = FloatArrayBuilder().also { it.add(x, y) }
        updatePreview(x, y)
    }

    fun move(x: Float, y: Float) {
        if (!drawing) return
        val dx = x - lastX
        val dy = y - lastY
        val step = hypot(dx, dy)
        if (tool.isFreehand && step < MIN_STEP) return
        travelled += step
        lastX = x; lastY = y
        if (tool.isFreehand && totalPoints + points.size / 2 < DoodleCodec.MAX_TOTAL_POINTS) points.add(x.clamp(), y.clamp())
        updatePreview(x, y)
    }

    fun end() {
        if (!drawing) return
        drawing = false
        val made = preview
        preview = emptyList()
        // A shape with no drag would be invisible; skip it.
        if (tool.isShape && hypot(lastX - startX, lastY - startY) < MIN_SHAPE) return
        commit(made)
    }

    fun cancel() {
        drawing = false
        preview = emptyList()
    }

    private fun updatePreview(x: Float, y: Float) {
        preview = when (tool) {
            Tool.PEN -> listOf(Stroke(points.toArray(), color, width))
            Tool.MARKER -> listOf(Stroke(points.toArray(), withAlpha(color, MARKER_ALPHA), width * MARKER_SCALE))
            Tool.ERASER -> listOf(Stroke(points.toArray(), ERASER_COLOR, width * ERASER_SCALE))
            Tool.RAINBOW -> rainbow(points.toArray(), width)
            Tool.LINE -> listOf(Stroke(floatArrayOf(startX, startY, x.clamp(), y.clamp()), color, width))
            Tool.ARROW -> arrow(startX, startY, x.clamp(), y.clamp(), color, width)
            Tool.BOX -> listOf(Stroke(box(startX, startY, x.clamp(), y.clamp()), color, width))
            Tool.CIRCLE -> listOf(Stroke(ellipse(startX, startY, x.clamp(), y.clamp()), color, width))
            Tool.TEXT, Tool.EMOJI -> emptyList()
        }
    }

    // --- Text & emoji ---

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
        commit(listOf(TextMark(t, x.clamp(), y.clamp(), color, textSize)))
        return true
    }

    /** Stamps the selected emoji at (x, y). */
    fun stamp(x: Float, y: Float): Boolean {
        if (emoji.toByteArray().size > textBudget) return false
        commit(listOf(TextMark(emoji, x.clamp(), y.clamp(), 0xFF000000.toInt(), textSize * EMOJI_SCALE)))
        return true
    }

    // --- History ---

    private fun commit(newMarks: List<Mark>) {
        val fitting = newMarks.take(DoodleCodec.MAX_MARKS - marks.size)
        if (fitting.isEmpty()) return
        marks.addAll(fitting)
        steps += fitting.size
        redoStack.clear()
        redoCount = 0
    }

    fun undo() {
        val n = steps.removeLastOrNull() ?: return
        val removed = marks.subList(marks.size - n, marks.size).toList()
        repeat(n) { marks.removeAt(marks.lastIndex) }
        redoStack += removed
        redoCount = redoStack.size
    }

    fun redo() {
        val again = redoStack.removeLastOrNull() ?: return
        marks.addAll(again)
        steps += again.size
        redoCount = redoStack.size
    }

    fun clear() {
        marks.clear()
        steps.clear()
        redoStack.clear()
        redoCount = 0
        preview = emptyList()
    }

    fun snapshot(nowMillis: Long): Doodle = Doodle(marks.toList(), nowMillis)

    // --- Geometry ---

    private fun rainbow(pts: FloatArray, w: Float): List<Mark> {
        if (pts.size <= 4) return listOf(Stroke(pts, hue(0f), w))
        // Short overlapping segments, each a step further round the colour wheel.
        val out = mutableListOf<Mark>()
        var i = 0
        var seg = 0
        while (i < pts.size - 2) {
            val end = minOf(i + RAINBOW_SEGMENT * 2 + 2, pts.size)
            out += Stroke(pts.copyOfRange(i, end), hue(seg * 24f), w)
            i = end - 2
            seg++
        }
        return out
    }

    private fun arrow(x0: Float, y0: Float, x1: Float, y1: Float, c: Int, w: Float): List<Mark> {
        val angle = atan2(y1 - y0, x1 - x0)
        val head = (hypot(x1 - x0, y1 - y0) * 0.3f).coerceIn(0.03f, 0.08f) + w
        fun wing(spread: Float) = Stroke(
            floatArrayOf(x1, y1, (x1 - head * cos(angle - spread)).clamp(), (y1 - head * sin(angle - spread)).clamp()), c, w,
        )
        return listOf(Stroke(floatArrayOf(x0, y0, x1, y1), c, w), wing(0.5f), wing(-0.5f))
    }

    private fun box(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1, x0, y0)

    private fun ellipse(x0: Float, y0: Float, x1: Float, y1: Float): FloatArray {
        val cx = (x0 + x1) / 2
        val cy = (y0 + y1) / 2
        val rx = kotlin.math.abs(x1 - x0) / 2
        val ry = kotlin.math.abs(y1 - y0) / 2
        return FloatArray((ELLIPSE_POINTS + 1) * 2) { i ->
            val t = (i / 2) * 2 * Math.PI.toFloat() / ELLIPSE_POINTS
            if (i % 2 == 0) (cx + rx * cos(t)).clamp() else (cy + ry * sin(t)).clamp()
        }
    }

    private fun Float.clamp() = coerceIn(0f, 1f)

    private class FloatArrayBuilder {
        private var data = FloatArray(64)
        var size = 0
            private set
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
            0xFF43A047.toInt(), 0xFF00ACC1.toInt(), 0xFF1E88E5.toInt(), 0xFF5E35B1.toInt(),
            0xFFEC407A.toInt(), 0xFF8D6E63.toInt(), 0xFF9E9E9E.toInt(), 0xFFFFFFFF.toInt(),
        )
        val WIDTHS = listOf(0.006f, 0.014f, 0.03f)
        val TEXT_SIZES = listOf(0.045f, 0.07f, 0.11f)
        val EMOJIS = listOf(
            "😀", "😂", "🥰", "😘", "😎", "🤔", "😴", "😭", "😡", "🤯", "🥳", "😇",
            "👍", "👏", "🙏", "💪", "👋", "🤞", "✌️", "🫶", "❤️", "💛", "💙", "💔",
            "⭐", "✨", "🔥", "🌈", "☀️", "🌙", "⚡", "❄️", "🌸", "🌻", "🍀", "🌊",
            "🎉", "🎁", "🎈", "🎂", "☕", "🍕", "🍔", "🍦", "🐶", "🐱", "🐦", "🦋",
            "💤", "💯", "✅", "❌", "❓", "❗", "💬", "📍", "🏠", "✈️", "🎵", "📷",
        )

        /** Minimum distance between recorded freehand points, as a fraction of the canvas side. */
        private const val MIN_STEP = 0.003f
        private const val MIN_SHAPE = 0.01f
        private const val MARKER_ALPHA = 0x66
        private const val MARKER_SCALE = 2.6f
        private const val ERASER_SCALE = 2.2f
        private const val ERASER_COLOR = 0xFFFFFFFF.toInt()
        private const val EMOJI_SCALE = 1.6f
        private const val RAINBOW_SEGMENT = 4
        private const val ELLIPSE_POINTS = 48

        private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

        /** Opaque colour at [degrees] round the colour wheel (HSV with s=0.85, v=0.95). */
        private fun hue(degrees: Float): Int {
            val h = (degrees % 360f) / 60f
            val v = 0.95f
            val c = v * 0.85f
            val x = c * (1 - kotlin.math.abs(h % 2 - 1))
            val (r, g, b) = when (h.toInt()) {
                0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
            }
            val m = v - c
            fun ch(f: Float) = ((f + m) * 255).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
        }
    }
}
