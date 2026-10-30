package app.duoodo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DrawTab(vm: MainViewModel) = DrawContent(vm.drawing, vm.busy, vm::send, vm.status, vm.partnerPeeked)

/** The draw screen, independent of the ViewModel so it can be previewed and snapshot-tested. */
@Composable
fun DrawContent(
    d: DrawingState,
    busy: Boolean,
    onSend: () -> Unit,
    status: String? = null,
    partnerPeeked: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val budget = d.imageBudget
                if (budget < 1024) {
                    android.widget.Toast.makeText(context, "Doodle full: no space for more images", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bytes = withContext(Dispatchers.IO) {
                    compressImage(context, uri, budget)
                }
                if (bytes != null) {
                    d.imageBytes = bytes
                    d.tool = DrawingState.Tool.IMAGE
                } else {
                    android.widget.Toast.makeText(context, "Could not compress image to fit budget", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var textAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    textAt?.let { (x, y) ->
        TextEntryDialog(
            maxBytes = d.textBudget,
            onDone = { text -> d.addText(text, x, y); textAt = null },
            onDismiss = { textAt = null },
        )
    }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Start over?") },
            text = { Text("This clears your drawing. It hasn't been sent.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; d.clear() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep drawing") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Scrolls only on short screens; on most phones everything fits.
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, MaterialTheme.shapes.large, clip = false)
                    .clip(MaterialTheme.shapes.large)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
            ) {
                DoodleCanvas(
                    d,
                    onTap = { x, y ->
                        when (d.tool) {
                            DrawingState.Tool.TEXT -> if (d.textBudget > 0) textAt = x to y
                            else -> Unit
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (d.isEmpty && d.preview.isEmpty()) {
                    Text(
                        hintFor(d.tool),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9E9EAE),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            ToolStrip(d, onPickImage = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            ToolOptions(d, onPickImage = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        }
        ActionBar(d, busy, onSend, onClear = { confirmClear = true }, status = status, partnerPeeked = partnerPeeked)
    }
}

private fun hintFor(tool: DrawingState.Tool) = when (tool) {
    DrawingState.Tool.TEXT -> "Tap anywhere to write"
    DrawingState.Tool.EMOJI -> "Touch & drag to place. Pinch to scale & rotate"
    DrawingState.Tool.IMAGE -> "Touch & drag to place. Pinch to scale & rotate"
    DrawingState.Tool.LINE, DrawingState.Tool.ARROW, DrawingState.Tool.BOX, DrawingState.Tool.CIRCLE -> "Drag to draw a shape"
    else -> "Draw something for your partner"
}

private fun iconFor(tool: DrawingState.Tool): ImageVector? = when (tool) {
    DrawingState.Tool.PEN -> Icons.Rounded.Edit
    DrawingState.Tool.MARKER -> Icons.Rounded.Highlight
    DrawingState.Tool.RAINBOW -> Icons.Rounded.AutoAwesome
    DrawingState.Tool.ERASER -> null // drawn by hand below
    DrawingState.Tool.LINE -> Icons.Rounded.HorizontalRule
    DrawingState.Tool.ARROW -> Icons.Rounded.ArrowOutward
    DrawingState.Tool.BOX -> Icons.Rounded.CropSquare
    DrawingState.Tool.CIRCLE -> Icons.Rounded.RadioButtonUnchecked
    DrawingState.Tool.TEXT -> Icons.Rounded.TextFields
    DrawingState.Tool.EMOJI -> Icons.Rounded.EmojiEmotions
    DrawingState.Tool.IMAGE -> Icons.Rounded.Image
}

@Composable
private fun ToolStrip(d: DrawingState, onPickImage: () -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(DrawingState.Tool.entries) { tool ->
            val selected = d.tool == tool
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "toolBg",
            )
            val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .clickable {
                        if (tool == DrawingState.Tool.IMAGE) {
                            onPickImage()
                        } else {
                            d.tool = tool
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    tool == DrawingState.Tool.RAINBOW -> RainbowGlyph()
                    tool == DrawingState.Tool.ERASER -> EraserGlyph(fg)
                    else -> Icon(iconFor(tool)!!, tool.label, tint = fg, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun RainbowGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.1f, w * 0.7f)
            cubicTo(w * 0.3f, w * 0.1f, w * 0.6f, w * 0.9f, w * 0.9f, w * 0.3f)
        }
        drawPath(
            path,
            GradientBrush.linearGradient(listOf(Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF007AFF), Color(0xFFAF52DE))),
            style = Stroke(width = w * 0.16f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun EraserGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val body = Path().apply {
            moveTo(w * 0.55f, w * 0.12f); lineTo(w * 0.9f, w * 0.47f); lineTo(w * 0.5f, w * 0.87f)
            lineTo(w * 0.15f, w * 0.52f); close()
        }
        drawPath(body, color, style = Stroke(width = w * 0.09f))
        drawLine(color, Offset(w * 0.33f, w * 0.34f), Offset(w * 0.68f, w * 0.69f), strokeWidth = w * 0.09f)
        drawLine(color, Offset(w * 0.45f, w * 0.92f), Offset(w * 0.92f, w * 0.92f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
    }
}

@Composable
private fun ToolOptions(d: DrawingState, onPickImage: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(targetState = d.tool, label = "toolOptions") { tool ->
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    tool == DrawingState.Tool.IMAGE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Touch & drag to place. Pinch to scale & rotate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onPickImage) {
                                Text("Pick new")
                            }
                        }
                    }
                    tool == DrawingState.Tool.EMOJI -> {
                        EmojiGrid(d)
                        Text(
                            "Touch & drag to place. Pinch to scale & rotate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    tool == DrawingState.Tool.TEXT -> {
                        ColorRow(d)
                        SizeRow(DrawingState.TEXT_SIZES, d.textSize, { d.textSize = it }) { i, _ ->
                            Text("Aa", color = MaterialTheme.colorScheme.onSurface, fontSize = (12 + i * 5).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    tool == DrawingState.Tool.ERASER || tool == DrawingState.Tool.RAINBOW -> {
                        Text(
                            if (tool == DrawingState.Tool.ERASER) "Rub out parts of your drawing" else "Every stroke paints a rainbow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        WidthRow(d, Color(0xFF9E9EAE))
                    }
                    else -> {
                        ColorRow(d)
                        WidthRow(d, Color(d.color).takeIf { it.luminance() < 0.95f } ?: Color(0xFFBDBDBD))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorRow(d: DrawingState) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(DrawingState.PALETTE) { c ->
            val selected = d.color == c
            Box(
                Modifier
                    .size(34.dp)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .padding(if (selected) 5.dp else 2.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .clickable { d.color = c },
            )
        }
    }
}

@Composable
private fun WidthRow(d: DrawingState, dot: Color) {
    SizeRow(DrawingState.WIDTHS, d.width, { d.width = it }) { i, _ ->
        Box(Modifier.size((6 + i * 7).dp).clip(CircleShape).background(dot))
    }
}

@Composable
private fun <T> SizeRow(options: List<T>, selected: T, onSelect: (T) -> Unit, glyph: @Composable (Int, T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) { glyph(i, option) }
        }
    }
}

@Composable
private fun EmojiGrid(d: DrawingState) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(44.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 152.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(DrawingState.EMOJIS) { e ->
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (d.emoji == e) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { d.emoji = e },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 24.sp) }
        }
    }
}

@Composable
private fun ActionBar(
    d: DrawingState,
    busy: Boolean,
    onSend: () -> Unit,
    onClear: () -> Unit,
    status: String? = null,
    partnerPeeked: Boolean = false,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            FilledTonalIconButton(onClick = d::undo, enabled = d.canUndo, colors = colors, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", Modifier.size(20.dp))
            }
            FilledTonalIconButton(onClick = d::redo, enabled = d.canRedo, colors = colors, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Redo, "Redo", Modifier.size(20.dp))
            }
            FilledTonalIconButton(onClick = onClear, enabled = d.canUndo, colors = colors, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Rounded.DeleteOutline, "Clear", Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (partnerPeeked) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("🙈", fontSize = 14.sp)
                        Text(
                            "Peeked!",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            } else if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            FilledIconButton(
                onClick = onSend,
                enabled = d.canUndo && !busy,
                modifier = Modifier.size(46.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                ),
                shape = CircleShape,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send", Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun TextEntryDialog(maxBytes: Int, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add text") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.toByteArray().size <= maxBytes) text = it },
                placeholder = { Text("Say something…") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { Button(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun compressImage(context: Context, uri: Uri, maxBytes: Int): ByteArray? = runCatching {
    if (maxBytes <= 512) return null
    val input = context.contentResolver.openInputStream(uri) ?: return null
    val original = BitmapFactory.decodeStream(input) ?: return null
    input.close()

    for (maxDim in intArrayOf(512, 384, 256, 192, 128)) {
        val scale = if (original.width > maxDim || original.height > maxDim) {
            maxDim.toFloat() / maxOf(original.width, original.height)
        } else 1f
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
        } else original

        for (quality in intArrayOf(75, 55, 35, 20)) {
            val stream = ByteArrayOutputStream()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, stream)
            } else {
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            if (stream.size() <= maxBytes) {
                return stream.toByteArray()
            }
        }
    }
    null
}.getOrNull()
