package app.duoodo.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.duoodo.model.Doodle
import app.duoodo.model.ImageMark
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

    fun toBitmap(doodle: Doodle, widthPx: Int, heightPx: Int, background: Int = Color.WHITE): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (background != Color.TRANSPARENT) canvas.drawColor(background)
        val side = min(widthPx, heightPx).toFloat()
        val offsetX = (widthPx - side) / 2f
        val offsetY = (heightPx - side) / 2f
        canvas.save()
        canvas.translate(offsetX, offsetY)
        drawMarks(canvas, doodle.marks, side)
        canvas.restore()
        return bitmap
    }

    fun toBitmap(doodle: Doodle, sizePx: Int, background: Int = Color.WHITE): Bitmap =
        toBitmap(doodle, sizePx, sizePx, background)

    /**
     * Renders the doodle on a transparent background for drawing straight onto the wallpaper.
     * Each mark gets a soft dark halo so it reads on light and dark wallpapers alike, and
     * white "eraser" strokes are dropped since there's no paper to rub out.
     */
    fun toTransparentBitmap(doodle: Doodle, widthPx: Int, heightPx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val marks = doodle.marks.filterNot { it is Stroke && it.color == Color.WHITE }
        val side = min(widthPx, heightPx).toFloat()
        val offsetX = (widthPx - side) / 2f
        val offsetY = (heightPx - side) / 2f

        // Halo pass: the same marks, blurred and darkened, underneath.
        val halo = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val haloCanvas = Canvas(halo)
        haloCanvas.save()
        haloCanvas.translate(offsetX, offsetY)
        drawMarks(haloCanvas, marks, side)
        haloCanvas.restore()

        val alpha = halo.extractAlpha()
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x59000000
            maskFilter = android.graphics.BlurMaskFilter(side * 0.006f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(alpha, 0f, side * 0.003f, shadow)
        alpha.recycle()
        halo.recycle()

        canvas.save()
        canvas.translate(offsetX, offsetY)
        drawMarks(canvas, marks, side)
        canvas.restore()
        return bitmap
    }

    fun toTransparentBitmap(doodle: Doodle, sizePx: Int): Bitmap =
        toTransparentBitmap(doodle, sizePx, sizePx)

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
            is ImageMark -> drawImage(canvas, mark, side)
        }
    }

    private fun imageBitmap(mark: ImageMark): Bitmap? = mark.cachedBitmap
        ?: android.graphics.BitmapFactory.decodeByteArray(mark.bytes, 0, mark.bytes.size)?.also { mark.cachedBitmap = it }

    fun markBounds(mark: Mark, side: Float): RectF? {
        if (side <= 0f) return null
        return when (mark) {
            is ImageMark -> {
                val bitmap = imageBitmap(mark) ?: return null
                val halfWidth = side * mark.scale / 2f
                val halfHeight = halfWidth * bitmap.height / bitmap.width
                RectF(mark.x * side - halfWidth, mark.y * side - halfHeight, mark.x * side + halfWidth, mark.y * side + halfHeight)
            }
            is TextMark -> if (mark.text.isEmpty()) null else textBounds(mark, side, textLayout(mark, side))
            else -> null
        }
    }

    fun hitTest(marks: List<Mark>, x: Float, y: Float, side: Float, tolerance: Float = 0f): Int? {
        return marks.indices.reversed().firstOrNull { index ->
            val mark = marks[index]
            val bounds = markBounds(mark, side) ?: return@firstOrNull false
            val point = floatArrayOf(x, y)
            when (mark) {
                is ImageMark -> Matrix().apply { setRotate(-mark.rotation, mark.x * side, mark.y * side) }.mapPoints(point)
                is TextMark -> Matrix().apply { setRotate(-mark.rotation, mark.x * side, mark.y * side) }.mapPoints(point)
                else -> return@firstOrNull false
            }
            bounds.inset(-tolerance, -tolerance)
            bounds.contains(point[0], point[1])
        }
    }

    private fun drawImage(canvas: Canvas, mark: ImageMark, side: Float) {
        if (mark.bytes.isEmpty() || side <= 0f) return
        val bmp = imageBitmap(mark) ?: return
        canvas.save()
        canvas.translate(mark.x * side, mark.y * side)
        canvas.rotate(mark.rotation)
        val destWidth = side * mark.scale
        val destHeight = destWidth * bmp.height / bmp.width
        canvas.scale(destWidth / bmp.width, destHeight / bmp.height)
        canvas.translate(-bmp.width / 2f, -bmp.height / 2f)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        canvas.restore()
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
        canvas.drawPath(smoothPath(p, size), paint)
    }

    /**
     * Smooths a polyline into quadratic curves through the midpoints of each segment, so freehand
     * strokes look drawn rather than jagged. Short polylines (shapes: lines, boxes) stay straight.
     */
    private fun smoothPath(p: FloatArray, size: Float): Path {
        val path = Path()
        path.moveTo(p[0] * size, p[1] * size)
        val n = p.size / 2
        if (n <= 5) {
            for (i in 1 until n) path.lineTo(p[2 * i] * size, p[2 * i + 1] * size)
            return path
        }
        for (i in 1 until n - 1) {
            val x = p[2 * i] * size
            val y = p[2 * i + 1] * size
            val mx = (x + p[2 * i + 2] * size) / 2
            val my = (y + p[2 * i + 3] * size) / 2
            path.quadTo(x, y, mx, my)
        }
        path.lineTo(p[2 * n - 2] * size, p[2 * n - 1] * size)
        return path
    }

    /** Centers the text on its anchor, wrapping long lines and nudging the block to stay on the canvas. */
    private fun textLayout(mark: TextMark, side: Float): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mark.color
            textSize = mark.size * side
            typeface = Typeface.DEFAULT_BOLD
        }
        val maxWidth = side * MAX_TEXT_WIDTH
        val width = min(ceil(StaticLayout.getDesiredWidth(mark.text, paint)), maxWidth).toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(mark.text, 0, mark.text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
    }

    private fun textBounds(mark: TextMark, side: Float, layout: StaticLayout): RectF {
        val width = layout.width
        val left = (mark.x * side - width / 2f).coerceIn(0f, side - width)
        val top = (mark.y * side - layout.height / 2f).coerceIn(0f, (side - layout.height).coerceAtLeast(0f))
        return RectF(left, top, left + width, top + layout.height)
    }

    private fun drawText(canvas: Canvas, mark: TextMark, side: Float) {
        if (mark.text.isEmpty() || side <= 0f) return
        val layout = textLayout(mark, side)
        val bounds = textBounds(mark, side, layout)
        canvas.save()
        canvas.rotate(mark.rotation, mark.x * side, mark.y * side)
        canvas.translate(bounds.left, bounds.top)
        layout.draw(canvas)
        canvas.restore()
    }
}
