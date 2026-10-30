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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.duoodo.ui.components.BirdLogo

private enum class Tab(val label: String) { DRAW("Draw"), INBOX("Inbox"), SETTINGS("Settings") }

@Composable
fun DuoodoScreen(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.status) {
        vm.status?.let {
            snackbar.showSnackbar(it)
            vm.dismissStatus()
        }
    }
    // Pairing flow screens are full-screen; the tabbed app appears once paired.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairedApp(vm: MainViewModel, snackbar: SnackbarHostState) {
    var tab by rememberSaveable { mutableIntStateOf(Tab.DRAW.ordinal) }
    LaunchedEffect(tab) { if (tab == Tab.INBOX.ordinal) vm.markSeen() }
    // Jump to the inbox the first time the screen sees a brand-new doodle? No: never yank the user
    // away from a half-finished drawing. The badge tells them instead.
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BirdLogo(size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Duoodo", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = { ConnectionDot(vm.instantDelivery) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.height(58.dp),
                tonalElevation = 0.dp,
            ) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = {
                            val icon = when (t) {
                                Tab.DRAW -> Icons.Rounded.Draw
                                Tab.INBOX -> Icons.Rounded.Inbox
                                Tab.SETTINGS -> Icons.Rounded.Settings
                            }
                            if (t == Tab.INBOX && vm.unseen) {
                                BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.error) }) {
                                    Icon(icon, t.label, modifier = Modifier.size(24.dp))
                                }
                            } else {
                                Icon(icon, t.label, modifier = Modifier.size(24.dp))
                            }
                        },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (Tab.entries[tab]) {
                Tab.DRAW -> DrawTab(vm)
                Tab.INBOX -> InboxTab(vm)
                Tab.SETTINGS -> SettingsTab(vm)
            }
        }
    }
}

/** Small status pill: green when instant delivery keeps us connected. */
@Composable
private fun ConnectionDot(live: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = Modifier.padding(end = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = if (live) Color(0xFF34C759) else MaterialTheme.colorScheme.outline, shape = CircleShape) {
                Spacer(Modifier.size(8.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (live) "Live" else "Paired",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
