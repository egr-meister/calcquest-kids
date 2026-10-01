package com.calcquest.kids.ui.question

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcquest.kids.R
import com.calcquest.kids.ui.common.AppIcon
import com.calcquest.kids.ui.common.SimpleTopBar
import com.calcquest.kids.ui.theme.MapColors
import com.calcquest.kids.ui.theme.accent
import com.calcquest.kids.ui.theme.soft

@Composable
fun QuestionScreen(
    viewModel: QuestionViewModel,
    onBack: () -> Unit,
    onShowCompletion: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is QuestionEvent.ShowCompletion -> onShowCompletion(event.attemptId)
                QuestionEvent.Close -> onBack()
            }
        }
    }

    // Timer runs only while this screen is resumed; leaving or backgrounding pauses and persists it.
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenVisible()
        onPauseOrDispose { viewModel.onScreenHidden() }
    }

    val topic = state.topic
    Scaffold(
        containerColor = MapColors.Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SimpleTopBar(
                title = topic?.title ?: "",
                onBack = onBack,
                backLabel = "Back to map",
                actions = {
                    Surface(color = MapColors.Paper, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(end = 12.dp)) {
                        Text(
                            state.difficulty.label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .semantics { contentDescription = "Difficulty ${state.difficulty.label}" },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading || topic == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                ProgressHeader(state)
                state.timer?.let { TimerRow(it, viewModel::onResumeTimer) }
                Spacer(Modifier.height(12.dp))
                ExpressionCard(state)
                Spacer(Modifier.height(16.dp))
                AnswerGrid(state, viewModel::onAnswer)
                Spacer(Modifier.height(12.dp))
                state.feedback?.let { FeedbackCard(it) }
                Spacer(Modifier.height(12.dp))
                ActionRow(state, onHint = viewModel::openHint, onNext = viewModel::onNext)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (state.hintOpen) {
        HintDialog(state, onClose = viewModel::closeHint)
    }
}

@Composable
private fun ProgressHeader(state: QuestionUiState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Question ${state.position + 1} of ${state.total}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Text("${state.solvedCount} of ${state.total} solved", style = MaterialTheme.typography.bodyMedium, color = MapColors.SlateMuted)
    }
    LinearProgressIndicator(
        progress = { state.solvedCount / state.total.toFloat() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(8.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "${state.solvedCount} of ${state.total} questions solved" },
        color = state.topic?.accent() ?: MaterialTheme.colorScheme.primary,
        trackColor = MapColors.CreamDark,
    )
}

private fun formatClock(millis: Long): String {
    val totalSeconds = ((millis + 999) / 1000).toInt()
    return String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun TimerRow(timer: TimerUi, onResume: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Surface(color = MapColors.Paper, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_timer, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            when {
                timer.expired -> Text(
                    "Time’s up — keep going at your pace.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                timer.waitingForResume -> {
                    Text(
                        "Timer paused at ${formatClock(timer.remainingMillis)}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) { Text("Resume timer") }
                }
                else -> Text(
                    "Challenge Timer: ${formatClock(timer.remainingMillis)}" + if (timer.running) "" else " (paused)",
                    style = MaterialTheme.typography.bodyLarge,
                    // Avoid announcing every tick; the value is read on focus.
                    modifier = Modifier.semantics {
                        contentDescription = "Challenge Timer, ${(timer.remainingMillis + 999) / 1000} seconds left"
                    },
                )
            }
        }
    }
}

@Composable
private fun ExpressionCard(state: QuestionUiState) {
    val topic = state.topic ?: return
    Surface(
        color = topic.soft(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(2.dp, topic.accent()),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            state.expression,
            style = MaterialTheme.typography.displayLarge,
            color = MapColors.Slate,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 12.dp)
                .clearAndSetSemantics {
                    contentDescription = state.spokenExpression
                    heading()
                },
        )
    }
}

@Composable
private fun AnswerGrid(state: QuestionUiState, onAnswer: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val columns = if (maxWidth < 320.dp || fontScale > 1.6f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.options.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { option ->
                        AnswerButton(option, state.solved, onAnswer, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerButton(option: OptionUi, questionSolved: Boolean, onAnswer: (Int) -> Unit, modifier: Modifier) {
    val enabled = !questionSolved && !option.triedWrong
    val (container, content, mark) = when {
        option.isCorrectAndSolved -> Triple(MapColors.SuccessSoft, MapColors.Success, "✓ ")
        option.triedWrong -> Triple(MapColors.GentleSoft, MapColors.Gentle, "✗ ")
        else -> Triple(MapColors.Paper, MapColors.Slate, "")
    }
    val stateText = when {
        option.isCorrectAndSolved -> "Correct answer"
        option.triedWrong -> "Already tried, not this one"
        else -> null
    }
    Button(
        onClick = { onAnswer(option.value) },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        border = BorderStroke(2.dp, if (option.isCorrectAndSolved) MapColors.Success else MapColors.Path.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics {
                contentDescription = "Answer ${option.value}"
                if (stateText != null) stateDescription = stateText
            },
    ) {
        Text(
            mark + option.value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FeedbackCard(feedback: Feedback) {
    val (title, body, color, soft) = when (feedback) {
        is Feedback.Correct -> Quad("That’s right!", feedback.explanation, MapColors.Success, MapColors.SuccessSoft)
        is Feedback.TryAgain -> Quad("Try another answer.", feedback.guidance, MapColors.Gentle, MapColors.GentleSoft)
    }
    Surface(
        color = soft,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, color),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = color)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MapColors.Slate)
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun ActionRow(state: QuestionUiState, onHint: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onHint,
            enabled = !state.solved,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            AppIcon(R.drawable.ic_hint, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Hint")
        }
        Button(
            onClick = onNext,
            enabled = state.solved,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            Text(if (state.allSolved) "See results" else "Next question")
        }
    }
}

@Composable
private fun HintDialog(state: QuestionUiState, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Hint") },
        text = {
            Column {
                Text(state.hint, style = MaterialTheme.typography.bodyLarge)
                state.dotGroups?.let { (groups, dots) ->
                    Spacer(Modifier.height(12.dp))
                    DotGroups(groups, dots)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Back to the question") } },
    )
}

/** Small dot picture with a text equivalent for screen readers. */
@Composable
private fun DotGroups(groups: Int, dotsPerGroup: Int) {
    val dotColor = MapColors.Slate
    val groupColor = MapColors.TowerSoft
    Column(
        Modifier.clearAndSetSemantics { contentDescription = "Picture: $groups groups with $dotsPerGroup dots in each group" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(groups) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp),
            ) {
                val r = 6.dp.toPx()
                val gap = 6.dp.toPx()
                drawRoundRect(groupColor, size = androidx.compose.ui.geometry.Size(dotsPerGroup * (2 * r + gap) + gap, size.height))
                for (i in 0 until dotsPerGroup) {
                    drawCircle(dotColor, radius = r, center = Offset(gap + r + i * (2 * r + gap), size.height / 2))
                }
            }
        }
    }
}
