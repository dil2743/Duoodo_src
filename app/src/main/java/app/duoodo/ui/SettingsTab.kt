package app.duoodo.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import app.duoodo.data.Settings as AppSettings
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.duoodo.ui.components.Footer
import app.duoodo.ui.components.SectionCard
import app.duoodo.widget.DuoodoWidget
import kotlin.math.roundToInt

private const val LATEST_RELEASE_URL = "https://github.com/dil2743/Duoodo/releases/latest"

internal fun latestReleaseIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_RELEASE_URL))

internal fun shareAppIntent(): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Duoodo")
        putExtra(Intent.EXTRA_TEXT, "Doodle together with Duoodo: $LATEST_RELEASE_URL")
    },
    "Share Duoodo",
)

private fun Context.launchSettingsIntent(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No app available for this action", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SettingsTab(vm: MainViewModel) {
    val context = LocalContext.current
    val power = context.getSystemService(PowerManager::class.java)
    var exempt by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    var widgetPlaced by remember { mutableStateOf(DuoodoWidget.isPlaced(context)) }
    LifecycleResumeEffect(Unit) {
        exempt = power.isIgnoringBatteryOptimizations(context.packageName)
        widgetPlaced = DuoodoWidget.isPlaced(context)
        onPauseOrDispose { }
    }
    SettingsContent(
        instantDelivery = vm.instantDelivery,
        readReceipts = vm.readReceipts,
        widgetStyle = vm.widgetStyle,
        widgetOpacity = vm.widgetOpacity,
        widgetColor = vm.widgetColor,
        safetyEmojis = vm.safetyEmojis,
        exempt = exempt,
        widgetPlaced = widgetPlaced,
        canPinWidget = DuoodoWidget.canRequestPin(context),
        onInstantDelivery = vm::changeInstantDelivery,
        onReadReceipts = vm::changeReadReceipts,
        onWidgetStyle = vm::changeWidgetStyle,
        onWidgetOpacity = vm::changeWidgetOpacity,
        onWidgetColor = vm::changeWidgetColor,
        onPinWidget = { DuoodoWidget.requestPin(context, it) },
        onBatterySettings = {
            @SuppressLint("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(intent) }
        },
        onUnpair = vm::unpair,
        onShareApp = { context.launchSettingsIntent(shareAppIntent()) },
        onCheckForUpdates = { context.launchSettingsIntent(latestReleaseIntent()) },
    )
}

@Composable
fun SettingsContent(
    instantDelivery: Boolean,
    readReceipts: Boolean,
    widgetStyle: AppSettings.WidgetStyle,
    widgetOpacity: Float,
    widgetColor: Int,
    safetyEmojis: List<String>?,
    exempt: Boolean,
    widgetPlaced: Boolean,
    canPinWidget: Boolean,
    onInstantDelivery: (Boolean) -> Unit,
    onReadReceipts: (Boolean) -> Unit,
    onWidgetStyle: (AppSettings.WidgetStyle) -> Unit,
    onWidgetOpacity: (Float) -> Unit,
    onWidgetColor: (Int) -> Unit,
    onPinWidget: (DuoodoWidget.Size) -> Unit,
    onBatterySettings: () -> Unit,
    onUnpair: () -> Unit,
    onShareApp: () -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    var confirmUnpair by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.widthIn(max = 600.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionCard("Delivery") {
            SettingRow(
                icon = Icons.Rounded.Bolt,
                title = "Instant delivery",
                subtitle = if (instantDelivery) "Doodles arrive within seconds" else "Checks about every 10 minutes",
                trailing = { Switch(checked = instantDelivery, onCheckedChange = onInstantDelivery, modifier = Modifier.semantics { contentDescription = "Instant delivery" }) },
                onClick = { onInstantDelivery(!instantDelivery) },
            )
            if (instantDelivery) {
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = if (exempt) "Runs in background" else "Allow background running",
                    subtitle = if (exempt) "Battery optimization is off for Duoodo"
                    else "Recommended. Stops your phone from pausing delivery",
                    trailing = { if (!exempt) Chevron() },
                    onClick = if (exempt) null else onBatterySettings,
                )
            }
        }

        SectionCard("Home screen") {
            SettingRow(
                icon = Icons.Rounded.Widgets,
                title = if (widgetPlaced) "Widget on home screen" else "Add widget",
                subtitle = if (widgetPlaced) "Your partner's latest doodle is on your home screen"
                else if (canPinWidget) "See their latest doodle without opening the app"
                else "Touch & hold home screen → Widgets → Duoodo",
                trailing = { if (canPinWidget) Chevron() },
                onClick = if (canPinWidget) {
                    { onPinWidget(DuoodoWidget.Size.SQUARE_2X2) }
                } else null,
            )
            if (canPinWidget) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Add size to home screen",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.size(8.dp))
                    DuoodoWidget.Size.entries.chunked(2).forEach { sizes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sizes.forEach { size ->
                            FilledTonalButton(
                                onClick = { onPinWidget(size) },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(size.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(size.subtitle, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    }
                }
            }
            RowDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Widget look", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val styles = AppSettings.WidgetStyle.entries
                    styles.forEachIndexed { i, style ->
                        SegmentedButton(
                            selected = widgetStyle == style,
                            onClick = { onWidgetStyle(style) },
                            shape = SegmentedButtonDefaults.itemShape(i, styles.size),
                        ) { Text(style.label) }
                    }
                }
                Spacer(Modifier.size(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Opacity", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${(widgetOpacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value = widgetOpacity,
                    onValueChange = onWidgetOpacity,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Widget opacity" },
                )
                if (widgetOpacity > 0.01f) {
                    Spacer(Modifier.size(14.dp))
                    Text("Background tint", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(AppSettings.FROSTED_PALETTE) { colorInt ->
                            val selected = widgetColor == colorInt
                            val color = Color(colorInt)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = "Tint #${Integer.toHexString(colorInt).takeLast(6)}" }
                                    .selectable(selected, role = Role.RadioButton, onClick = { onWidgetColor(colorInt) })
                                    .padding(6.dp)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape,
                                    )
                                    .padding(if (selected) 3.dp else 1.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }

        SectionCard("Privacy") {
            SettingRow(
                icon = Icons.Rounded.Visibility,
                title = "Read receipts",
                subtitle = "Let your partner know when you've taken a peek at their doodle",
                trailing = {
                    Switch(checked = readReceipts, onCheckedChange = onReadReceipts, modifier = Modifier.semantics { contentDescription = "Read receipts" })
                },
            )
        }

        safetyEmojis?.let { emojis ->
            SectionCard("Security") {
                SettingRow(
                    icon = Icons.Rounded.VerifiedUser,
                    title = emojis.joinToString("  "),
                    subtitle = "Safety code. If your partner's screen shows the same emojis, nobody is listening in.",
                )
            }
        }

        SectionCard {
            SettingRow(
                icon = Icons.Rounded.LinkOff,
                title = "Unpair",
                subtitle = "Disconnect from your partner",
                tint = MaterialTheme.colorScheme.error,
                onClick = { confirmUnpair = true },
            )
        }

        SectionCard("App") {
            SettingRow(
                icon = Icons.Rounded.Share,
                title = "Share Duoodo",
                trailing = { Chevron() },
                onClick = onShareApp,
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.SystemUpdate,
                title = "Check for updates",
                trailing = { Chevron() },
                onClick = onCheckForUpdates,
            )
        }

        Footer()
    }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair?") },
            text = { Text("Your partner will be told, and you'll both need a new invite to pair again. The received doodle is deleted.") },
            confirmButton = {
                TextButton(onClick = { confirmUnpair = false; onUnpair() }) {
                    Text("Unpair", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(12.dp))
            trailing()
        }
    }
}

@Composable
private fun Chevron() = Icon(
    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun RowDivider() = HorizontalDivider(
    Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
)
