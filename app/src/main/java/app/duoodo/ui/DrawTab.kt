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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.duoodo.ui.components.ControlTooltip

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
                    d.chooseImage(bytes)
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

    val pickImage = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val paper: @Composable () -> Unit = {
            BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                val paperSize = minOf(maxWidth, maxHeight, 560.dp)
                Box(
                    Modifier
                        .size(paperSize)
                        .shadow(6.dp, RoundedCornerShape(3.dp), ambientColor = Color(0xFF8A7DA8), spotColor = Color(0xFF8A7DA8))
                        .clip(RoundedCornerShape(3.dp)),
                ) {
                    DoodleCanvas(
                        d,
                        onTap = { x, y ->
                            if (d.tool == DrawingState.Tool.TEXT && d.textBudget > 0) textAt = x to y
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (d.isEmpty && d.preview.isEmpty()) {
                        Column(
                            Modifier.align(Alignment.Center).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Rounded.FavoriteBorder, null, tint = Color(0xFFC0A4B8), modifier = Modifier.size(28.dp))
                            Text(
                                "Just for you",
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontSize = 22.sp,
                                color = Color(0xFF918398),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        val controls: @Composable () -> Unit = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        ToolStrip(d, onPickImage = pickImage)
                        Spacer(Modifier.height(8.dp))
                        ToolOptions(d, onPickImage = pickImage)
                    }
                    ActionBar(d, busy, onSend, onClear = { confirmClear = true }, status = status, partnerPeeked = partnerPeeked)
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { paper() }
                Column(
                    Modifier.width(320.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) { controls() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { paper() }
                controls()
            }
        }
    }
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
    DrawingState.Tool.SELECT -> Icons.Rounded.OpenWith
}

@Composable
private fun ToolStrip(d: DrawingState, onPickImage: () -> Unit) {
    val groups = listOf(
        listOf(DrawingState.Tool.PEN, DrawingState.Tool.MARKER, DrawingState.Tool.RAINBOW, DrawingState.Tool.ERASER),
        listOf(DrawingState.Tool.SELECT),
        listOf(DrawingState.Tool.ARROW, DrawingState.Tool.LINE, DrawingState.Tool.BOX, DrawingState.Tool.CIRCLE),
        listOf(DrawingState.Tool.TEXT),
        listOf(DrawingState.Tool.EMOJI),
        listOf(DrawingState.Tool.IMAGE),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        groups.forEach { group ->
            val selected = d.tool in group
            val tool = if (selected) d.tool else group.first()
            var expanded by remember { mutableStateOf(false) }
            val label = when {
                group.size == 1 -> tool.label
                tool.isShape -> "Shapes"
                else -> "Brushes"
            }
            Box {
                ControlTooltip(label) {
                    val background by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                        label = "toolSelection",
                    )
                    val foreground = if (selected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(background)
                            .semantics { contentDescription = if (selected) "$label, ${tool.label}" else label }
                            .selectable(selected, role = Role.RadioButton, onClick = {
                                when {
                                    group.size > 1 -> expanded = true
                                    tool == DrawingState.Tool.IMAGE -> onPickImage()
                                    else -> d.chooseTool(tool)
                                }
                            }),
                        contentAlignment = Alignment.Center,
                    ) {
                        ToolGlyph(tool, foreground)
                        if (group.size > 1) {
                            Icon(Icons.Rounded.ExpandMore, null, Modifier.align(Alignment.BottomCenter).size(12.dp), tint = foreground)
                        }
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    group.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = { ToolGlyph(option, MaterialTheme.colorScheme.onSurface) },
                            trailingIcon = { if (d.tool == option) Icon(Icons.Rounded.Check, "Selected") },
                            onClick = { d.chooseTool(option); expanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolGlyph(tool: DrawingState.Tool, color: Color) {
    when (tool) {
        DrawingState.Tool.RAINBOW -> RainbowGlyph()
        DrawingState.Tool.ERASER -> EraserGlyph(color)
        else -> Icon(iconFor(tool)!!, null, tint = color, modifier = Modifier.size(22.dp))
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
    Column(Modifier.fillMaxWidth().height(104.dp)) {
        AnimatedContent(targetState = d.tool, label = "toolOptions") { tool ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    tool == DrawingState.Tool.SELECT -> {
                        val selected = d.selectedMark
                        val selectionSize = when (selected) {
                            is app.duoodo.model.TextMark -> selected.size
                            is app.duoodo.model.ImageMark -> selected.scale
                            else -> null
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (selected) {
                                    is app.duoodo.model.ImageMark -> "Image"
                                    is app.duoodo.model.TextMark -> if (selected.text in DrawingState.EMOJIS) "Emoji" else "Text"
                                    else -> "Nothing selected"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (selectionSize != null) Text("${(selectionSize * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                        }
                        if (selectionSize != null) {
                            Slider(
                                value = selectionSize.coerceIn(0.02f, 1f),
                                onValueChange = d::resizeSelection,
                                onValueChangeFinished = d::endTransform,
                                valueRange = (if (selected is app.duoodo.model.ImageMark) 0.04f else 0.02f)..1f,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Selected item size" },
                            )
                        }
                    }
                    tool == DrawingState.Tool.IMAGE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onPickImage) {
                                Icon(Icons.Rounded.Image, null, Modifier.size(20.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Choose photo")
                            }
                        }
                    }
                    tool == DrawingState.Tool.EMOJI -> {
                        EmojiGrid(d)
                    }
                    tool == DrawingState.Tool.TEXT -> {
                        ColorRow(d)
                        SizeRow(DrawingState.TEXT_SIZES, d.textSize, { d.textSize = it }) { i, _ ->
                            Text("Aa", color = MaterialTheme.colorScheme.onSurface, fontSize = (12 + i * 5).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    tool == DrawingState.Tool.ERASER || tool == DrawingState.Tool.RAINBOW -> {
                        WidthRow(d, MaterialTheme.colorScheme.onSurface)
                    }
                    else -> {
                        ColorRow(d)
                        WidthRow(d, MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorRow(d: DrawingState) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items(DrawingState.PALETTE) { c ->
            val selected = d.color == c
            Box(
                Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Color #${Integer.toHexString(c).takeLast(6)}" }
                    .selectable(selected, role = Role.RadioButton, onClick = { d.color = c })
                    .padding(8.dp)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .padding(if (selected) 4.dp else 1.dp)
                    .clip(CircleShape)
                    .background(Color(c)),
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .semantics { contentDescription = "Size ${i + 1}" }
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) { glyph(i, option) }
        }
    }
}

@Composable
private fun EmojiGrid(d: DrawingState) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(48.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(DrawingState.EMOJIS) { e ->
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (d.emoji == e) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .selectable(d.emoji == e, role = Role.RadioButton, onClick = { d.chooseEmoji(e) }),
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
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column {
        if (status != null) {
            Text(
            status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ControlTooltip("Undo") {
            IconButton(onClick = d::undo, enabled = d.canUndo, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", Modifier.size(20.dp))
            }
            }
            ControlTooltip("Redo") {
            IconButton(onClick = d::redo, enabled = d.canRedo, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Redo, "Redo", Modifier.size(20.dp))
            }
            }
            ControlTooltip("Clear drawing") {
            IconButton(onClick = onClear, enabled = d.canUndo, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.DeleteOutline, "Clear", Modifier.size(20.dp))
            }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (partnerPeeked) {
                    ControlTooltip("Your partner took a peek") {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "🙈",
                                fontSize = with(LocalDensity.current) { 28.dp.toSp() },
                                modifier = Modifier.clearAndSetSemantics { contentDescription = "Your partner took a peek" },
                            )
                        }
                    }
                }
            }
            ControlTooltip("Send doodle") {
            FilledIconButton(
                onClick = onSend,
                enabled = d.canUndo && !busy,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                ),
                shape = CircleShape,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp).semantics { contentDescription = "Sending doodle" },
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send", Modifier.size(22.dp))
                }
            }
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
