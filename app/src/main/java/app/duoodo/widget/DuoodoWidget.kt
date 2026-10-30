package app.duoodo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
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
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.duoodo.R
import app.duoodo.data.LatestDoodleStore
import app.duoodo.data.Settings
import app.duoodo.render.DoodleRenderer
import app.duoodo.ui.MainActivity

class DuoodoWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    enum class Size(
        val label: String,
        val subtitle: String,
        val receiverClass: Class<out GlanceAppWidgetReceiver>,
    ) {
        SQUARE_2X2("2×2", "Square", DuoodoWidgetReceiver::class.java),
        WIDE_2X4("2×4", "Wide", DuoodoWidgetReceiver2x4::class.java),
        MEDIUM_2X3("2×3", "Medium", DuoodoWidgetReceiver2x3::class.java),
        BANNER_1X4("1×4", "Banner", DuoodoWidgetReceiver1x4::class.java),
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = LatestDoodleStore(context)
        val density = context.resources.displayMetrics.density
        provideContent {
            val version = currentState(VERSION) ?: 0L
            val settings = remember(version) { Settings(context) }
            val opacity = settings.widgetOpacity
            val doodle = remember(version) { store.read()?.takeIf { !it.isEmpty } }
            val size = LocalSize.current

            val inset = if (opacity > 0.01f && opacity < 0.99f) 10.dp else 0.dp
            val availW = (size.width - inset * 2).coerceAtLeast(32.dp)
            val availH = (size.height - inset * 2).coerceAtLeast(32.dp)
            val widthPx = ((availW.value * density).toInt()).coerceIn(64, 1200)
            val heightPx = ((availH.value * density).toInt()).coerceIn(64, 1200)

            val bitmap = remember(version, widthPx, heightPx, opacity) {
                doodle?.let {
                    if (opacity < 0.65f) DoodleRenderer.toTransparentBitmap(it, widthPx, heightPx)
                    else DoodleRenderer.toBitmap(it, widthPx, heightPx, android.graphics.Color.TRANSPARENT)
                }
            }

            val card = when {
                opacity <= 0.01f -> ColorProvider(day = Color.Transparent, night = Color.Transparent)
                opacity >= 0.99f -> ColorProvider(day = Color.White, night = Color.White)
                else -> ColorProvider(
                    day = Color.White.copy(alpha = opacity),
                    night = Color(0xFF1B1B23).copy(alpha = opacity),
                )
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
                    val textColor = when {
                        opacity >= 0.9f -> ColorProvider(day = Color(0xFF5E5D6E), night = Color(0xFF5E5D6E))
                        else -> ColorProvider(day = Color(0xFF1B1B23), night = Color(0xFFE5E3EE))
                    }
                    if (size.height < 90.dp) {
                        Row(
                            modifier = GlanceModifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(ImageProvider(R.drawable.ic_widget_bird), null, GlanceModifier.size(24.dp))
                            Spacer(GlanceModifier.size(8.dp))
                            Text(
                                "No doodle yet",
                                style = TextStyle(
                                    color = textColor,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(ImageProvider(R.drawable.ic_widget_bird), null, GlanceModifier.size(36.dp))
                            Spacer(GlanceModifier.size(4.dp))
                            Text(
                                "No doodle yet",
                                style = TextStyle(
                                    color = textColor,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                } else {
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = "Latest doodle from your partner",
                        modifier = if (inset > 0.dp) GlanceModifier.cornerRadius(16.dp).fillMaxSize() else GlanceModifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    companion object {
        private val VERSION = longPreferencesKey("doodle_version")

        val ALL_RECEIVERS = listOf(
            DuoodoWidgetReceiver::class.java,
            DuoodoWidgetReceiver2x4::class.java,
            DuoodoWidgetReceiver2x3::class.java,
            DuoodoWidgetReceiver1x4::class.java,
        )

        fun canRequestPin(context: Context): Boolean =
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

        /** True if at least one Duoodo widget is on a home screen. */
        fun isPlaced(context: Context): Boolean {
            val awm = AppWidgetManager.getInstance(context)
            return ALL_RECEIVERS.any { awm.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        }

        /** Asks the launcher to place the widget of the chosen [size]; the user confirms in a system dialog. */
        fun requestPin(context: Context, size: Size = Size.SQUARE_2X2) {
            AppWidgetManager.getInstance(context)
                .requestPinAppWidget(ComponentName(context, size.receiverClass), null, null)
        }

        /** Redraws every placed widget from the latest stored doodle. */
        suspend fun refreshAll(context: Context) {
            val widget = DuoodoWidget()
            val stamp = System.nanoTime()
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = runCatching { manager.getGlanceIds(DuoodoWidget::class.java) }.getOrDefault(emptyList())
            glanceIds.forEach { id ->
                runCatching {
                    updateAppWidgetState(context, id) { it[VERSION] = stamp }
                    widget.update(context, id)
                }
            }
        }
    }
}

class DuoodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DuoodoWidget()
}

class DuoodoWidgetReceiver2x4 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DuoodoWidget()
}

class DuoodoWidgetReceiver2x3 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DuoodoWidget()
}

class DuoodoWidgetReceiver1x4 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DuoodoWidget()
}
