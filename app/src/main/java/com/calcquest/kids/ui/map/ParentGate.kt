package com.calcquest.kids.ui.map

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.calcquest.kids.R
import com.calcquest.kids.ui.common.AppIcon
import com.calcquest.kids.ui.theme.MapColors
import kotlin.random.Random
import kotlinx.coroutines.launch

private const val HOLD_MILLIS = 3_000L

/**
 * Accidental-entry safeguard (not authentication): press and hold for three seconds.
 * A short tap, or the accessibility click action, offers a typed-number alternative.
 */
@Composable
fun ParentGateButton(onOpen: () -> Unit) {
    val currentOnOpen by rememberUpdatedState(onOpen)
    var progress by remember { mutableFloatStateOf(0f) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showCheck by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.5.dp, MapColors.Path, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var opened = false
                        val job = scope.launch {
                            val start = withFrameMillis { it }
                            while (true) {
                                val now = withFrameMillis { it }
                                progress = ((now - start) / HOLD_MILLIS.toFloat()).coerceIn(0f, 1f)
                                if (now - start >= HOLD_MILLIS) {
                                    opened = true
                                    progress = 0f
                                    currentOnOpen()
                                    break
                                }
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        if (!opened) showHelp = true
                        progress = 0f
                    },
                )
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "Parents. Press and hold for 3 seconds, or double tap for another way in."
                onClick(label = "Open parent check") { showCheck = true; true }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(R.drawable.ic_parents, contentDescription = null)
                if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }
            Spacer(Modifier.size(6.dp))
            Text("Parents", style = MaterialTheme.typography.labelLarge)
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("For parents") },
            text = {
                Text(
                    "Press and hold the Parents button for 3 seconds to open settings. " +
                        "If holding is hard, you can type a number check instead.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false; showCheck = true }) { Text("Type a number check") }
            },
            dismissButton = { TextButton(onClick = { showHelp = false }) { Text("Close") } },
        )
    }

    if (showCheck) {
        ParentNumberCheck(
            onPassed = { showCheck = false; currentOnOpen() },
            onDismiss = { showCheck = false },
        )
    }
}

private val numberWords = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

@Composable
private fun ParentNumberCheck(onPassed: () -> Unit, onDismiss: () -> Unit) {
    val digits = rememberSaveable { List(4) { Random.nextInt(0, 10) }.joinToString("") }
    var typed by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    val words = digits.map { numberWords[it - '0'] }.joinToString(", ")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parent check") },
        text = {
            Column {
                Text("Type these numbers as digits: $words", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { value -> typed = value.filter { it.isDigit() }.take(4); wrong = false },
                    label = { Text("Digits") },
                    singleLine = true,
                    isError = wrong,
                    supportingText = { if (wrong) Text("That doesn't match. Please try again.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (typed == digits) onPassed() else wrong = true }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
