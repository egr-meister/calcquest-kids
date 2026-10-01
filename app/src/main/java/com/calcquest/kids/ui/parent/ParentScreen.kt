package com.calcquest.kids.ui.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.Topic
import com.calcquest.kids.domain.timer.TimerDurations
import com.calcquest.kids.ui.common.ConfirmDialog
import com.calcquest.kids.ui.common.SimpleTopBar
import com.calcquest.kids.ui.theme.MapColors

@Composable
fun ParentScreen(
    viewModel: ParentViewModel,
    onClose: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val requestClose: () -> Unit = {
        if (state.hasUnsavedChanges) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    // Predictive-Back compatible: only intercepts when there is something to lose.
    BackHandler(enabled = state.hasUnsavedChanges) { confirmDiscard = true }

    Scaffold(
        containerColor = MapColors.Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SimpleTopBar(
                title = "Parent settings",
                onBack = requestClose,
                backLabel = "Close parent settings",
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.hasUnsavedChanges,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .heightIn(min = 48.dp),
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Settings stay on this device. The Parents button is an accidental-tap guard, not a password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MapColors.SlateMuted,
                )
                state.message?.let {
                    Surface(color = MapColors.SuccessSoft, shape = MaterialTheme.shapes.small) {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }

                Section("Topics") {
                    Text("At least one topic stays on. Turned-off topics never block the route.", style = MaterialTheme.typography.bodyMedium)
                    Topic.routeOrder.forEach { topic ->
                        val checked = topic in state.draft.enabledTopics
                        val isLast = checked && state.draft.enabledTopics.size == 1
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .toggleable(
                                    value = checked,
                                    enabled = !isLast,
                                    role = Role.Checkbox,
                                    onValueChange = { viewModel.toggleTopic(topic) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = !isLast)
                            Spacer(Modifier.size(12.dp))
                            Text("Level ${topic.level}: ${topic.title}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                Section("Difficulty") {
                    Column(Modifier.selectableGroup()) {
                        Difficulty.entries.forEach { d ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .selectable(
                                        selected = state.draft.difficulty == d,
                                        role = Role.RadioButton,
                                        onClick = { viewModel.setDifficulty(d) },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = state.draft.difficulty == d, onClick = null)
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text(d.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(difficultyDescription(d), style = MaterialTheme.typography.bodyMedium, color = MapColors.SlateMuted)
                                }
                            }
                        }
                    }
                    Text(
                        "New quests use the new difficulty. Quests already started keep their own questions. Each difficulty has its own route progress.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Section("Challenge Timer") {
                    SwitchRow("Challenge Timer", "A soft per-question timer. Running out never blocks progress.", state.draft.timerEnabled, viewModel::setTimerEnabled)
                    if (state.draft.timerEnabled) {
                        Column(Modifier.selectableGroup()) {
                            TimerDurations.CHOICES.forEach { seconds ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .selectable(
                                            selected = state.draft.timerSeconds == seconds,
                                            role = Role.RadioButton,
                                            onClick = { viewModel.setTimerSeconds(seconds) },
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = state.draft.timerSeconds == seconds, onClick = null)
                                    Spacer(Modifier.size(12.dp))
                                    Text("$seconds seconds", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    Text("Timer changes apply from the next question.", style = MaterialTheme.typography.bodyMedium)
                }

                Section("Sound and motion") {
                    SwitchRow("Sound", "Short feedback sounds while the app is open.", state.draft.soundEnabled, viewModel::setSound)
                    SwitchRow("Reduce decorative animation", "Turns off the completion animation.", state.draft.reduceAnimation, viewModel::setReduceAnimation)
                }

                Section("Reset and data") {
                    ResetButton("Reset ${state.saved.difficulty.label} quest progress") { viewModel.requestReset(ResetAction.RESET_DIFFICULTY) }
                    ResetButton("Reset all quest progress") { viewModel.requestReset(ResetAction.RESET_ALL_QUESTS) }
                    ResetButton("Clear calculator history") { viewModel.requestReset(ResetAction.CLEAR_HISTORY) }
                    ResetButton("Clear all local data") { viewModel.requestReset(ResetAction.CLEAR_ALL_DATA) }
                }

                Section("Privacy") {
                    OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Privacy information")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    state.pendingWarning?.let { topic ->
        AlertDialog(
            onDismissRequest = viewModel::dismissWarning,
            title = { Text("${topic.title} will be next") },
            text = {
                Text(
                    "${topic.title} is not finished yet at this difficulty. After saving, it becomes the next level to complete " +
                        "before later levels unlock. Levels already completed stay completed and replayable.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmWarning) { Text("Save anyway") } },
            dismissButton = { TextButton(onClick = viewModel::dismissWarning) { Text("Go back") } },
        )
    }

    state.pendingReset?.let { action ->
        ConfirmDialog(
            title = action.title,
            message = action.message,
            confirmLabel = action.confirm,
            onConfirm = viewModel::confirmReset,
            onDismiss = viewModel::dismissReset,
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your unsaved setting changes will be lost.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onConfirm = {
                confirmDiscard = false
                viewModel.discardChanges()
                onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

private fun difficultyDescription(d: Difficulty): String =
    "Add and subtract ${d.addSubRange.first}–${d.addSubRange.last}, " +
        "multiply ${d.factorRange.first}–${d.factorRange.last}, " +
        "divide up to ${d.dividendRange.last}"

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MapColors.Paper, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MapColors.SlateMuted)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ResetButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(label)
    }
}
