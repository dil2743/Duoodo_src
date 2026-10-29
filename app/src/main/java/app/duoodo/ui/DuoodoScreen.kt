package app.duoodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun DuoodoScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.status) {
        vm.status?.let {
            snackbar.showSnackbar(it)
            vm.dismissStatus()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val state = vm.pairState) {
                MainViewModel.PairState.Unpaired -> return@Column UnpairedScreen(vm)
                is MainViewModel.PairState.Inviting -> return@Column InvitingScreen(vm, state.code)
                MainViewModel.PairState.Joining -> return@Column JoiningScreen(vm)
                MainViewModel.PairState.Paired -> Unit
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Draw") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Received") })
            }
            when (tab) {
                0 -> DrawTab(vm)
                else -> ReceivedTab(vm)
            }
        }
    }
}

@Composable
private fun DrawTab(vm: MainViewModel) {
    val d = vm.drawing
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DoodleCanvas(
            d,
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DrawingState.PALETTE.forEach { c ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .border(if (d.color == c) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { d.color = c }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DrawingState.WIDTHS.forEachIndexed { i, w ->
                val dot = (6 + i * 7).dp
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .border(if (d.width == w) 2.dp else 1.dp,
                            if (d.width == w) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape)
                        .clickable { d.width = w },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(dot).clip(CircleShape).background(Color(d.color))) }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = d::undo, enabled = d.canUndo) { Text("Undo") }
            OutlinedButton(onClick = d::clear, enabled = d.canUndo) { Text("Clear") }
            Spacer(Modifier.weight(1f))
            Button(onClick = vm::send, enabled = d.canUndo && !vm.busy) { Text(if (vm.busy) "Sending…" else "Send") }
        }
    }
}

@Composable
private fun ReceivedTab(vm: MainViewModel) {
    val doodle = vm.received
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (doodle == null || doodle.isEmpty) {
            Spacer(Modifier.height(48.dp))
            Text(
                "Nothing yet.\nYour partner's latest doodle will appear here and on your home-screen widget.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DoodleView(
                doodle.strokes,
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            )
            Text(
                "Sent " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(doodle.createdAtMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = vm::checkNow) { Text("Check now") }
        Spacer(Modifier.weight(1f))
        vm.safetyEmojis?.let {
            Text(
                "Safety code: ${it.joinToString(" ")}\nIf your partner sees the same emojis, your link is secure.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        var confirmUnpair by remember { mutableStateOf(false) }
        TextButton(onClick = { confirmUnpair = true }) { Text("Unpair") }
        if (confirmUnpair) {
            AlertDialog(
                onDismissRequest = { confirmUnpair = false },
                title = { Text("Unpair?") },
                text = { Text("You'll need a new invite to pair again. The received doodle is deleted.") },
                confirmButton = { TextButton(onClick = { confirmUnpair = false; vm.unpair() }) { Text("Unpair") } },
                dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
            )
        }
    }
}
