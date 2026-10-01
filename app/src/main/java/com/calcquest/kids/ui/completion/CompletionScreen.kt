package com.calcquest.kids.ui.completion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcquest.kids.R
import com.calcquest.kids.ui.common.AppIcon
import com.calcquest.kids.ui.common.SimpleTopBar
import com.calcquest.kids.ui.theme.MapColors
import com.calcquest.kids.ui.theme.accent
import com.calcquest.kids.ui.theme.soft

@Composable
fun CompletionScreen(
    viewModel: CompletionViewModel,
    onBackToMap: () -> Unit,
    onOpenAttempt: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showReview by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.openAttemptEvents.collect { onOpenAttempt(it) }
    }

    Scaffold(
        containerColor = MapColors.Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { SimpleTopBar(title = "Level complete", onBack = onBackToMap, backLabel = "Back to map") },
    ) { padding ->
        val topic = state.topic
        if (state.loading || topic == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CompletionBadge(animate = state.animate, color = topic.accent(), background = topic.soft())
                Spacer(Modifier.height(16.dp))
                Text(
                    topic.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text("${state.difficulty.label} route", style = MaterialTheme.typography.bodyMedium, color = MapColors.SlateMuted)
                Spacer(Modifier.height(12.dp))
                Text("${state.solved} of ${state.total} solved", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Correct on the first try: ${state.firstTryCount} of ${state.total}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (state.routeComplete && state.nextTopic == null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(color = MapColors.SuccessSoft, shape = MaterialTheme.shapes.medium) {
                        Text(
                            "Your quest route is complete!",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = { showReview = !showReview },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (showReview) "Hide review" else "Review questions") }
                if (showReview) {
                    Spacer(Modifier.height(8.dp))
                    ReviewList(state.review)
                }
                state.nextTopic?.let { next ->
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::continueToNext,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("Continue to next level: ${next.title}") }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBackToMap, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Back to map")
                }
            }
        }
    }
}

@Composable
private fun CompletionBadge(animate: Boolean, color: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color) {
    // Brief, single scale-in; skipped entirely when decorative animation is reduced.
    val scale = remember { Animatable(if (animate) 0.6f else 1f) }
    LaunchedEffect(animate) {
        if (animate) scale.animateTo(1f, tween(durationMillis = 350))
    }
    Surface(
        color = background,
        shape = CircleShape,
        border = BorderStroke(3.dp, color),
        modifier = Modifier
            .size(112.dp)
            .scale(scale.value)
            .clearAndSetSemantics { contentDescription = "Level complete" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppIcon(R.drawable.ic_check, contentDescription = null, tint = MapColors.Success, modifier = Modifier.size(64.dp))
        }
    }
}

@Composable
private fun ReviewList(items: List<ReviewItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            val notes = buildList {
                add(if (item.firstTry) "first try" else "solved after trying again")
                if (item.hintUsed) add("hint used")
            }.joinToString(", ")
            Surface(
                color = MapColors.Paper,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = "Question ${item.number}: ${item.spoken}. $notes." },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${item.number}.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.equation, style = MaterialTheme.typography.titleMedium)
                        Text(notes, style = MaterialTheme.typography.bodyMedium, color = MapColors.SlateMuted)
                    }
                    if (item.firstTry) AppIcon(R.drawable.ic_check, contentDescription = null, tint = MapColors.Success)
                }
            }
        }
    }
}
