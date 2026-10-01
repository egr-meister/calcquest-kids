package com.calcquest.kids.domain

import com.calcquest.kids.domain.calculator.CalcError
import com.calcquest.kids.domain.calculator.CalcInput
import com.calcquest.kids.domain.calculator.CalcOperator
import com.calcquest.kids.domain.calculator.CalcResult
import com.calcquest.kids.domain.calculator.CalculatorEngine
import com.calcquest.kids.domain.calculator.CalculatorReducer
import com.calcquest.kids.domain.calculator.CalculatorState
import com.calcquest.kids.domain.calculator.CompletedCalculation
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    private fun bd(s: String) = BigDecimal(s)

    private fun success(a: String, op: CalcOperator, b: String): CalcResult.Success =
        CalculatorEngine.compute(bd(a), op, bd(b)) as CalcResult.Success

    /** Feeds key presses; returns final state and every completed calculation. */
    private fun press(keys: String, start: CalculatorState = CalculatorState()): Pair<CalculatorState, List<CompletedCalculation>> {
        var s = start
        val done = mutableListOf<CompletedCalculation>()
        for (k in keys) {
            val input = when (k) {
                in '0'..'9' -> CalcInput.Digit(k - '0')
                '.' -> CalcInput.Decimal
                '+' -> CalcInput.Operator(CalcOperator.ADD)
                '-' -> CalcInput.Operator(CalcOperator.SUBTRACT)
                '*' -> CalcInput.Operator(CalcOperator.MULTIPLY)
                '/' -> CalcInput.Operator(CalcOperator.DIVIDE)
                '=' -> CalcInput.Equals
                'C' -> CalcInput.Clear
                '<' -> CalcInput.Backspace
                '~' -> CalcInput.ToggleSign
                ' ' -> continue
                else -> error("bad key $k")
            }
            val r = CalculatorReducer.reduce(s, input)
            s = r.state
            r.completed?.let { done += it }
        }
        return s to done
    }

    @Test fun basicArithmetic() {
        assertEquals("15", CalculatorEngine.format(success("8", CalcOperator.ADD, "7").value))
        assertEquals("-3", CalculatorEngine.format(success("4", CalcOperator.SUBTRACT, "7").value))
        assertEquals("0.06", CalculatorEngine.format(success("0.2", CalcOperator.MULTIPLY, "0.3").value))
        assertEquals("2.5", CalculatorEngine.format(success("5", CalcOperator.DIVIDE, "2").value))
        assertEquals("0.3", CalculatorEngine.format(success("0.1", CalcOperator.ADD, "0.2").value))
    }

    @Test fun divisionRoundsHalfUpToSixDigitsAndMarksApproximate() {
        val third = success("1", CalcOperator.DIVIDE, "3")
        assertEquals("0.333333", CalculatorEngine.format(third.value))
        assertTrue(third.rounded)
        val twoThirds = success("2", CalcOperator.DIVIDE, "3")
        assertEquals("0.666667", CalculatorEngine.format(twoThirds.value))
        assertTrue(twoThirds.rounded)
        val exact = success("1", CalcOperator.DIVIDE, "8")
        assertEquals("0.125", CalculatorEngine.format(exact.value))
        assertFalse(exact.rounded)
        val longExact = success("1", CalcOperator.DIVIDE, "1024") // 0.0009765625
        assertEquals("0.000977", CalculatorEngine.format(longExact.value))
        assertTrue(longExact.rounded)
    }

    @Test fun trailingZerosRemovedWithoutExponent() {
        assertEquals("100", CalculatorEngine.format(success("50", CalcOperator.MULTIPLY, "2").value))
        assertEquals("1000000", CalculatorEngine.format(success("500000", CalcOperator.ADD, "500000").value))
        assertEquals("0", CalculatorEngine.format(success("0.5", CalcOperator.SUBTRACT, "0.5").value))
    }

    @Test fun divisionByZeroIsFriendlyErrorWithoutHistory() {
        val r = CalculatorEngine.compute(bd("5"), CalcOperator.DIVIDE, bd("0"))
        assertEquals(CalcResult.Failure(CalcError.DIVIDE_BY_ZERO), r)
        val (state, done) = press("5/0=")
        assertEquals(CalcError.DIVIDE_BY_ZERO, state.error)
        assertTrue(done.isEmpty())
        // Fix the divisor and continue.
        val (fixed, done2) = press("<2=", state)
        assertEquals("2.5", fixed.first)
        assertEquals(1, done2.size)
    }

    @Test fun limitsAreEnforced() {
        assertEquals(
            CalcResult.Failure(CalcError.OUT_OF_RANGE),
            CalculatorEngine.compute(bd("1000000"), CalcOperator.ADD, bd("1")),
        )
        assertEquals(
            CalcResult.Failure(CalcError.OUT_OF_RANGE),
            CalculatorEngine.compute(bd("1001"), CalcOperator.MULTIPLY, bd("1000")),
        )
        // Input cannot exceed 1,000,000 or six fractional digits.
        val (big, _) = press("10000000")
        assertEquals("1000000", big.first)
        val (frac, _) = press("0.1234567")
        assertEquals("0.123456", frac.first)
        val (outOfRange, done) = press("999999*9=")
        assertEquals(CalcError.OUT_OF_RANGE, outOfRange.error)
        assertTrue(done.isEmpty())
    }

    @Test fun negativeOperandsAndResults() {
        val (s, done) = press("~5*3=")
        assertEquals("-15", s.first)
        assertEquals(1, done.size)
        val (s2, _) = press("-4+1=")
        assertEquals("-3", s2.first)
        assertEquals("-2", CalculatorEngine.format(success("-6", CalcOperator.DIVIDE, "3").value))
    }

    @Test fun inputRules() {
        assertEquals("0.5", press("0..5").first.first) // no second decimal point
        assertEquals("7", press("0007").first.first) // leading zeros normalized
        assertEquals("0.", press(".").first.first)
        val replaced = press("8+*").first
        assertEquals(CalcOperator.MULTIPLY, replaced.operator)
        val noSecond = press("8+=")
        assertFalse(noSecond.first.showingResult)
        assertTrue(noSecond.second.isEmpty())
    }

    @Test fun repeatedEqualsDoesNotRepeatOrDuplicate() {
        val (s, done) = press("2+3===")
        assertEquals("5", s.first)
        assertEquals(1, done.size)
    }

    @Test fun digitAfterResultStartsNewAndOperatorContinues() {
        val (afterDigit, _) = press("2+3=7")
        assertEquals("7", afterDigit.first)
        assertNull(afterDigit.operator)
        val (continued, done) = press("2+3=*4=")
        assertEquals("20", continued.first)
        assertEquals(2, done.size)
    }

    @Test fun backspaceEditsCurrentOperandAndClearResets() {
        val (s, _) = press("12+345<")
        assertEquals("12", s.first)
        assertEquals("34", s.second)
        assertEquals(CalculatorState(), press("12+3C").first)
    }

    @Test fun useResultFromHistory() {
        val s = CalculatorReducer.reduce(press("4*").first, CalcInput.UseValue("2.5")).state
        assertEquals("2.5", s.second)
        val r = CalculatorReducer.reduce(s, CalcInput.Equals)
        assertEquals("10", r.state.first)
    }

    @Test fun roundedResultIsMarkedOnDisplay() {
        val (s, done) = press("1/3=")
        assertTrue(s.resultRounded)
        assertEquals("≈ 0.333333", s.mainLine)
        assertTrue(done.single().rounded)
    }
}
