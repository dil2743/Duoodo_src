package app.duoodo.data

import android.content.Context

/** Non-secret user preferences. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Keep a live connection (foreground service) so doodles arrive within seconds. */
    var instantDelivery: Boolean
        get() = prefs.getBoolean(INSTANT, true)
        set(value) = prefs.edit().putBoolean(INSTANT, value).apply()

    private companion object {
        const val INSTANT = "instant_delivery"
    }
}
