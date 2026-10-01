package com.calcquest.kids.domain

import com.calcquest.kids.domain.generation.Expression
import com.calcquest.kids.domain.generation.OptionGenerator
import com.calcquest.kids.domain.generation.QuestionGenerator
import com.calcquest.kids.domain.quests.ArithmeticOp
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.QUESTIONS_PER_LEVEL
import com.calcquest.kids.domain.quests.Question
import com.calcquest.kids.domain.quests.Topic
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationTest {

    private fun assertValid(q: Question, topic: Topic, d: Difficulty) {
        assertEquals(topic.op, q.op)
        assertEquals(q.op.apply(q.a, q.b), q.answer)
        when (topic) {
            Topic.ADDITION -> {
                assertTrue(q.a in d.addSubRange && q.b in d.addSubRange)
            }
            Topic.SUBTRACTION -> {
                assertTrue(q.a in d.addSubRange && q.b in d.addSubRange)
                assertTrue("nonnegative", q.answer >= 0)
            }
            Topic.MULTIPLICATION -> assertTrue(q.a in d.factorRange && q.b in d.factorRange)
            Topic.DIVISION -> {
                assertTrue(q.a in d.dividendRange)
                assertTrue(q.b in d.divisorRange && q.b != 0)
                assertEquals(0, q.a % q.b)
                assertTrue(q.answer > 0)
            }
        }
        // Four distinct integer options, exactly one correct.
        assertEquals(4, q.options.size)
        assertEquals(4, q.options.toSet().size)
        assertEquals(1, q.options.count { it == q.answer })
        val min = if (topic == Topic.ADDITION || topic == Topic.SUBTRACTION) 0 else 1
        assertTrue("options >= $min: ${q.options}", q.options.all { it >= min })
    }

    @Test fun validAttemptsForEveryTopicAndDifficulty() {
        for (seed in 0 until 40) {
            val gen = QuestionGenerator(Random(seed))
            for (topic in Topic.entries) for (d in Difficulty.entries) {
                val attempt = gen.generateAttempt(topic, d)
                assertEquals(QUESTIONS_PER_LEVEL, attempt.size)
                attempt.forEach { assertValid(it, topic, d) }
                assertEquals("no duplicates", QUESTIONS_PER_LEVEL, attempt.map { it.key }.toSet().size)
            }
        }
    }

    @Test fun reversedOperandsCountAsDuplicates() {
        val a = Question(3, ArithmeticOp.ADD, 8, 11, listOf())
        val b = Question(8, ArithmeticOp.ADD, 3, 11, listOf())
        assertEquals(a.key, b.key)
        val m1 = Question(4, ArithmeticOp.MULTIPLY, 6, 24, listOf())
        val m2 = Question(6, ArithmeticOp.MULTIPLY, 4, 24, listOf())
        assertEquals(m1.key, m2.key)
        val s1 = Question(8, ArithmeticOp.SUBTRACT, 3, 5, listOf())
        assertTrue(s1.key != Question(3, ArithmeticOp.SUBTRACT, 8, -5, listOf()).key)
    }

    @Test fun seededGenerationIsReproducible() {
        val one = QuestionGenerator(Random(42)).generateAttempt(Topic.MULTIPLICATION, Difficulty.MEDIUM)
        val two = QuestionGenerator(Random(42)).generateAttempt(Topic.MULTIPLICATION, Difficulty.MEDIUM)
        assertEquals(one, two)
    }

    @Test fun avoidsPreviousAttemptWhenPoolAllows() {
        val gen = QuestionGenerator(Random(7))
        for (d in Difficulty.entries) for (topic in Topic.entries) {
            val first = gen.generateAttempt(topic, d)
            val pool = QuestionGenerator.candidatePool(topic, d).size
            val second = gen.generateAttempt(topic, d, avoidKeys = first.map { it.key }.toSet())
            val overlap = second.count { q -> first.any { it.key == q.key } }
            val allowedOverlap = maxOf(0, QUESTIONS_PER_LEVEL - (pool - QUESTIONS_PER_LEVEL))
            assertTrue("$topic $d overlap $overlap pool $pool", overlap <= allowedOverlap)
        }
    }

    @Test fun smallestPoolStillYieldsTenDistinct() {
        // Easy multiplication has only 15 unique pairs (1..5 x 1..5, order ignored).
        assertEquals(15, QuestionGenerator.candidatePool(Topic.MULTIPLICATION, Difficulty.EASY).size)
        val gen = QuestionGenerator(Random(1))
        val all = QuestionGenerator.candidatePool(Topic.MULTIPLICATION, Difficulty.EASY).map { it.key }.toSet()
        val attempt = gen.generateAttempt(Topic.MULTIPLICATION, Difficulty.EASY, avoidKeys = all)
        assertEquals(10, attempt.map { it.key }.toSet().size)
    }

    @Test fun variedAnswersAcrossRange() {
        val gen = QuestionGenerator(Random(3))
        val attempt = gen.generateAttempt(Topic.ADDITION, Difficulty.HARD)
        val answers = attempt.map { it.answer }
        assertTrue("spread ${answers}", answers.max() - answers.min() > 60)
    }

    @Test fun optionsFallbackWhenDistractorsCollapse() {
        val gen = OptionGenerator(Random(0))
        for (seed in 0 until 200) {
            val g = OptionGenerator(Random(seed))
            val opts = g.options(Expression(1, ArithmeticOp.MULTIPLY, 1))
            assertEquals(4, opts.toSet().size)
            assertTrue(opts.all { it >= 1 })
            assertTrue(1 in opts)
            val sub = g.options(Expression(1, ArithmeticOp.SUBTRACT, 1))
            assertEquals(4, sub.toSet().size)
            assertTrue(sub.all { it >= 0 } && 0 in sub)
        }
        // Correct answer position is shuffled.
        val positions = (0 until 100).map { s ->
            OptionGenerator(Random(s)).options(Expression(6, ArithmeticOp.MULTIPLY, 4)).indexOf(24)
        }.toSet()
        assertEquals(4, positions.size)
        assertTrue(gen.plausibleDistractors(Expression(6, ArithmeticOp.MULTIPLY, 4)).containsAll(listOf(30, 18, 28, 20, 10)))
    }
}
