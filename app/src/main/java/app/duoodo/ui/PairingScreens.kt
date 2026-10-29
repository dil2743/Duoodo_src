package app.duoodo.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
private fun PairingColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
fun UnpairedScreen(vm: MainViewModel) {
    var pasted by rememberSaveable { mutableStateOf("") }
    PairingColumn {
        Spacer(Modifier.height(24.dp))
        Text("Duoodo", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Doodle notes for exactly two people. End-to-end encrypted, no accounts, no servers of ours.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::createInvite, modifier = Modifier.fillMaxWidth()) { Text("Create invite code") }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Got a code from your partner? Paste it here.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it },
            placeholder = { Text("duoodo-…") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.acceptInvite(pasted) }, enabled = pasted.isNotBlank() && !vm.busy) { Text("Join") }
        if (vm.busy) CircularProgressIndicator()
    }
}

@Composable
fun InvitingScreen(vm: MainViewModel, code: String) {
    val context = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }
    PairingColumn {
        Text("Your invite code", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Copy it and send it to your partner over WhatsApp, Signal, or any messenger. " +
                "It works once. Anyone who gets it first could pair with you, so only send it to them.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(code, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp))
        }
        Button(onClick = { copyToClipboard(context, code); copied = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (copied) "Copied ✓" else "Copy code")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
            Text("Waiting for your partner to join…")
        }
        TextButton(onClick = vm::unpair) { Text("Cancel") }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clip = ClipData.newPlainText("Duoodo invite", text)
    // Hide the code from the clipboard preview and keyboard suggestions.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
