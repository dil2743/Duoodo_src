package app.duoodo.model

/** One element of a doodle. Coordinates are normalized to 0..1 on a square canvas. */
sealed interface Mark

/**
 * One continuous pen stroke. Coordinates are normalized to 0..1 on a square canvas so a doodle
 * renders identically on any screen or widget size.
 *
 * @param points interleaved x,y pairs
 * @param width stroke width as a fraction of the canvas side
 */
class Stroke(val points: FloatArray, val color: Int, val width: Float) : Mark {
    val pointCount: Int get() = points.size / 2

    override fun equals(other: Any?): Boolean =
        other is Stroke && color == other.color && width == other.width && points.contentEquals(other.points)

    override fun hashCode(): Int = (points.contentHashCode() * 31 + color) * 31 + width.hashCode()
}

/**
 * A piece of text centered on ([x], [y]).
 *
 * @param size text height as a fraction of the canvas side
 */
data class TextMark(val text: String, val x: Float, val y: Float, val color: Int, val size: Float) : Mark

/** @param marks drawn in order, so later marks paint over earlier ones */
data class Doodle(val marks: List<Mark>, val createdAtMillis: Long) {
    val isEmpty: Boolean get() = marks.isEmpty()
    val strokes: List<Stroke> get() = marks.filterIsInstance<Stroke>()
    val texts: List<TextMark> get() = marks.filterIsInstance<TextMark>()
}
