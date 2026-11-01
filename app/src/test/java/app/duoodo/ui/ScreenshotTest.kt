package app.duoodo.ui

import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.duoodo.model.Doodle
import app.duoodo.model.ImageMark
import app.duoodo.model.Stroke
import app.duoodo.model.TextMark
import app.duoodo.ui.theme.DuoodoTheme
import app.duoodo.render.DoodleRenderer
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders key screens on the JVM (no device) in light and dark, for visual review.
 * Run: ./gradlew recordPaparazziDebug — images land in app/src/test/snapshots/images.
 */
@RunWith(Parameterized::class)
class ScreenshotTest(private val night: Boolean) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "night={0}")
        fun modes() = listOf(false, true)
    }

    private val deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = if (night) NightMode.NIGHT else NightMode.NOTNIGHT)

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = deviceConfig,
        showSystemUi = false,
        maxPercentDifference = 1.0,
    )

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <Input, Output> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<Input, Output>,
                input: Input,
                options: ActivityOptionsCompat?,
            ) = error("Activity launches are not supported in screenshots")
        }
    }

    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
            DuoodoTheme(dark = night) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

    private val sample = Doodle(
        listOf(
            Stroke(FloatArray(120) { i -> if (i % 2 == 0) 0.15f + i / 240f else 0.35f + 0.08f * sin(i / 6f) }, 0xFF1E88E5.toInt(), 0.014f),
            Stroke(FloatArray(98) { i ->
                val t = 2 * PI * (i / 2) / 48
                if (i % 2 == 0) (0.72 + 0.12 * cos(t)).toFloat() else (0.68 + 0.12 * sin(t)).toFloat()
            }, 0xFFE53935.toInt(), 0.014f),
            TextMark("Hi there 👋", 0.45f, 0.18f, 0xFF5E35B1.toInt(), 0.08f),
        ),
        System.currentTimeMillis() - 5 * 60_000,
    )

    @Test fun drawEmpty() = shot { DrawContent(DrawingState(), busy = false, onSend = {}) }

    @Test fun drawWithShapes() = shot {
        val d = DrawingState()
        d.tool = DrawingState.Tool.PEN
        sample.marks.filterIsInstance<Stroke>().forEach { s ->
            d.start(s.points[0], s.points[1]); var i = 2
            while (i < s.points.size) { d.move(s.points[i], s.points[i + 1]); i += 2 }
            d.end()
        }
        d.tool = DrawingState.Tool.ARROW; d.color = 0xFF43A047.toInt()
        d.start(0.2f, 0.85f); d.move(0.5f, 0.6f); d.end()
        d.tool = DrawingState.Tool.EMOJI
        d.stamp(0.82f, 0.2f)
        d.tool = DrawingState.Tool.PEN
        DrawContent(d, busy = false, onSend = {})
    }

    @Test fun drawEmojiTool() = shot {
        val d = DrawingState(); d.tool = DrawingState.Tool.EMOJI
        DrawContent(d, busy = false, onSend = {})
    }

    @Test fun drawShapeTool() = shot {
        val drawing = remember { DrawingState().apply { tool = DrawingState.Tool.BOX } }
        DrawContent(drawing, busy = false, onSend = {})
    }

    @Test fun drawTextLarge() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig.copy(screenWidth = 320, screenHeight = 640, density = com.android.resources.Density.MEDIUM, fontScale = 1.5f))
        shot {
            val drawing = remember { DrawingState().apply { tool = DrawingState.Tool.TEXT } }
            DrawContent(drawing, busy = false, onSend = {})
        }
    }

    @Test fun drawSending() = shot {
        val drawing = remember {
            DrawingState().apply {
                start(0.25f, 0.5f)
                move(0.75f, 0.5f)
                end()
            }
        }
        DrawContent(drawing, busy = true, onSend = {}, status = "Sending your doodle to your partner")
    }

    @Test fun drawPeekedCompact() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig.copy(screenWidth = 320, screenHeight = 640, density = com.android.resources.Density.MEDIUM))
        shot { DrawContent(remember { DrawingState() }, busy = false, onSend = {}, partnerPeeked = true) }
    }

    @Test fun drawPeekedWithStatus() = shot {
        DrawContent(remember { DrawingState() }, busy = false, onSend = {}, status = "Sent", partnerPeeked = true)
    }

    @Test fun selectedEmojiCompact() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig.copy(screenWidth = 320, screenHeight = 640, density = com.android.resources.Density.MEDIUM))
        shot {
            val drawing = remember {
                DrawingState().apply {
                    chooseEmoji("❤️")
                    startPlacement(0.5f, 0.5f)
                    endPlacement()
                    val emoji = marks.single()
                    val bounds = DoodleRenderer.markBounds(emoji, 280f)!!
                    assertFalse(isResizeHandleHit(emoji, androidx.compose.ui.geometry.Offset(140f, 140f), 280f, 24f))
                    assertTrue(isResizeHandleHit(emoji, androidx.compose.ui.geometry.Offset(bounds.right, bounds.bottom), 280f, 24f))
                    resizeSelection(0.25f)
                    endTransform()
                }
            }
            DrawContent(drawing, busy = false, onSend = {})
        }
    }

    @Test fun selectedImageAndRotatedHitBounds() = shot {
        val drawing = remember {
            val bitmap = android.graphics.Bitmap.createBitmap(80, 40, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xFF83BFA4.toInt())
            android.graphics.Canvas(bitmap).drawCircle(40f, 20f, 12f, android.graphics.Paint().apply { color = 0xFFFFD7B7.toInt() })
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
            DrawingState().apply {
                chooseImage(stream.toByteArray())
                startPlacement(0.5f, 0.5f)
                updatePlacement(0f, 0f, 1f, 90f)
                endPlacement()
                val image = marks.single() as ImageMark
                assertEquals(0, DoodleRenderer.hitTest(marks, 500f, 650f, 1000f))
                assertNull(DoodleRenderer.hitTest(marks, 650f, 500f, 1000f))
                assertEquals(1, DoodleRenderer.hitTest(listOf(image, image), 500f, 500f, 1000f))
                val text = TextMark("❤️", 0f, 0f, 0xFF000000.toInt(), 0.15f)
                val bounds = DoodleRenderer.markBounds(text, 1000f)!!
                assertEquals(0, DoodleRenderer.hitTest(listOf(text), bounds.centerX(), bounds.centerY(), 1000f))
            }
        }
        DrawContent(drawing, busy = false, onSend = {})
    }

    @Test fun inboxEmpty() = shot { InboxContent(null, false, {}, {}, {}) }

    @Test fun inboxWithDoodle() = shot { InboxContent(sample, false, {}, {}, {}) }

    @Suppress("DEPRECATION")
    @Test fun appReleaseIntents() {
        val releaseUrl = "https://github.com/dil2743/Duoodo/releases/latest"
        val update = latestReleaseIntent()
        assertEquals(Intent.ACTION_VIEW, update.action)
        assertEquals(releaseUrl, update.data.toString())

        val chooser = shareAppIntent()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val share = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("text/plain", share.type)
        assertEquals("Duoodo", share.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Doodle together with Duoodo: $releaseUrl", share.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(share.hasExtra(Intent.EXTRA_STREAM))
    }

    @Composable
    private fun home(panel: HomePanel? = null, drawing: DrawingState? = null, partnerPeeked: Boolean = false) {
        val drawingState = drawing ?: remember { DrawingState() }
        val settings: @Composable () -> Unit = {
                SettingsContent(
                    instantDelivery = true,
                    readReceipts = true,
                    widgetStyle = app.duoodo.data.Settings.WidgetStyle.entries.first(),
                    widgetOpacity = 0.7f,
                    widgetColor = app.duoodo.data.Settings.FROSTED_PALETTE.first(),
                    safetyEmojis = listOf("💜", "🌸", "✨", "🌙", "🍓", "💛"),
                    exempt = false,
                    widgetPlaced = false,
                    canPinWidget = true,
                    onInstantDelivery = {},
                    onReadReceipts = {},
                    onWidgetStyle = {},
                    onWidgetOpacity = {},
                    onWidgetColor = {},
                    onPinWidget = {},
                    onBatterySettings = {},
                    onUnpair = {},
                    onShareApp = {},
                    onCheckForUpdates = {},
                )
        }
        if (panel != null) {
            HomePanelContent(panel, onClose = {}) {
                when (panel) {
                    HomePanel.INBOX -> InboxContent(sample, false, {}, {}, {})
                    HomePanel.SETTINGS -> settings()
                }
            }
        } else {
            HomeContent(
                panel = null,
                onPanelChange = {},
                unseen = true,
                live = false,
                snackbar = remember { SnackbarHostState() },
                draw = { DrawContent(drawingState, busy = false, onSend = {}, partnerPeeked = partnerPeeked) },
                inbox = { InboxContent(sample, false, {}, {}, {}) },
                settings = settings,
            )
        }
    }

    @Test fun homeEmpty() = shot { home() }

    private fun sampleNote() = DrawingState().apply {
        color = 0xFFAD4565.toInt()
        textSize = 0.065f
        addText("Thinking of you", 0.5f, 0.25f)
        chooseEmoji("❤️")
        startPlacement(0.5f, 0.52f)
        endPlacement()
        resizeSelection(0.24f)
        endTransform()
    }

    @Test fun homeWithDoodle() = shot {
        val drawing = remember { sampleNote().apply { chooseTool(DrawingState.Tool.PEN) } }
        home(drawing = drawing, partnerPeeked = true)
    }

    @Test fun homeEmojiPicker() = shot {
        val drawing = remember { sampleNote().apply { chooseTool(DrawingState.Tool.EMOJI) } }
        home(drawing = drawing)
    }

    @Test fun homeSelectedEmoji() = shot {
        home(drawing = remember { sampleNote() })
    }

    @Test fun homeCompact() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig.copy(screenWidth = 320, screenHeight = 640, density = com.android.resources.Density.MEDIUM))
        shot { home() }
    }

    @Test fun homeLandscape() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig.copy(screenWidth = 360, screenHeight = 800, density = com.android.resources.Density.MEDIUM, orientation = com.android.resources.ScreenOrientation.LANDSCAPE))
        shot { home() }
    }

    @Test fun homeInbox() = shot { home(HomePanel.INBOX) }

    @Test fun homeSettings() = shot { home(HomePanel.SETTINGS) }

    @Test fun homeLargeText() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig.copy(screenWidth = 320, screenHeight = 640, density = com.android.resources.Density.MEDIUM, fontScale = 1.5f))
        shot { home() }
    }

    @Test fun settingsCompact() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig.copy(screenWidth = 320, screenHeight = 640, density = com.android.resources.Density.MEDIUM, fontScale = 1.5f))
        shot { home(HomePanel.SETTINGS) }
    }

    @Test fun safetyCompact() {
        paparazzi.unsafeUpdateConfig(deviceConfig = deviceConfig.copy(screenWidth = 320, screenHeight = 640, density = com.android.resources.Density.MEDIUM, fontScale = 1.5f))
        shot { VerifySafetyContent(listOf("💜", "🌸", "✨", "🌙", "🍓", "💛"), {}, {}) }
    }

    /**
     * Mock of the three widget looks on a wallpaper, built from the same renderer the widget uses.
     * (Glance widgets can't be rendered off-device, so this mirrors DuoodoWidget's layout.)
     */
    @Test fun widgetStyles() = paparazzi.snapshot {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val px = 300
        val solid = app.duoodo.render.DoodleRenderer.toBitmap(sample, px).asImageBitmap()
        val clear = app.duoodo.render.DoodleRenderer.toTransparentBitmap(sample, px).asImageBitmap()
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    if (night) listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                    else listOf(Color(0xFFFFB199), Color(0xFFFF6A88), Color(0xFF7F7FD5)),
                ),
            ),
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                listOf("Solid", "Frosted", "Clear").forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shape = RoundedCornerShape(24.dp)
                        Box(
                            Modifier.size(170.dp).clip(shape).background(
                                when (i) {
                                    0 -> Color.White
                                    1 -> if (night) Color(0x8C1B1B23) else Color(0x8CFFFFFF)
                                    else -> Color.Transparent
                                },
                            ).padding(if (i == 1) 10.dp else 0.dp),
                        ) {
                            androidx.compose.foundation.Image(
                                if (i == 2) clear else solid, null,
                                Modifier.fillMaxSize().clip(if (i == 1) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp)),
                            )
                        }
                        Text(
                            label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                }
            }
        }
    }
}
