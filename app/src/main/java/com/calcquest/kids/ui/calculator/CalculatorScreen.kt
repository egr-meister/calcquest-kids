package com.calcquest.kids.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcquest.kids.R
import com.calcquest.kids.domain.calculator.CalcInput
import com.calcquest.kids.domain.calculator.CalcOperator
import com.calcquest.kids.domain.calculator.CalculatorState
import com.calcquest.kids.ui.common.AppIcon
import com.calcquest.kids.ui.common.SimpleTopBar
import com.calcquest.kids.ui.theme.MapColors

private data class Key(val label: String, val spoken: String, val input: CalcInput, val kind: KeyKind, val span: Int = 1)

private enum class KeyKind { DIGIT, OPERATOR, ACTION, EQUALS }

private fun digit(d: Int) = Key(d.toString(), d.toString(), CalcInput.Digit(d), KeyKind.DIGIT)
private fun op(o: CalcOperator) = Key(o.symbol, o.spoken, CalcInput.Operator(o), KeyKind.OPERATOR)

private val keyRows: List<List<Key>> = listOf(
    listOf(
        Key("C", "clear", CalcInput.Clear, KeyKind.ACTION),
        Key("±", "change sign", CalcInput.ToggleSign, KeyKind.ACTION),
        Key("⌫", "backspace", CalcInput.Backspace, KeyKind.ACTION),
        op(CalcOperator.DIVIDE),
    ),
    listOf(digit(7), digit(8), digit(9), op(CalcOperator.MULTIPLY)),
    listOf(digit(4), digit(5), digit(6), op(CalcOperator.SUBTRACT)),
    listOf(digit(1), digit(2), digit(3), op(CalcOperator.ADD)),
    listOf(
        digit(0).copy(span = 2),
        Key(".", "decimal point", CalcInput.Decimal, KeyKind.DIGIT),
        Key("=", "equals", CalcInput.Equals, KeyKind.EQUALS),
    ),
)

/** Reads symbols as words for screen readers ("12 divided by 4"). */
fun spokenExpression(text: String): String = text
    .replace("+", " plus ")
    .replace("−", " minus ")
    .replace("×", " times ")
    .replace("÷", " divided by ")
    .replace("=", " equals ")
    .replace("≈", " approximately ")
    .replace(Regex("(^|\\s)-"), "$1negative ")
    .replace(Regex("\\s+"), " ")
    .trim()

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MapColors.Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SimpleTopBar(
                title = "Calculator",
                onBack = onBack,
                backLabel = "Back to map",
                actions = {
                    OutlinedButton(
                        onClick = onOpenHistory,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .heightIn(min = 48.dp),
                    ) {
                        AppIcon(R.drawable.ic_history, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("History")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
        ) {
            val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
            if (landscape) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        Display(state)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Keypad(viewModel::onInput)
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = 520.dp)
                        .align(Alignment.TopCenter),
                ) {
                    Display(state)
                    Spacer(Modifier.height(12.dp))
                    Keypad(viewModel::onInput)
                }
            }
        }
    }
}

@Composable
private fun Display(state: CalculatorState) {
    val message = state.error?.message ?: state.notice
    Surface(color = MapColors.Paper, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
            Text(
                state.expressionLine.ifEmpty { " " },
                style = MaterialTheme.typography.titleLarge,
                color = MapColors.SlateMuted,
                maxLines = 2,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = if (state.expressionLine.isEmpty()) "" else spokenExpression(state.expressionLine)
                    },
            )
            val main = if (state.error != null) "Oops" else state.mainLine
            val size = when {
                main.length <= 9 -> 52.sp
                main.length <= 13 -> 40.sp
                else -> 30.sp
            }
            Text(
                main,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = size, lineHeight = size * 1.15f),
                color = MapColors.Slate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = (if (state.showingResult) "Result " else "") + spokenExpression(main)
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            if (state.showingResult && state.resultRounded) {
                Text("Rounded to 6 decimal places", style = MaterialTheme.typography.bodyMedium, color = MapColors.SlateMuted)
            }
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MapColors.Gentle,
                    textAlign = TextAlign.End,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Keypad(onInput: (CalcInput) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        keyRows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 52.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { key ->
                    KeyButton(key, onInput, Modifier.weight(key.span.toFloat()).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun KeyButton(key: Key, onInput: (CalcInput) -> Unit, modifier: Modifier) {
    val (container, content) = when (key.kind) {
        KeyKind.DIGIT -> MapColors.Paper to MapColors.Slate
        KeyKind.OPERATOR -> MapColors.CaveSoft to MapColors.Slate
        KeyKind.ACTION -> MapColors.GateSoft to MapColors.Slate
        KeyKind.EQUALS -> Color(0xFF4F6F52) to Color.White
    }
    Button(
        onClick = { onInput(key.input) },
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = MaterialTheme.shapes.medium,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
        modifier = modifier
            .heightIn(min = 52.dp)
            .semantics { contentDescription = key.spoken },
    ) {
        Text(key.label, fontSize = 28.sp, maxLines = 1)
    }
}
