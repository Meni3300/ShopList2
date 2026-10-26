package com.shoplist.app.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight

/** "−" / "+" style buttons made from text so no extra icon library is needed. */
@Composable
fun TextIconButton(label: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
    ) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AddProductDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    TextInputDialog(
        title = "מה חסר בבית?",
        placeholder = "כתוב מוצר שחסר...",
        confirmLabel = "הוסף",
        onConfirm = { onSubmit(it) },
        onDismiss = onDismiss
    )
}

@Composable
fun TextInputDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    initial: String = "",
    errorText: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // focus can fail if the dialog window is not attached yet; harmless
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text) })
                )
                if (errorText != null) {
                    Text(
                        errorText,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

@Composable
fun EmptyState(message: String, hint: String? = null, buttonLabel: String? = null, onButton: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("🛒", fontSize = 40.sp)
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (buttonLabel != null) Button(onClick = onButton) { Text(buttonLabel) }
    }
}

private fun <T> swapped(list: List<T>, a: Int, b: Int): List<T> =
    list.toMutableList().also {
        val t = it[a]
        it[a] = it[b]
        it[b] = t
    }

/**
 * A column whose rows can be re-ordered by dragging the row's handle. The new order is reported
 * through [onCommit] once the finger is released (and only if it actually changed).
 * Each row gets a [handle] modifier that must be put on the drag handle icon.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> Long,
    onCommit: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, index: Int, handle: Modifier, dragging: Boolean) -> Unit
) {
    val working = remember(items) { mutableStateOf(items) }
    var dragKey by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Long, Int>() }
    val latestCommit by rememberUpdatedState(onCommit)

    fun finish() {
        dragKey = null
        dragOffset = 0f
        val now = working.value
        if (now.map(keyOf) != items.map(keyOf)) latestCommit(now)
    }

    Column(modifier) {
        working.value.forEachIndexed { index, item ->
            val k = keyOf(item)
            key(k) {
                val dragging = dragKey == k
                val handle = Modifier.pointerInput(k, working) {
                    detectDragGestures(
                        onDragStart = {
                            dragKey = k
                            dragOffset = 0f
                        },
                        onDragEnd = { finish() },
                        onDragCancel = { finish() },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val list = working.value
                            val i = list.indexOfFirst { keyOf(it) == k }
                            if (i >= 0) {
                                if (dragOffset > 0 && i < list.lastIndex) {
                                    val h = heights[keyOf(list[i + 1])] ?: 0
                                    if (h > 0 && dragOffset > h / 2f) {
                                        working.value = swapped(list, i, i + 1)
                                        dragOffset -= h
                                    }
                                } else if (dragOffset < 0 && i > 0) {
                                    val h = heights[keyOf(list[i - 1])] ?: 0
                                    if (h > 0 && -dragOffset > h / 2f) {
                                        working.value = swapped(list, i, i - 1)
                                        dragOffset += h
                                    }
                                }
                            }
                        }
                    )
                }
                Box(
                    Modifier
                        .onGloballyPositioned { heights[k] = it.size.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .alpha(if (dragging) 0.92f else 1f)
                ) {
                    itemContent(item, index, handle, dragging)
                }
            }
        }
    }
}
