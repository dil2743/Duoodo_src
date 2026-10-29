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
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.layout.size
import app.duoodo.R
import app.duoodo.data.Settings
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
            // Glance keeps this session alive between updates, so read the doodle and style inside
            // the composition, keyed on a version that refreshAll() bumps. Reading them once above
            // would keep showing whatever was current when the session started.
            val version = currentState(VERSION) ?: 0L
            val style = remember(version) { Settings(context).widgetStyle }
            val doodle = remember(version) { store.read()?.takeIf { !it.isEmpty } }
            val size = LocalSize.current
            val side = minOf(size.width, size.height)
            val inset = if (style == Settings.WidgetStyle.FROSTED) 10.dp else 0.dp
            val sidePx = ((side - inset * 2).value * density).toInt().coerceIn(64, 800)
            val bitmap = remember(version, sidePx, style) {
                doodle?.let {
                    if (style == Settings.WidgetStyle.CLEAR) DoodleRenderer.toTransparentBitmap(it, sidePx)
                    else DoodleRenderer.toBitmap(it, sidePx)
                }
            }
            val card = when (style) {
                Settings.WidgetStyle.SOLID -> ColorProvider(day = Color.White, night = Color.White)
                // Translucent card that follows the system theme; the wallpaper shows through.
                Settings.WidgetStyle.FROSTED -> ColorProvider(day = Color(0x8CFFFFFF), night = Color(0x8C1B1B23))
                Settings.WidgetStyle.CLEAR -> ColorProvider(Color.Transparent)
            }
            Box(
                GlanceModifier
                    .fillMaxSize()
                    .background(card)
                    .cornerRadius(24.dp)
                    .padding(inset)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(ImageProvider(R.drawable.ic_widget_bird), null, GlanceModifier.size(36.dp))
                        Text(
                            "No doodle yet",
                            style = TextStyle(
                                color = when (style) {
                                    Settings.WidgetStyle.SOLID -> ColorProvider(Color(0xFF5E5D6E))
                                    else -> ColorProvider(day = Color(0xFF1B1B23), night = Color(0xFFE5E3EE))
                                },
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                } else {
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = "Latest doodle from your partner",
                        modifier = if (style == Settings.WidgetStyle.FROSTED) GlanceModifier.cornerRadius(16.dp) else GlanceModifier,
                    )
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
