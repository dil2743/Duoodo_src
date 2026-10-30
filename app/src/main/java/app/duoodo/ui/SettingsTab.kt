package app.duoodo.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.duoodo.ui.components.Footer
import app.duoodo.ui.components.SectionCard
import app.duoodo.widget.DuoodoWidget
import kotlin.math.roundToInt

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
    var confirmUnpair by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionCard("Delivery") {
            SettingRow(
                icon = Icons.Rounded.Bolt,
                title = "Instant delivery",
                subtitle = if (vm.instantDelivery) "Doodles arrive within seconds" else "Checks about every 10 minutes",
                trailing = { Switch(checked = vm.instantDelivery, onCheckedChange = vm::changeInstantDelivery) },
                onClick = { vm.changeInstantDelivery(!vm.instantDelivery) },
            )
            if (vm.instantDelivery) {
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = if (exempt) "Runs in background" else "Allow background running",
                    subtitle = if (exempt) "Battery optimization is off for Duoodo"
                    else "Recommended. Stops your phone from pausing delivery",
                    trailing = { if (!exempt) Chevron() },
                    onClick = if (exempt) null else {
                        {
                            @SuppressLint("BatteryLife")
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                            runCatching { context.startActivity(intent) }
                        }
                    },
                )
            }
        }

        SectionCard("Home screen") {
            SettingRow(
                icon = Icons.Rounded.Widgets,
                title = if (widgetPlaced) "Widget on home screen" else "Add widget",
                subtitle = if (widgetPlaced) "Your partner's latest doodle is on your home screen"
                else "See their latest doodle without opening the app",
            )
            if (DuoodoWidget.canRequestPin(context)) {
                Column(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp)) {
                    Text(
                        "Add size to home screen",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.size(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DuoodoWidget.Size.entries.forEach { size ->
                            FilledTonalButton(
                                onClick = { DuoodoWidget.requestPin(context, size) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(size.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(size.subtitle, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            RowDivider()
            Column(Modifier.padding(start = 56.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)) {
                Text("Widget look", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Frosted and Clear let your wallpaper show through",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val styles = AppSettings.WidgetStyle.entries
                    styles.forEachIndexed { i, style ->
                        SegmentedButton(
                            selected = vm.widgetStyle == style,
                            onClick = { vm.changeWidgetStyle(style) },
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
                        "${(vm.widgetOpacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value = vm.widgetOpacity,
                    onValueChange = vm::changeWidgetOpacity,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        vm.safetyEmojis?.let { emojis ->
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

        Footer()
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair?") },
            text = { Text("Your partner will be told, and you'll both need a new invite to pair again. The received doodle is deleted.") },
            confirmButton = {
                TextButton(onClick = { confirmUnpair = false; vm.unpair() }) {
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
