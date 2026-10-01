package com.calcquest.kids.domain.calculator

import java.math.BigDecimal

sealed interface CalcInput {
    data class Digit(val digit: Int) : CalcInput
    data object Decimal : CalcInput
    data class Operator(val op: CalcOperator) : CalcInput
    data object Equals : CalcInput
    data object Clear : CalcInput
    data object Backspace : CalcInput
    data object ToggleSign : CalcInput
    /** "Use result" from history. */
    data class UseValue(val value: String) : CalcInput
}

data class CalculatorState(
    val first: String = "",
    val operator: CalcOperator? = null,
    val second: String = "",
    /** True right after Equals: [first] holds the result. */
    val showingResult: Boolean = false,
    /** e.g. "12 + 3 =" shown above the result. */
    val lastExpression: String? = null,
    val resultRounded: Boolean = false,
    val error: CalcError? = null,
    val notice: String? = null,
) {
    /** Operand currently being edited. */
    val currentOperand: String get() = if (operator == null || showingResult) first else second

    val expressionLine: String
        get() = when {
            error != null || showingResult -> lastExpression ?: ""
            operator != null -> listOf(first, operator.symbol, second).filter { it.isNotEmpty() }.joinToString(" ")
            else -> ""
        }

    val mainLine: String
        get() {
            val operand = currentOperand.ifEmpty { "0" }
            return if (showingResult && resultRounded) "≈ $operand" else operand
        }
}

data class CompletedCalculation(
    val firstOperand: String,
    val operator: CalcOperator,
    val secondOperand: String,
    val result: String,
    val rounded: Boolean,
)

data class ReduceResult(val state: CalculatorState, val completed: CompletedCalculation? = null)

/**
 * Pure calculator state machine: one binary operation at a time, no expression evaluation.
 * Every successful Equals (or chained operator) yields exactly one [CompletedCalculation];
 * repeated Equals yields none, so history cannot get duplicates from repeated taps.
 */
object CalculatorReducer {

    private const val LIMIT_NOTICE = "Numbers can go up to 1,000,000 with 6 decimal places."

    fun reduce(state: CalculatorState, input: CalcInput): ReduceResult {
        // Any new input dismisses a previous error or notice but keeps the operands.
        val s = state.copy(error = null, notice = null)
        return when (input) {
            is CalcInput.Digit -> ReduceResult(digit(s, input.digit))
            CalcInput.Decimal -> ReduceResult(decimal(s))
            is CalcInput.Operator -> operator(s, input.op)
            CalcInput.Equals -> equals(s)
            CalcInput.Clear -> ReduceResult(CalculatorState())
            CalcInput.Backspace -> ReduceResult(backspace(s))
            CalcInput.ToggleSign -> ReduceResult(toggleSign(s))
            is CalcInput.UseValue -> ReduceResult(useValue(s, input.value))
        }
    }

    // ----- operand editing -----

    private fun editCurrent(s: CalculatorState, transform: (String) -> String?): CalculatorState {
        if (s.showingResult) {
            // Editing a result continues from it as a plain first operand.
            val base = s.copy(showingResult = false, lastExpression = null, resultRounded = false)
            val edited = transform(base.first) ?: return base.copy(notice = LIMIT_NOTICE)
            return base.copy(first = edited)
        }
        val current = s.currentOperand
        val edited = transform(current) ?: return s.copy(notice = LIMIT_NOTICE)
        return if (s.operator == null) s.copy(first = edited) else s.copy(second = edited)
    }

    private fun digit(s: CalculatorState, d: Int): CalculatorState {
        require(d in 0..9)
        // A digit after a result starts a new calculation.
        val start = if (s.showingResult) CalculatorState() else s
        return editCurrent(start) { appendDigit(it, d) }
    }

    private fun decimal(s: CalculatorState): CalculatorState {
        val start = if (s.showingResult) CalculatorState() else s
        return editCurrent(start) { appendDecimal(it) }
    }

    private fun backspace(s: CalculatorState): CalculatorState = editCurrent(s) { operand ->
        val dropped = operand.dropLast(1)
        if (dropped == "-") "" else dropped
    }

    private fun toggleSign(s: CalculatorState): CalculatorState = editCurrent(s) { operand ->
        if (operand.startsWith("-")) operand.removePrefix("-") else "-$operand"
    }

    private fun useValue(s: CalculatorState, value: String): CalculatorState {
        val parsed = parse(value) ?: return s
        if (parsed.abs() > CalculatorEngine.LIMIT) return s.copy(notice = LIMIT_NOTICE)
        val text = CalculatorEngine.format(parsed)
        return if (s.operator != null && !s.showingResult) {
            s.copy(second = text)
        } else {
            CalculatorState(first = text)
        }
    }

    // ----- operators -----

    private fun operator(s: CalculatorState, op: CalcOperator): ReduceResult {
        if (s.showingResult) {
            // An operator after a result continues from that value.
            return ReduceResult(
                s.copy(operator = op, second = "", showingResult = false, lastExpression = null, resultRounded = false)
            )
        }
        if (s.operator == null) {
            if (parse(s.first) == null) {
                return if (op == CalcOperator.SUBTRACT && s.first.isEmpty()) {
                    ReduceResult(s.copy(first = "-"))
                } else {
                    ReduceResult(s.copy(notice = "Enter a number first."))
                }
            }
            return ReduceResult(s.copy(first = canonical(s.first), operator = op))
        }
        if (parse(s.second) == null) {
            // Second operand has not started: replace the operator.
            return ReduceResult(s.copy(operator = op, second = ""))
        }
        // Both operands present: finish this operation, then continue from its result.
        val evaluated = equals(s)
        val after = evaluated.state
        if (evaluated.completed == null) return evaluated
        return ReduceResult(
            after.copy(operator = op, second = "", showingResult = false, lastExpression = null, resultRounded = false),
            evaluated.completed,
        )
    }

    private fun equals(s: CalculatorState): ReduceResult {
        if (s.showingResult) return ReduceResult(s) // no repeated operation
        val op = s.operator ?: return ReduceResult(s)
        val a = parse(s.first) ?: return ReduceResult(s.copy(notice = "Enter a number first."))
        val b = parse(s.second) ?: return ReduceResult(s.copy(notice = "Enter the second number."))
        val firstText = CalculatorEngine.format(a)
        val secondText = CalculatorEngine.format(b)
        val expression = "$firstText ${op.symbol} $secondText ="
        return when (val r = CalculatorEngine.compute(a, op, b)) {
            is CalcResult.Failure -> ReduceResult(s.copy(error = r.error, lastExpression = expression))
            is CalcResult.Success -> {
                val resultText = CalculatorEngine.format(r.value)
                ReduceResult(
                    CalculatorState(
                        first = resultText,
                        showingResult = true,
                        lastExpression = expression,
                        resultRounded = r.rounded,
                    ),
                    CompletedCalculation(firstText, op, secondText, resultText, r.rounded),
                )
            }
        }
    }

    // ----- helpers -----

    fun parse(operand: String): BigDecimal? {
        val trimmed = operand.removeSuffix(".")
        if (trimmed.isEmpty() || trimmed == "-") return null
        return trimmed.toBigDecimalOrNull()
    }

    private fun canonical(operand: String): String =
        parse(operand)?.let { CalculatorEngine.format(it) } ?: operand

    /** Returns null when the digit would break a limit. */
    fun appendDigit(operand: String, d: Int): String? {
        val negative = operand.startsWith("-")
        val body = operand.removePrefix("-")
        val newBody = when {
            body.isEmpty() || body == "0" -> d.toString() // normalize leading zeros
            body.contains('.') -> {
                if (body.substringAfter('.').length >= CalculatorEngine.MAX_FRACTION_DIGITS) return null
                body + d
            }
            else -> body + d
        }
        val candidate = if (negative) "-$newBody" else newBody
        val value = parse(candidate) ?: return candidate
        return if (value.abs() > CalculatorEngine.LIMIT) null else candidate
    }

    /** Prevents multiple decimal points; "." on an empty operand becomes "0.". */
    fun appendDecimal(operand: String): String? {
        val negative = operand.startsWith("-")
        val body = operand.removePrefix("-")
        if (body.contains('.')) return operand
        val newBody = if (body.isEmpty()) "0." else "$body."
        return if (negative) "-$newBody" else newBody
    }
}
