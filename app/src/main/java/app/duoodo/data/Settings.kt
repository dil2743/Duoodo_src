package app.duoodo.data

import android.content.Context

/** Non-secret user preferences. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Keep a live connection (foreground service) so doodles arrive within seconds. */
    var instantDelivery: Boolean
        get() = prefs.getBoolean(INSTANT, true)
        set(value) = prefs.edit().putBoolean(INSTANT, value).apply()

    enum class WidgetStyle(val label: String) { SOLID("Solid"), FROSTED("Frosted"), CLEAR("Clear") }

    /** How see-through the home-screen widget is. */
    var widgetStyle: WidgetStyle
        get() = runCatching { WidgetStyle.valueOf(prefs.getString(WIDGET_STYLE, null)!!) }.getOrDefault(WidgetStyle.FROSTED)
        set(value) = prefs.edit().putString(WIDGET_STYLE, value.name).apply()

    /** Background opacity for the home-screen widget (0f = fully clear, 1f = fully solid). */
    var widgetOpacity: Float
        get() = prefs.getFloat(WIDGET_OPACITY, when (widgetStyle) {
            WidgetStyle.CLEAR -> 0f
            WidgetStyle.SOLID -> 1f
            WidgetStyle.FROSTED -> 0.55f
        })
        set(value) = prefs.edit().putFloat(WIDGET_OPACITY, value.coerceIn(0f, 1f)).apply()

    /** Background color for the widget when frosted or solid. */
    var widgetColor: Int
        get() = prefs.getInt(WIDGET_COLOR, 0xFFFFFFFF.toInt())
        set(value) = prefs.edit().putInt(WIDGET_COLOR, value).apply()

    /** Whether to send read receipts when viewing the partner's doodle. */
    var readReceipts: Boolean
        get() = prefs.getBoolean(READ_RECEIPTS, true)
        set(value) = prefs.edit().putBoolean(READ_RECEIPTS, value).apply()

    companion object {
        private const val INSTANT = "instant_delivery"
        private const val WIDGET_STYLE = "widget_style"
        private const val WIDGET_OPACITY = "widget_opacity"
        private const val WIDGET_COLOR = "widget_color"
        private const val READ_RECEIPTS = "read_receipts"

        val FROSTED_PALETTE = listOf(
            0xFFFFFFFF.toInt(), // Pure White
            0xFF1B1B23.toInt(), // Midnight Black
            0xFFFFE0B2.toInt(), // Warm Peach
            0xFFE1BEE7.toInt(), // Soft Lavender
            0xFFB3E5FC.toInt(), // Sky Blue
            0xFFC8E6C9.toInt(), // Mint Green
            0xFFFFF9C4.toInt(), // Lemon Cream
            0xFFFFCDD2.toInt(), // Rose Pink
        )
    }
}
