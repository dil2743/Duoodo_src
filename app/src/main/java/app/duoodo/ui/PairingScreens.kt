package app.duoodo.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duoodo.ui.components.BirdLogo
import app.duoodo.ui.components.Footer
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
private fun PairingColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        Footer()
    }
}

@Composable
fun UnpairedScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var pasted by rememberSaveable { mutableStateOf("") }
    PairingColumn {
        Spacer(Modifier.height(32.dp))
        BirdLogo(size = 88.dp)
        Text("Duoodo", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Little doodles for your favourite person. Private, end-to-end encrypted, no accounts.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Button(onClick = vm::createInvite, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Invite my partner")
        }
        FilledTonalButton(
            onClick = {
                // Google's scanner runs inside Play services, so Duoodo needs no camera permission.
                val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                GmsBarcodeScanning.getClient(context, options).startScan()
                    .addOnSuccessListener { code -> code.rawValue?.let(vm::acceptInvite) }
            },
            enabled = !vm.busy,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Scan partner's QR code")
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                "  or paste their code  ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f))
        }
        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it },
            placeholder = { Text("duoodo-…") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            trailingIcon = {
                IconButton(onClick = { pasteFromClipboard(context)?.let { pasted = it } }) {
                    Icon(Icons.Rounded.ContentPaste, "Paste")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.acceptInvite(pasted) },
            enabled = pasted.isNotBlank() && !vm.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Join")
        }
        PrivacyNote()
    }
}

@Composable
private fun PrivacyNote() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            "Only your partner can see what you draw",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun InvitingScreen(vm: MainViewModel, code: String) {
    val context = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }
    PairingColumn {
        Text("Invite your partner", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Let them scan this, or copy the code and send it on WhatsApp or any messenger.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The QR stays black on white in both themes so every camera reads it.
        Surface(
            color = Color.White,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 4.dp,
            modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(),
        ) { QrCode(code, Modifier.padding(12.dp)) }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    code,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                IconButton(onClick = { copyToClipboard(context, code); copied = true }) {
                    Icon(Icons.Rounded.ContentCopy, "Copy")
                }
            }
        }
        Button(onClick = { copyToClipboard(context, code); copied = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (copied) "Copied ✓" else "Copy code")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Waiting for your partner…", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Valid for 24 hours, for one person only. You'll stay paired until one of you unpairs.",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = vm::unpair) { Text("Cancel") }
    }
}

@Composable
fun JoiningScreen(vm: MainViewModel) {
    PairingColumn {
        Spacer(Modifier.height(72.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(104.dp), strokeWidth = 3.dp)
            BirdLogo(size = 72.dp)
        }
        Text("Joining…", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Waiting for your partner's phone to confirm. It's quickest if their app is open.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = vm::unpair) { Text("Cancel") }
    }
}

@Composable
fun VerifySafetyScreen(vm: MainViewModel, emojis: List<String>) =
    VerifySafetyContent(emojis, vm::confirmSafetyCode, vm::unpair)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VerifySafetyContent(emojis: List<String>, onConfirm: () -> Unit, onUnpair: () -> Unit) {
    PairingColumn {
        Spacer(Modifier.height(16.dp))
        BirdLogo(size = 72.dp)
        Text("Verify safety code", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Confirm that both your and your partner's screens display these exact 6 emojis. If an invite was intercepted, they will not match.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 6,
            ) {
                emojis.forEach { emoji ->
                    Text(emoji, fontSize = 32.sp)
                }
            }
        }

        Text(
            "Do these emojis match your partner's screen?",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )

        Button(onClick = onConfirm, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("They match")
        }

        FilledTonalButton(onClick = onUnpair, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("They don't match (unpair)")
        }
    }
}

@Composable
fun PartnerLeftScreen(vm: MainViewModel) {
    PairingColumn {
        Spacer(Modifier.height(72.dp))
        Box(
            Modifier.size(88.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.LinkOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(40.dp))
        }
        Text("Your partner unpaired", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Nothing more can be sent or received. Their last doodle was removed from this phone.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = vm::unpair, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start over") }
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

private fun pasteFromClipboard(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java).primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
