package com.calcquest.kids.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcquest.kids.R
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.LevelNode
import com.calcquest.kids.domain.quests.LevelStatus
import com.calcquest.kids.domain.quests.QUESTIONS_PER_LEVEL
import com.calcquest.kids.domain.quests.Topic
import com.calcquest.kids.ui.common.AppIcon
import com.calcquest.kids.ui.common.bottomBarInsets
import com.calcquest.kids.ui.common.topBarInsets
import com.calcquest.kids.ui.common.ConfirmDialog
import com.calcquest.kids.ui.common.InfoDialog
import com.calcquest.kids.ui.theme.MapColors
import com.calcquest.kids.ui.theme.accent
import com.calcquest.kids.ui.theme.soft

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onOpenAttempt: (Long) -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenParents: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    var showList by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.openAttemptEvents.collect { onOpenAttempt(it) }
    }

    Scaffold(
        containerColor = MapColors.Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            MapTopUtility(
                difficulty = state.difficulty,
                showList = showList,
                onToggleList = { showList = !showList },
                onOpenParents = onOpenParents,
            )
        },
        bottomBar = { CalculatorBar(onOpenCalculator) },
    ) { padding ->
        val route = state.route
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (route != null) {
                if (showList) {
                    RouteList(route.levels, route.isRouteComplete, viewModel::onLevelSelected)
                } else {
                    IllustratedMap(route.levels, route.isRouteComplete, viewModel::onLevelSelected)
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is MapDialog.Disabled -> InfoDialog(
            title = d.topic.title,
            message = "A parent has turned this level off. It can be turned back on in Parent settings.",
            onDismiss = viewModel::dismissDialog,
        )
        is MapDialog.Locked -> InfoDialog(
            title = "${d.topic.title} is locked",
            message = "Finish all 10 questions in ${d.required.title} to open ${d.topic.title}.",
            onDismiss = viewModel::dismissDialog,
        )
        is MapDialog.ResumeOrRestart -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(d.topic.title) },
            text = { Text("You have a quest in progress here. Pick up where you left off, or start again?", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { viewModel.resume(d.topic) }) { Text("Resume") } },
            dismissButton = { TextButton(onClick = { viewModel.askRestart(d.topic) }) { Text("Restart") } },
        )
        is MapDialog.ConfirmRestart -> ConfirmDialog(
            title = "Start ${d.topic.title} again?",
            message = "The answers in this unfinished quest will be cleared. Levels you already completed stay completed.",
            confirmLabel = "Restart",
            onConfirm = { viewModel.confirmRestart(d.topic) },
            onDismiss = viewModel::dismissDialog,
        )
    }
}

@Composable
private fun MapTopUtility(
    difficulty: Difficulty,
    showList: Boolean,
    onToggleList: () -> Unit,
    onOpenParents: () -> Unit,
) {
    Surface(color = MapColors.Cream) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .topBarInsets()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Quest Map",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Difficulty: ${difficulty.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onToggleList,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                AppIcon(if (showList) R.drawable.ic_map else R.drawable.ic_list, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(if (showList) "Map" else "List")
            }
            ParentGateButton(onOpen = onOpenParents)
        }
    }
}

@Composable
private fun CalculatorBar(onOpenCalculator: () -> Unit) {
    Surface(color = MapColors.Paper, shadowElevation = 6.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .bottomBarInsets()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = onOpenCalculator,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                AppIcon(R.drawable.ic_calculator, contentDescription = null, tint = Color.White)
                Spacer(Modifier.size(10.dp))
                Text("Calculator", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Illustrated map
// ------------------------------------------------------------------------------------------

@Composable
private fun IllustratedMap(levels: List<LevelNode>, routeComplete: Boolean, onSelect: (LevelNode) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .drawBehind { drawMapDecor() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (routeComplete) RouteCompleteBanner()
            if (wide) {
                WideRoute(levels, onSelect)
            } else {
                NarrowRoute(levels, onSelect)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NarrowRoute(levels: List<LevelNode>, onSelect: (LevelNode) -> Unit) {
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        levels.forEachIndexed { index, node ->
            val leftSide = index % 2 == 0
            Box(Modifier.fillMaxWidth(), contentAlignment = if (leftSide) Alignment.CenterStart else Alignment.CenterEnd) {
                LandmarkCard(node, onSelect, Modifier.fillMaxWidth(0.86f))
            }
            if (index < levels.lastIndex) {
                PathConnector(
                    fromFraction = if (leftSide) 0.3f else 0.7f,
                    toFraction = if (leftSide) 0.7f else 0.3f,
                    active = levels[index + 1].isPlayable,
                    horizontal = false,
                )
            }
        }
    }
}

@Composable
private fun WideRoute(levels: List<LevelNode>, onSelect: (LevelNode) -> Unit) {
    // Two columns, logical order kept: row 1 = levels 1 → 2, row 2 = levels 3 → 4.
    Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
        levels.chunked(2).forEachIndexed { rowIndex, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LandmarkCard(row[0], onSelect, Modifier.weight(1f))
                if (row.size > 1) {
                    Box(Modifier.size(width = 56.dp, height = 24.dp)) {
                        PathConnector(0f, 1f, active = row[1].isPlayable, horizontal = true)
                    }
                    LandmarkCard(row[1], onSelect, Modifier.weight(1f))
                }
            }
            val nextIndex = (rowIndex + 1) * 2
            if (nextIndex < levels.size) {
                PathConnector(fromFraction = 0.75f, toFraction = 0.25f, active = levels[nextIndex].isPlayable, horizontal = false)
            }
        }
    }
}

@Composable
private fun PathConnector(fromFraction: Float, toFraction: Float, active: Boolean, horizontal: Boolean) {
    val color = if (active) MapColors.Path else MapColors.Disabled
    Canvas(
        modifier = (if (horizontal) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(52.dp))
    ) {
        val dots = PathEffect.dashPathEffect(floatArrayOf(1f, 14.dp.toPx()), 0f)
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots)
        val path = Path()
        if (horizontal) {
            path.moveTo(0f, size.height / 2)
            path.lineTo(size.width, size.height / 2)
        } else {
            val x1 = size.width * fromFraction
            val x2 = size.width * toFraction
            path.moveTo(x1, 0f)
            path.cubicTo(x1, size.height * 0.6f, x2, size.height * 0.4f, x2, size.height)
        }
        drawPath(path, color, style = stroke)
    }
}

/** Decorative, static background: soft hills and grass dots. Hidden from accessibility. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMapDecor() {
    val hill = MapColors.CreamDark
    val w = size.width
    val h = size.height
    var y = 120.dp.toPx()
    var flip = false
    while (y < h) {
        val cx = if (flip) w * 0.9f else w * 0.08f
        drawCircle(hill, radius = 70.dp.toPx(), center = Offset(cx, y))
        drawCircle(MapColors.TrailSoft.copy(alpha = 0.6f), radius = 6.dp.toPx(), center = Offset(w * 0.5f + (if (flip) -1 else 1) * w * 0.42f, y + 90.dp.toPx()))
        y += 260.dp.toPx()
        flip = !flip
    }
}

@Composable
private fun RouteCompleteBanner() {
    Surface(
        color = MapColors.SuccessSoft,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(2.dp, MapColors.Success),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_check, contentDescription = null, tint = MapColors.Success)
            Spacer(Modifier.size(12.dp))
            Text(
                "Your quest route is complete!",
                style = MaterialTheme.typography.titleMedium,
                color = MapColors.Slate,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

private fun Topic.landmarkIcon(): Int = when (this) {
    Topic.ADDITION -> R.drawable.ic_landmark_trail
    Topic.SUBTRACTION -> R.drawable.ic_landmark_cave
    Topic.MULTIPLICATION -> R.drawable.ic_landmark_tower
    Topic.DIVISION -> R.drawable.ic_landmark_gate
}

/** Status text that never relies on colour alone. */
fun LevelNode.statusText(): String = when (val s = status) {
    LevelStatus.Available -> "Ready to start"
    is LevelStatus.InProgress -> "In progress"
    LevelStatus.Completed -> if (hasActiveAttempt) "Completed · replay in progress" else "Completed"
    is LevelStatus.Locked -> "Locked · finish ${s.requiredTopic.title} first"
    LevelStatus.DisabledByParent -> "Turned off by a parent"
}

fun LevelNode.progressText(): String = "$solvedCount of $QUESTIONS_PER_LEVEL solved"

fun LevelNode.accessibilityLabel(): String =
    "Level ${topic.level}, ${topic.title}. ${statusText()}. ${progressText()}."

@Composable
private fun LandmarkCard(node: LevelNode, onSelect: (LevelNode) -> Unit, modifier: Modifier = Modifier) {
    val dimmed = node.status is LevelStatus.Locked || node.status == LevelStatus.DisabledByParent
    val accent = node.topic.accent()
    Surface(
        onClick = { onSelect(node) },
        color = MapColors.Paper,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(if (node.status == LevelStatus.Completed) 3.dp else 2.dp, if (dimmed) MapColors.Disabled else accent),
        shadowElevation = 2.dp,
        modifier = modifier
            .heightIn(min = 96.dp)
            .clearAndSetSemantics {
                contentDescription = node.accessibilityLabel()
                stateDescription = node.statusText()
                role = Role.Button
                onClick(label = if (node.isPlayable) "Open level" else "Why is this closed?") { onSelect(node); true }
            },
    ) {
        Row(
            Modifier
                .padding(14.dp)
                .alpha(if (dimmed) 0.6f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(node.topic.soft()),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(node.topic.landmarkIcon()), contentDescription = null, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Level ${node.topic.level}", style = MaterialTheme.typography.labelLarge, color = MapColors.SlateMuted)
                Text(node.topic.title, style = MaterialTheme.typography.titleLarge, color = MapColors.Slate)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (node.status) {
                        LevelStatus.Completed -> R.drawable.ic_check
                        is LevelStatus.Locked -> R.drawable.ic_lock
                        LevelStatus.DisabledByParent -> R.drawable.ic_close
                        else -> R.drawable.ic_play
                    }
                    val tint = if (node.status == LevelStatus.Completed) MapColors.Success else MapColors.Slate
                    AppIcon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(node.statusText(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                if (node.status != LevelStatus.DisabledByParent) {
                    Spacer(Modifier.height(6.dp))
                    Text(node.progressText(), style = MaterialTheme.typography.bodyMedium, color = MapColors.SlateMuted)
                    LinearProgressIndicator(
                        progress = { node.solvedCount / QUESTIONS_PER_LEVEL.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = accent,
                        trackColor = MapColors.CreamDark,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Accessible ordered list equivalent
// ------------------------------------------------------------------------------------------

@Composable
private fun RouteList(levels: List<LevelNode>, routeComplete: Boolean, onSelect: (LevelNode) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (routeComplete) RouteCompleteBanner()
        Text(
            "Quest route, in order",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        levels.forEach { node ->
            Surface(
                onClick = { onSelect(node) },
                color = MapColors.Paper,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .heightIn(min = 56.dp)
                    .clearAndSetSemantics {
                        contentDescription = node.accessibilityLabel()
                        role = Role.Button
                        onClick { onSelect(node); true }
                    },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${node.topic.level}.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(node.topic.title, style = MaterialTheme.typography.titleMedium)
                        Text("${node.statusText()} · ${node.progressText()}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
