package app.duoodo.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.duoodo.model.Doodle
import app.duoodo.model.Stroke

/** Renders doodles off-screen (used by the widget, which can't host a Compose canvas). */
object DoodleRenderer {
    fun toBitmap(doodle: Doodle, sizePx: Int, background: Int = Color.WHITE): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in doodle.strokes) {
            paint.color = stroke.color
            paint.strokeWidth = stroke.width * sizePx
            drawStroke(canvas, stroke, sizePx.toFloat(), paint)
        }
        return bitmap
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke, size: Float, paint: Paint) {
        val p = stroke.points
        if (p.size < 2) return
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
}
