package app.duoodo.ui

import app.duoodo.model.DoodleCodec
import app.duoodo.model.ImageMark
import app.duoodo.model.Stroke
import app.duoodo.model.TextMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingStateTest {
    @Test
    fun curatedEmojiPaletteIsUniqueAndRoundTripsWithoutRepeatedInsertion() {
        assertEquals(72, DrawingState.EMOJIS.size)
        assertEquals(DrawingState.EMOJIS.size, DrawingState.EMOJIS.distinct().size)
        val unrelated = setOf("🐶", "🐱", "🐦", "✈️", "📍", "⚡", "❄️", "🌊", "✅", "❌", "💯")
        assertTrue(DrawingState.EMOJIS.none { it in unrelated })
        DrawingState.EMOJIS.forEach { emoji ->
            val drawing = DrawingState()
            drawing.chooseEmoji(emoji)
            drawing.startPlacement(0.5f, 0.5f)
            drawing.endPlacement()
            drawing.startPlacement(0.7f, 0.7f)
            drawing.endPlacement()
            assertEquals("Insert once: $emoji", 1, drawing.marks.size)
            val decoded = DoodleCodec.decode(DoodleCodec.encode(drawing.snapshot(1)))
            assertEquals(emoji, (decoded.marks.single() as TextMark).text)
        }
    }

    @Test
    fun emojiSelectionInsertsOnlyOnceUntilAnotherEmojiIsChosen() {
        val drawing = DrawingState()
        drawing.chooseEmoji("❤️")
        drawing.startPlacement(0.3f, 0.3f)
        drawing.endPlacement()
        drawing.startPlacement(0.7f, 0.7f)
        drawing.endPlacement()
        assertEquals(1, drawing.marks.size)
        assertEquals(0, drawing.selectedIndex)
        assertEquals(DrawingState.Tool.SELECT, drawing.tool)
        drawing.chooseEmoji("❤️")
        drawing.startPlacement(0.7f, 0.7f)
        drawing.endPlacement()
        assertEquals(2, drawing.marks.size)
    }

    @Test
    fun photoInsertsOnceAndTransformsWithoutDuplicatingItsBytes() {
        val drawing = DrawingState()
        val bytes = byteArrayOf(1, 2, 3)
        drawing.chooseImage(bytes)
        drawing.startPlacement(0.4f, 0.4f)
        drawing.endPlacement()
        drawing.startPlacement(0.7f, 0.7f)
        drawing.endPlacement()
        val budget = drawing.imageBudget
        drawing.startTransform(0)
        drawing.updateTransform(0.1f, 0.2f, 1.5f, 30f)
        drawing.endTransform()
        val image = drawing.marks.single() as ImageMark
        assertEquals(0.5f, image.x, 0.0001f)
        assertEquals(0.6f, image.y, 0.0001f)
        assertEquals(0.6f, image.scale, 0.0001f)
        assertEquals(30f, image.rotation, 0.0001f)
        assertTrue(bytes === image.bytes)
        assertEquals(budget, drawing.imageBudget)
    }

    @Test
    fun transformIsOneUndoStepAndKeepsLayerOrder() {
        val drawing = DrawingState()
        drawing.chooseEmoji("❤️")
        drawing.startPlacement(0.3f, 0.3f)
        drawing.endPlacement()
        val original = drawing.marks.single()
        drawing.chooseTool(DrawingState.Tool.PEN)
        drawing.drag(0.1f, 0.1f, 0.2f, 0.2f)
        val stroke = drawing.marks.last()
        drawing.startTransform(0)
        repeat(3) { drawing.updateTransform(0.1f, 0f, 1.1f, 5f) }
        drawing.endTransform()
        val transformed = drawing.marks.first()
        assertEquals(stroke, drawing.marks.last())
        drawing.undo()
        assertEquals(original, drawing.marks.first())
        assertEquals(stroke, drawing.marks.last())
        drawing.redo()
        assertEquals(transformed, drawing.marks.first())
        drawing.undo()
        drawing.undo()
        assertEquals(listOf(original), drawing.marks.toList())
        drawing.undo()
        assertTrue(drawing.isEmpty)
    }

    @Test
    fun cancelledTransformRestoresOriginalAndSelectionDoesNotAddHistory() {
        val drawing = DrawingState()
        drawing.chooseEmoji("❤️")
        drawing.startPlacement(0.5f, 0.5f)
        drawing.endPlacement()
        val original = drawing.marks.single()
        drawing.startTransform(0)
        drawing.updateTransform(0.2f, 0.1f, 2f, 90f)
        drawing.cancel()
        assertEquals(original, drawing.marks.single())
        drawing.startTransform(0)
        drawing.endTransform()
        drawing.undo()
        assertTrue(drawing.isEmpty)
    }

    @Test
    fun sliderResizeIsUndoableAndNewTransformClearsRedo() {
        val drawing = DrawingState()
        drawing.chooseEmoji("❤️")
        drawing.startPlacement(0.5f, 0.5f)
        drawing.endPlacement()
        val original = drawing.marks.single()
        drawing.resizeSelection(0.2f)
        drawing.resizeSelection(0.3f)
        drawing.endTransform()
        assertEquals(0.3f, (drawing.marks.single() as TextMark).size, 0.0001f)
        drawing.undo()
        assertEquals(original, drawing.marks.single())
        assertTrue(drawing.canRedo)
        drawing.startTransform(0)
        drawing.updateTransform(0.1f, 0f, 1f, 0f)
        drawing.endTransform()
        assertFalse(drawing.canRedo)
    }

    @Test
    fun fullCanvasCanStillTransformAndEncodeExistingItems() {
        val drawing = DrawingState()
        drawing.chooseEmoji("❤️")
        drawing.startPlacement(0.5f, 0.5f)
        drawing.endPlacement()
        drawing.chooseTool(DrawingState.Tool.PEN)
        repeat(DoodleCodec.MAX_MARKS - 1) { drawing.drag(0.1f, 0.1f) }
        drawing.startTransform(0)
        drawing.updateTransform(10f, -10f, 100f, 450f)
        drawing.endTransform()
        val emoji = drawing.marks.first() as TextMark
        assertEquals(1f, emoji.x)
        assertEquals(0f, emoji.y)
        assertEquals(1f, emoji.size)
        assertEquals(90f, emoji.rotation)
        val decoded = DoodleCodec.decode(DoodleCodec.encode(drawing.snapshot(1)))
        assertEquals(DoodleCodec.MAX_MARKS, decoded.marks.size)
        assertEquals(emoji.size, (decoded.marks.first() as TextMark).size, 0.0001f)
    }

    @Test
    fun cancelledPlacementCanBeRetriedAndToolChangeCancelsAnEdit() {
        val drawing = DrawingState()
        drawing.chooseEmoji("❤️")
        drawing.startPlacement(0.3f, 0.3f)
        drawing.cancel()
        drawing.endPlacement()
        assertTrue(drawing.isEmpty)
        drawing.startPlacement(0.5f, 0.5f)
        drawing.endPlacement()
        val original = drawing.marks.single()
        drawing.startTransform(0)
        drawing.updateTransform(0.2f, 0f, 2f, 0f)
        drawing.chooseTool(DrawingState.Tool.PEN)
        assertEquals(original, drawing.marks.single())
        assertEquals(null, drawing.selectedIndex)
        drawing.startTransform(0)
        drawing.updateTransform(Float.NaN, 0f, 1f, 0f)
        drawing.endTransform()
        drawing.undo()
        assertTrue(drawing.isEmpty)
    }

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
