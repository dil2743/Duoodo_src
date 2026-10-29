package app.duoodo.ui

import app.duoodo.model.DoodleCodec
import app.duoodo.model.Stroke
import app.duoodo.model.TextMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingStateTest {
    private fun DrawingState.drag(vararg pts: Float) {
        start(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { move(pts[i], pts[i + 1]); i += 2 }
        end()
    }

    @Test
    fun arrowIsOneUndoStepOfThreeStrokes() {
        val d = DrawingState()
        d.tool = DrawingState.Tool.ARROW
        d.drag(0.1f, 0.1f, 0.5f, 0.5f)
        assertEquals(3, d.marks.size)
        d.undo()
        assertTrue(d.isEmpty)
        d.redo()
        assertEquals(3, d.marks.size)
    }

    @Test
    fun rainbowMakesManyColoursInOneStep() {
        val d = DrawingState()
        d.tool = DrawingState.Tool.RAINBOW
        d.drag(*FloatArray(80) { i -> if (i % 2 == 0) 0.1f + i * 0.01f else 0.5f })
        val colours = d.marks.map { (it as Stroke).color }.toSet()
        assertTrue("several hues, got ${colours.size}", colours.size >= 3)
        d.undo()
        assertTrue(d.isEmpty)
    }

    @Test
    fun tinyShapeIsIgnoredButTapWithPenMakesADot() {
        val d = DrawingState()
        d.tool = DrawingState.Tool.BOX
        d.drag(0.5f, 0.5f, 0.501f, 0.5f)
        assertTrue(d.isEmpty)
        d.tool = DrawingState.Tool.PEN
        d.drag(0.5f, 0.5f)
        assertEquals(1, d.marks.size)
    }

    @Test
    fun markerIsTranslucentAndEraserIsPaperWhite() {
        val d = DrawingState()
        d.color = 0xFFE53935.toInt()
        d.tool = DrawingState.Tool.MARKER
        d.drag(0.1f, 0.1f, 0.4f, 0.4f)
        assertTrue((d.marks[0] as Stroke).color ushr 24 < 0xFF)
        d.tool = DrawingState.Tool.ERASER
        d.drag(0.1f, 0.1f, 0.4f, 0.4f)
        assertEquals(0xFFFFFFFF.toInt(), (d.marks[1] as Stroke).color)
    }

    @Test
    fun emojiStampsAreTextAndEverythingStillEncodes() {
        val d = DrawingState()
        d.tool = DrawingState.Tool.EMOJI
        d.emoji = "🐦"
        assertTrue(d.stamp(0.3f, 0.3f))
        assertEquals("🐦", (d.marks[0] as TextMark).text)
        listOf(DrawingState.Tool.CIRCLE, DrawingState.Tool.LINE, DrawingState.Tool.RAINBOW).forEach {
            d.tool = it
            d.drag(0.2f, 0.2f, 0.6f, 0.7f, 0.8f, 0.8f)
        }
        val back = DoodleCodec.decode(DoodleCodec.encode(d.snapshot(1)))
        assertEquals(d.marks.size, back.marks.size)
    }

    @Test
    fun newActionClearsRedo() {
        val d = DrawingState()
        d.drag(0.1f, 0.1f, 0.2f, 0.2f)
        d.undo()
        assertTrue(d.canRedo)
        d.drag(0.3f, 0.3f, 0.4f, 0.4f)
        assertFalse(d.canRedo)
    }
}
