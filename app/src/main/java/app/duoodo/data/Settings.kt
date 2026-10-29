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

    private companion object {
        const val INSTANT = "instant_delivery"
        const val WIDGET_STYLE = "widget_style"
    }
}
