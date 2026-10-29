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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val doodle = LatestDoodleStore(context).read()
        val density = context.resources.displayMetrics.density
        provideContent {
            val size = LocalSize.current
            val sidePx = (minOf(size.width.value, size.height.value) * density).toInt().coerceIn(64, 800)
            Box(
                GlanceModifier
                    .fillMaxSize()
                    .background(Color.White)
                    .cornerRadius(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                if (doodle == null || doodle.isEmpty) {
                    Text("No doodle yet ✏️", style = androidx.glance.text.TextStyle(color = ColorProvider(Color.Gray)))
                } else {
                    Image(
                        provider = ImageProvider(DoodleRenderer.toBitmap(doodle, sidePx)),
                        contentDescription = "Latest doodle from your partner",
                    )
                }
            }
        }
    }

    companion object {
        fun canRequestPin(context: Context): Boolean =
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

        /** Asks the launcher to place the widget; the user confirms in a system dialog. */
        fun requestPin(context: Context) {
            AppWidgetManager.getInstance(context)
                .requestPinAppWidget(ComponentName(context, DuoodoWidgetReceiver::class.java), null, null)
        }

        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val widget = DuoodoWidget()
            manager.getGlanceIds(DuoodoWidget::class.java).forEach { widget.update(context, it) }
        }
    }
}

class DuoodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DuoodoWidget()
}
