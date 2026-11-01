package app.duoodo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duoodo.ui.components.BirdLogo

enum class HomePanel(val label: String) { INBOX("Inbox"), SETTINGS("Settings") }

@Composable
fun DuoodoScreen(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.status) {
        vm.status?.let {
            snackbar.showSnackbar(it)
            vm.dismissStatus()
        }
    }
    AnimatedContent(
        targetState = vm.pairState,
        contentKey = { it::class },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "pairState",
    ) { state ->
        when (state) {
            MainViewModel.PairState.Unpaired -> PairingScaffold(snackbar) { UnpairedScreen(vm) }
            is MainViewModel.PairState.Inviting -> PairingScaffold(snackbar) { InvitingScreen(vm, state.code) }
            MainViewModel.PairState.Joining -> PairingScaffold(snackbar) { JoiningScreen(vm) }
            is MainViewModel.PairState.VerifySafety -> PairingScaffold(snackbar) { VerifySafetyScreen(vm, state.emojis) }
            MainViewModel.PairState.PartnerLeft -> PairingScaffold(snackbar) { PartnerLeftScreen(vm) }
            MainViewModel.PairState.Paired -> PairedApp(vm, snackbar)
        }
    }
}

@Composable
private fun PairingScaffold(snackbar: SnackbarHostState, content: @Composable () -> Unit) {
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

@Composable
private fun PairedApp(vm: MainViewModel, snackbar: SnackbarHostState) {
    var panel by rememberSaveable { mutableStateOf<HomePanel?>(null) }
    LaunchedEffect(panel) { if (panel == HomePanel.INBOX) vm.markSeen() }
    HomeContent(
        panel = panel,
        onPanelChange = { panel = it },
        unseen = vm.unseen,
        live = vm.instantDelivery,
        snackbar = snackbar,
        draw = { DrawTab(vm) },
        inbox = { InboxTab(vm) },
        settings = { SettingsTab(vm) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    panel: HomePanel?,
    onPanelChange: (HomePanel?) -> Unit,
    unseen: Boolean,
    live: Boolean,
    snackbar: SnackbarHostState,
    draw: @Composable () -> Unit,
    inbox: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BirdLogo(size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Duoodo", style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (live) "Together, live" else "Just the two of you",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onPanelChange(HomePanel.INBOX) }) {
                        BadgedBox(badge = {
                            if (unseen) Badge(containerColor = MaterialTheme.colorScheme.tertiary)
                        }) {
                            Icon(Icons.Rounded.Inbox, if (unseen) "Inbox, new doodle" else "Inbox")
                        }
                    }
                    IconButton(onClick = { onPanelChange(HomePanel.SETTINGS) }) {
                        Icon(Icons.Rounded.Settings, "Settings")
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { if (panel == null) SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { draw() }
    }
    if (panel != null) {
        ModalBottomSheet(
            onDismissRequest = { onPanelChange(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = null,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
                HomePanelContent(panel, onClose = { onPanelChange(null) }) {
                    when (panel) {
                        HomePanel.INBOX -> inbox()
                        HomePanel.SETTINGS -> settings()
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
internal fun HomePanelContent(panel: HomePanel, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(panel.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, "Close ${panel.label.lowercase()}")
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
