package app.duoodo.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.duoodo.model.Doodle
import app.duoodo.model.Mark
import app.duoodo.model.Stroke
import app.duoodo.model.TextMark
import kotlin.math.ceil
import kotlin.math.min

/**
 * Draws doodles onto an Android canvas. The app's Compose canvases and the widget bitmap both
 * go through here, so a doodle looks the same everywhere.
 */
object DoodleRenderer {
    /** Text wraps once a line would exceed this fraction of the canvas width. */
    private const val MAX_TEXT_WIDTH = 0.9f

    fun toBitmap(doodle: Doodle, sizePx: Int, background: Int = Color.WHITE): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        drawMarks(canvas, doodle.marks, sizePx.toFloat())
        return bitmap
    }

    /** Draws [marks] in order onto a square region of side [side] at the canvas origin. */
    fun drawMarks(canvas: Canvas, marks: List<Mark>, side: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (mark in marks) when (mark) {
            is Stroke -> drawStroke(canvas, mark, side, paint)
            is TextMark -> drawText(canvas, mark, side)
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke, size: Float, paint: Paint) {
        val p = stroke.points
        if (p.size < 2) return
        paint.color = stroke.color
        paint.strokeWidth = stroke.width * size
        if (p.size == 2) {
            // A tap: draw a dot.
            paint.style = Paint.Style.FILL
            canvas.drawCircle(p[0] * size, p[1] * size, paint.strokeWidth / 2, paint)
            paint.style = Paint.Style.STROKE
            return
        }
        val path = Path().apply {
            moveTo(p[0] * size, p[1] * size)
            var i = 2
            while (i < p.size) {
                lineTo(p[i] * size, p[i + 1] * size)
                i += 2
            }
        }
        canvas.drawPath(path, paint)
    }

    /** Centers the text on its anchor, wrapping long lines and nudging the block to stay on the canvas. */
    private fun drawText(canvas: Canvas, mark: TextMark, side: Float) {
        if (mark.text.isEmpty() || side <= 0f) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mark.color
            textSize = mark.size * side
            typeface = Typeface.DEFAULT_BOLD
        }
        val maxWidth = side * MAX_TEXT_WIDTH
        val width = min(ceil(StaticLayout.getDesiredWidth(mark.text, paint)), maxWidth).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(mark.text, 0, mark.text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        val left = (mark.x * side - width / 2f).coerceIn(0f, side - width)
        val top = (mark.y * side - layout.height / 2f).coerceIn(0f, (side - layout.height).coerceAtLeast(0f))
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }
}
