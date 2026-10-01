package com.calcquest.kids.domain

import com.calcquest.kids.domain.generation.HintProvider
import com.calcquest.kids.domain.generation.QuestionGenerator
import com.calcquest.kids.domain.quests.ArithmeticOp
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HintTest {

    @Test fun specExamples() {
        assertEquals(
            "Break 7 into 2 and 5. Add 2 to reach 10, then add 5.",
            HintProvider.hint(8, ArithmeticOp.ADD, 7),
        )
        assertEquals(
            "Break 7 into 5 and 2. Take away 5 to reach 10, then take away 2.",
            HintProvider.hint(15, ArithmeticOp.SUBTRACT, 7),
        )
        assertEquals(
            "Think of 6 groups of 4. You can split them into 3 groups and 3 groups.",
            HintProvider.hint(6, ArithmeticOp.MULTIPLY, 4),
        )
        assertEquals("Think: 6 times which number makes 24?", HintProvider.hint(24, ArithmeticOp.DIVIDE, 6))
    }

    @Test fun explanationsContainCorrectEquation() {
        assertTrue(HintProvider.explanation(8, ArithmeticOp.ADD, 7).startsWith("8 + 7 = 15."))
        assertTrue(HintProvider.explanation(8, ArithmeticOp.ADD, 7).contains("8 + 2 = 10, and 10 + 5 = 15."))
        assertTrue(HintProvider.explanation(15, ArithmeticOp.SUBTRACT, 7).contains("15 − 5 = 10, and 10 − 2 = 8."))
        assertTrue(HintProvider.explanation(6, ArithmeticOp.MULTIPLY, 4).contains("3 groups of 4 make 12, and 12 + 12 = 24."))
        assertTrue(HintProvider.explanation(24, ArithmeticOp.DIVIDE, 6).contains("6 × 4 = 24"))
    }

    private val numberPattern = Regex("""(-?\d+) ([+−×]) (\d+) = (-?\d+)""")

    /** Every arithmetic statement inside any hint/explanation must be true. */
    @Test fun allStatementsAreArithmeticallyTrue() {
        for (topic in Topic.entries) for (d in Difficulty.entries) {
            for (e in QuestionGenerator.candidatePool(topic, d)) {
                for (pair in listOf(e.a to e.b, e.b to e.a)) {
                    val (a, b) = pair
                    if (topic == Topic.SUBTRACTION && a < b) continue
                    if (topic == Topic.DIVISION && (b == 0 || a % b != 0)) continue
                    val text = HintProvider.hint(a, e.op, b) + " " + HintProvider.explanation(a, e.op, b)
                    for (m in numberPattern.findAll(text)) {
                        val (x, op, y, z) = m.destructured
                        val expected = when (op) {
                            "+" -> x.toInt() + y.toInt()
                            "−" -> x.toInt() - y.toInt()
                            else -> x.toInt() * y.toInt()
                        }
                        assertEquals("'$text'", expected, z.toInt())
                    }
                    assertTrue(HintProvider.explanation(a, e.op, b).startsWith("$a ${e.op.symbol} $b = ${e.op.apply(a, b)}."))
                    // Hints never give away the answer as a final "= answer" statement of the question itself.
                    assertFalse(HintProvider.hint(a, e.op, b).contains("$a ${e.op.symbol} $b ="))
                }
            }
        }
    }

    @Test fun dotGroupsOnlyForSmallProducts() {
        assertEquals(6 to 4, HintProvider.dotGroups(6, ArithmeticOp.MULTIPLY, 4))
        assertEquals(null, HintProvider.dotGroups(12, ArithmeticOp.MULTIPLY, 12))
        assertEquals(null, HintProvider.dotGroups(8, ArithmeticOp.ADD, 7))
    }
}
