package app.duoodo.ui

import android.text.format.DateUtils
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duoodo.data.Exporter
import app.duoodo.model.Doodle
import app.duoodo.ui.components.EmptyIllustration
import app.duoodo.ui.components.ControlTooltip

@Composable
fun InboxTab(vm: MainViewModel) =
    InboxContent(vm.received, vm.refreshing, vm::refresh, vm::saveReceived, vm::clearReceived, vm.partnerPeeked)

/** The inbox screen, independent of the ViewModel so it can be previewed and snapshot-tested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxContent(
    doodle: Doodle?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    partnerPeeked: Boolean = false,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (doodle == null || doodle.isEmpty) {
                Spacer(Modifier.height(32.dp))
                EmptyIllustration()
                Text("No doodle yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "A little space for something from them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                OutlinedButton(onClick = onRefresh, enabled = !refreshing) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Check now")
                }
            } else {
                Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("From your partner", style = MaterialTheme.typography.titleMedium)
                        Text(
                            DateUtils.getRelativeTimeSpanString(
                                doodle.createdAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                            ).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (partnerPeeked) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("🙈", fontSize = 14.sp)
                                Text(
                                    "Peeked!",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    ControlTooltip("Check for a new doodle") {
                        IconButton(onClick = onRefresh, enabled = !refreshing) {
                            Icon(Icons.Rounded.Refresh, "Check for a new doodle")
                        }
                    }
                }
                Box(
                    Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                ) { DoodleView(doodle.marks, Modifier.fillMaxWidth()) }

                var confirmClear by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                    if (Exporter.isSupported) {
                        ControlTooltip("Save doodle") {
                            IconButton(onClick = onSave) {
                                Icon(Icons.Rounded.SaveAlt, "Save doodle")
                            }
                        }
                    }
                    ControlTooltip("Clear doodle") {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Rounded.DeleteOutline, "Clear doodle")
                        }
                    }
                }
                if (confirmClear) {
                    AlertDialog(
                        onDismissRequest = { confirmClear = false },
                        title = { Text("Clear this doodle?") },
                        text = { Text("It's removed from this phone and your widget. Your partner isn't affected.") },
                        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear") } },
                        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
                    )
                }
            }
        }
    }
}
