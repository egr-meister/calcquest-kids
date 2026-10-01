package com.calcquest.kids.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcquest.kids.data.local.CalculationEntity
import com.calcquest.kids.ui.common.ConfirmDialog
import com.calcquest.kids.ui.common.SimpleTopBar
import com.calcquest.kids.ui.theme.MapColors
import java.text.DateFormat
import java.util.Date

private fun CalculationEntity.equation(): String =
    "$firstOperand $operator $secondOperand ${if (rounded) "≈" else "="} $result"

@Composable
fun HistoryScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val selected = history.firstOrNull { it.id == selectedId }

    Scaffold(
        containerColor = MapColors.Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SimpleTopBar(
                title = "History",
                onBack = onBack,
                backLabel = "Back to calculator",
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Clear")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No calculations yet.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(history, key = { it.id }) { entry ->
                    val spoken = spokenExpression(entry.equation())
                    Surface(
                        onClick = { selectedId = entry.id },
                        color = MapColors.Paper,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clearAndSetSemantics {
                                contentDescription = spoken
                                role = Role.Button
                                onClick(label = "Choose this result") { selectedId = entry.id; true }
                            },
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(entry.equation(), style = MaterialTheme.typography.titleMedium)
                            Text(
                                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.createdAt)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MapColors.SlateMuted,
                            )
                        }
                    }
                }
            }
        }
    }

    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text(selected.equation()) },
            text = { Text("Put ${selected.result} into the calculator?", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.useResult(selected)
                    selectedId = null
                    onBack()
                }) { Text("Use result") }
            },
            dismissButton = { TextButton(onClick = { selectedId = null }) { Text("Cancel") } },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear history?",
            message = "All saved calculations will be removed from this device.",
            confirmLabel = "Clear history",
            onConfirm = { viewModel.clearHistory(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}
