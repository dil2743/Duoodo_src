package app.duoodo.model

/**
 * One continuous pen stroke. Coordinates are normalized to 0..1 on a square canvas so a doodle
 * renders identically on any screen or widget size.
 *
 * @param points interleaved x,y pairs
 * @param width stroke width as a fraction of the canvas side
 */
class Stroke(val points: FloatArray, val color: Int, val width: Float) {
    val pointCount: Int get() = points.size / 2

    override fun equals(other: Any?): Boolean =
        other is Stroke && color == other.color && width == other.width && points.contentEquals(other.points)

    override fun hashCode(): Int = (points.contentHashCode() * 31 + color) * 31 + width.hashCode()
}

data class Doodle(val strokes: List<Stroke>, val createdAtMillis: Long) {
    val isEmpty: Boolean get() = strokes.isEmpty()
}
