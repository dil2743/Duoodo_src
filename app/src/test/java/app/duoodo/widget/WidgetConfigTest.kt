package app.duoodo.widget

import app.duoodo.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConfigTest {
    @Test
    fun allWidgetSizesAreConfiguredWithUniqueReceivers() {
        val sizes = DuoodoWidget.Size.entries
        assertEquals(4, sizes.size)
        val receiverClasses = sizes.map { it.receiverClass }.toSet()
        assertEquals("Each size must have a distinct receiver class", 4, receiverClasses.size)
        assertTrue(receiverClasses.contains(DuoodoWidgetReceiver::class.java))
        assertTrue(receiverClasses.contains(DuoodoWidgetReceiver2x4::class.java))
        assertTrue(receiverClasses.contains(DuoodoWidgetReceiver2x3::class.java))
        assertTrue(receiverClasses.contains(DuoodoWidgetReceiver1x4::class.java))
        assertEquals(4, DuoodoWidget.ALL_RECEIVERS.size)
    }

    @Test
    fun widgetSizeLabelsMatchExpectedDimensions() {
        assertEquals("2×2", DuoodoWidget.Size.SQUARE_2X2.label)
        assertEquals("2×4", DuoodoWidget.Size.WIDE_2X4.label)
        assertEquals("2×3", DuoodoWidget.Size.MEDIUM_2X3.label)
        assertEquals("1×4", DuoodoWidget.Size.BANNER_1X4.label)
    }

    @Test
    fun widgetStylesHaveExpectedPresets() {
        val styles = Settings.WidgetStyle.entries
        assertEquals(3, styles.size)
        assertEquals("Solid", Settings.WidgetStyle.SOLID.label)
        assertEquals("Frosted", Settings.WidgetStyle.FROSTED.label)
        assertEquals("Clear", Settings.WidgetStyle.CLEAR.label)
    }
}
