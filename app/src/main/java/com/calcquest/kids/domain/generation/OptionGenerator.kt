package com.calcquest.kids.domain.generation

import com.calcquest.kids.domain.quests.ArithmeticOp
import com.calcquest.kids.domain.quests.OPTIONS_PER_QUESTION
import kotlin.random.Random

/**
 * Builds four distinct integer answer choices with exactly one correct answer.
 *
 * Distractors are plausible mistakes for the operation; a bounded fallback
 * (answer ± 1, ± 2, ...) guarantees enough choices without unbounded loops.
 */
class OptionGenerator(private val random: Random) {

    fun options(expr: Expression): List<Int> {
        val answer = expr.answer
        val minAllowed = minimumValue(expr.op)
        val distractors = LinkedHashSet<Int>()

        for (candidate in plausibleDistractors(expr).shuffled(random)) {
            if (distractors.size >= OPTIONS_PER_QUESTION - 1) break
            if (candidate != answer && candidate >= minAllowed) distractors.add(candidate)
        }

        // Bounded fallback: answer + d is always valid, so at most 3 extra steps are needed.
        var d = 1
        while (distractors.size < OPTIONS_PER_QUESTION - 1 && d <= MAX_FALLBACK_OFFSET) {
            val up = answer + d
            val down = answer - d
            if (down >= minAllowed && down != answer && random.nextBoolean()) distractors.add(down)
            if (distractors.size < OPTIONS_PER_QUESTION - 1) distractors.add(up)
            d++
        }

        return (distractors.take(OPTIONS_PER_QUESTION - 1) + answer).shuffled(random)
    }

    private fun minimumValue(op: ArithmeticOp): Int = when (op) {
        ArithmeticOp.ADD, ArithmeticOp.SUBTRACT -> 0
        ArithmeticOp.MULTIPLY, ArithmeticOp.DIVIDE -> 1
    }

    /** Operation-specific mistakes children commonly make. */
    fun plausibleDistractors(expr: Expression): List<Int> {
        val (a, op, b) = Triple(expr.a, expr.op, expr.b)
        val ans = expr.answer
        return when (op) {
            ArithmeticOp.ADD -> listOf(
                ans + 1, ans - 1, // one-step counting errors
                ans + 2, ans - 2,
                ans + 10, ans - 10, // tens slip
                kotlin.math.abs(a - b), // subtracted instead of added
            )
            ArithmeticOp.SUBTRACT -> listOf(
                ans + 1, ans - 1,
                ans + 2, ans - 2,
                a + b, // added instead of subtracted
                ans + 10, ans - 10,
            )
            ArithmeticOp.MULTIPLY -> listOf(
                a * (b + 1), a * (b - 1), // nearby products
                (a + 1) * b, (a - 1) * b,
                a + b, // added instead of multiplied
                ans + 1, ans - 1,
            )
            ArithmeticOp.DIVIDE -> listOf(
                ans + 1, ans - 1, // nearby quotients
                ans + 2, ans - 2,
                a - b, // subtracted instead of divided
                b, // repeated the divisor
            )
        }
    }

    private companion object {
        const val MAX_FALLBACK_OFFSET = 50
    }
}
