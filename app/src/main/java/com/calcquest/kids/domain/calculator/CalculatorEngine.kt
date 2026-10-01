package com.calcquest.kids.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode

enum class CalcOperator(val symbol: String, val spoken: String) {
    ADD("+", "plus"),
    SUBTRACT("−", "minus"),
    MULTIPLY("×", "times"),
    DIVIDE("÷", "divided by");

    companion object {
        fun fromSymbol(symbol: String?): CalcOperator? = entries.firstOrNull { it.symbol == symbol }
    }
}

enum class CalcError(val message: String) {
    DIVIDE_BY_ZERO("You can't divide by zero. Try another number."),
    OUT_OF_RANGE("That number is too big for this calculator."),
}

sealed interface CalcResult {
    data class Success(val value: BigDecimal, val rounded: Boolean) : CalcResult
    data class Failure(val error: CalcError) : CalcResult
}

/**
 * Single binary operation with BigDecimal.
 *
 * - |operand| and |result| ≤ 1,000,000
 * - at most 6 fractional digits per operand
 * - results are rounded to 6 fractional digits with HALF_UP (division and products
 *   of fractional operands); rounding is reported so the UI can mark "≈"
 */
object CalculatorEngine {
    val LIMIT: BigDecimal = BigDecimal("1000000")
    const val MAX_FRACTION_DIGITS = 6

    fun compute(a: BigDecimal, op: CalcOperator, b: BigDecimal): CalcResult {
        if (a.abs() > LIMIT || b.abs() > LIMIT) return CalcResult.Failure(CalcError.OUT_OF_RANGE)
        val exact: BigDecimal? = when (op) {
            CalcOperator.ADD -> a.add(b)
            CalcOperator.SUBTRACT -> a.subtract(b)
            CalcOperator.MULTIPLY -> a.multiply(b)
            CalcOperator.DIVIDE -> null
        }
        val rounded: BigDecimal
        val wasRounded: Boolean
        if (op == CalcOperator.DIVIDE) {
            if (b.signum() == 0) return CalcResult.Failure(CalcError.DIVIDE_BY_ZERO)
            rounded = a.divide(b, MAX_FRACTION_DIGITS, RoundingMode.HALF_UP)
            // With scale 6, q * b == a exactly iff the true quotient has ≤ 6 decimals.
            wasRounded = rounded.multiply(b).compareTo(a) != 0
        } else {
            val value = exact!!
            rounded = value.setScale(MAX_FRACTION_DIGITS, RoundingMode.HALF_UP)
            wasRounded = rounded.compareTo(value) != 0
        }
        val normalized = normalize(rounded)
        if (normalized.abs() > LIMIT) return CalcResult.Failure(CalcError.OUT_OF_RANGE)
        return CalcResult.Success(normalized, wasRounded)
    }

    /** Removes unnecessary trailing zeros and never produces exponent notation. */
    fun normalize(value: BigDecimal): BigDecimal {
        if (value.signum() == 0) return BigDecimal.ZERO
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }

    fun format(value: BigDecimal): String = normalize(value).toPlainString()
}
