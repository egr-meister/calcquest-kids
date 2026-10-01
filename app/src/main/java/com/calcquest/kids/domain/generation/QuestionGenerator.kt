package com.calcquest.kids.domain.generation

import com.calcquest.kids.domain.quests.ArithmeticOp
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.QUESTIONS_PER_LEVEL
import com.calcquest.kids.domain.quests.Question
import com.calcquest.kids.domain.quests.Topic
import com.calcquest.kids.domain.quests.questionKey
import kotlin.random.Random

/** One valid (a, op, b) pair before answer choices are attached. */
data class Expression(val a: Int, val op: ArithmeticOp, val b: Int) {
    val answer: Int get() = op.apply(a, b)
    val key: String get() = questionKey(a, op, b)
}

/**
 * Offline question generator.
 *
 * Every attempt is drawn from a finite pool of valid expressions, so there are no
 * unbounded retry loops: the pool is shuffled once and walked in order.
 * The random source is injected so tests are reproducible.
 */
class QuestionGenerator(
    private val random: Random,
    private val optionGenerator: OptionGenerator = OptionGenerator(random),
) {

    /**
     * Generates [QUESTIONS_PER_LEVEL] distinct questions.
     * Keys in [avoidKeys] (typically the previous attempt) are used only when the pool
     * does not contain enough fresh expressions.
     */
    fun generateAttempt(
        topic: Topic,
        difficulty: Difficulty,
        avoidKeys: Set<String> = emptySet(),
        count: Int = QUESTIONS_PER_LEVEL,
    ): List<Question> {
        val pool = candidatePool(topic, difficulty).shuffled(random)
        val fresh = pool.filter { it.key !in avoidKeys }
        val repeats = pool.filter { it.key in avoidKeys }
        val chosen = (spreadAcrossRange(fresh, count) + repeats).take(count)
        check(chosen.size == count) { "Pool for $topic/$difficulty is too small: ${pool.size}" }
        return chosen.map { pooled ->
            // Pools store commutative pairs once; show either operand order.
            val commutative = pooled.op == ArithmeticOp.ADD || pooled.op == ArithmeticOp.MULTIPLY
            val expr = if (commutative && pooled.a != pooled.b && random.nextBoolean()) {
                Expression(pooled.b, pooled.op, pooled.a)
            } else {
                pooled
            }
            Question(
                a = expr.a,
                op = expr.op,
                b = expr.b,
                answer = expr.answer,
                options = optionGenerator.options(expr),
            )
        }
    }

    /**
     * Picks [count] expressions so that answers cover low, middle and high parts of the
     * range instead of clustering. Works on an already shuffled list; bounded by its size.
     */
    private fun spreadAcrossRange(shuffled: List<Expression>, count: Int): List<Expression> {
        if (shuffled.size <= count) return shuffled
        val sorted = shuffled.sortedBy { it.answer }
        val bucketCount = count
        val buckets = List(bucketCount) { i ->
            val from = i * sorted.size / bucketCount
            val to = (i + 1) * sorted.size / bucketCount
            sorted.subList(from, to)
        }
        val picked = LinkedHashMap<String, Expression>()
        for (bucket in buckets) {
            if (bucket.isEmpty()) continue
            val candidate = bucket[random.nextInt(bucket.size)]
            picked.putIfAbsent(candidate.key, candidate)
        }
        // Top up from the shuffled order if any bucket was empty or collided.
        for (expr in shuffled) {
            if (picked.size >= count) break
            picked.putIfAbsent(expr.key, expr)
        }
        return picked.values.toList().shuffled(random)
    }

    companion object {
        /** All valid, de-duplicated expressions for a topic and difficulty. */
        fun candidatePool(topic: Topic, difficulty: Difficulty): List<Expression> {
            val result = LinkedHashMap<String, Expression>()
            fun add(e: Expression) {
                result.putIfAbsent(e.key, e)
            }
            when (topic) {
                Topic.ADDITION -> {
                    val r = difficulty.addSubRange
                    for (a in r) for (b in r) add(Expression(a, ArithmeticOp.ADD, b))
                }
                Topic.SUBTRACTION -> {
                    val r = difficulty.addSubRange
                    for (a in r) for (b in r) if (a >= b) add(Expression(a, ArithmeticOp.SUBTRACT, b))
                }
                Topic.MULTIPLICATION -> {
                    val r = difficulty.factorRange
                    for (a in r) for (b in r) add(Expression(a, ArithmeticOp.MULTIPLY, b))
                }
                Topic.DIVISION -> {
                    for (divisor in difficulty.divisorRange) {
                        if (divisor == 0) continue
                        for (dividend in difficulty.dividendRange) {
                            if (dividend % divisor == 0 && dividend / divisor > 0) {
                                add(Expression(dividend, ArithmeticOp.DIVIDE, divisor))
                            }
                        }
                    }
                }
            }
            return result.values.toList()
        }
    }
}
