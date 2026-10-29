package app.duoodo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import app.duoodo.data.LatestDoodleStore
import app.duoodo.render.DoodleRenderer
import app.duoodo.ui.MainActivity

class DuoodoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = LatestDoodleStore(context)
        val density = context.resources.displayMetrics.density
        provideContent {
            // Glance keeps this session alive between updates, so read the doodle inside the
            // composition, keyed on a version that refreshAll() bumps. Reading it once above
            // would keep showing the doodle that was current when the session started.
            val version = currentState(VERSION) ?: 0L
            val doodle = remember(version) { store.read() }
            val size = LocalSize.current
            val sidePx = (minOf(size.width.value, size.height.value) * density).toInt().coerceIn(64, 800)
            val bitmap = remember(version, sidePx) { doodle?.takeIf { !it.isEmpty }?.let { DoodleRenderer.toBitmap(it, sidePx) } }
            Box(
                GlanceModifier
                    .fillMaxSize()
                    .background(Color.White)
                    .cornerRadius(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap == null) {
                    Text("No doodle yet ✏️", style = androidx.glance.text.TextStyle(color = ColorProvider(Color.Gray)))
                } else {
                    Image(provider = ImageProvider(bitmap), contentDescription = "Latest doodle from your partner")
                }
            }
        }
    }

    companion object {
        private val VERSION = longPreferencesKey("doodle_version")

        fun canRequestPin(context: Context): Boolean =
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

        /** True if at least one Duoodo widget is on a home screen. */
        fun isPlaced(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, DuoodoWidgetReceiver::class.java)).isNotEmpty()

        /** Asks the launcher to place the widget; the user confirms in a system dialog. */
        fun requestPin(context: Context) {
            AppWidgetManager.getInstance(context)
                .requestPinAppWidget(ComponentName(context, DuoodoWidgetReceiver::class.java), null, null)
        }

        /** Redraws every placed widget from the latest stored doodle. */
        suspend fun refreshAll(context: Context) {
            val widget = DuoodoWidget()
            val stamp = System.nanoTime()
            GlanceAppWidgetManager(context).getGlanceIds(DuoodoWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { it[VERSION] = stamp }
                widget.update(context, id)
            }
        }
    }
}

class DuoodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DuoodoWidget()
}
