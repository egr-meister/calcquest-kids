package com.calcquest.kids.domain.quests

/** Arithmetic operation used by quest questions. Persisted by [symbol]. */
enum class ArithmeticOp(val symbol: String, val spoken: String) {
    ADD("+", "plus"),
    SUBTRACT("−", "minus"),
    MULTIPLY("×", "times"),
    DIVIDE("÷", "divided by");

    fun apply(a: Int, b: Int): Int = when (this) {
        ADD -> a + b
        SUBTRACT -> a - b
        MULTIPLY -> a * b
        DIVIDE -> a / b
    }

    companion object {
        fun fromSymbol(symbol: String): ArithmeticOp =
            entries.firstOrNull { it.symbol == symbol }
                ?: throw IllegalArgumentException("Unknown operator: $symbol")
    }
}

/** The four landmarks, in route order. Persisted by [name]. */
enum class Topic(val level: Int, val title: String, val op: ArithmeticOp) {
    ADDITION(1, "Addition Trail", ArithmeticOp.ADD),
    SUBTRACTION(2, "Subtraction Cave", ArithmeticOp.SUBTRACT),
    MULTIPLICATION(3, "Multiplication Tower", ArithmeticOp.MULTIPLY),
    DIVISION(4, "Division Gate", ArithmeticOp.DIVIDE);

    companion object {
        val routeOrder: List<Topic> = entries.sortedBy { it.level }
        fun fromName(name: String): Topic? = entries.firstOrNull { it.name == name }
    }
}

/** Parent-selected difficulty. Persisted by [name]. */
enum class Difficulty(
    val label: String,
    val addSubRange: IntRange,
    val factorRange: IntRange,
    val dividendRange: IntRange,
    val divisorRange: IntRange,
) {
    EASY("Easy", 1..10, 1..5, 1..20, 1..10),
    MEDIUM("Medium", 1..50, 1..10, 1..100, 1..10),
    HARD("Hard", 1..100, 1..12, 1..144, 1..12);

    companion object {
        val DEFAULT = EASY
        fun fromName(name: String?): Difficulty = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** A generated arithmetic question with its four answer choices. */
data class Question(
    val a: Int,
    val op: ArithmeticOp,
    val b: Int,
    val answer: Int,
    val options: List<Int>,
) {
    val expression: String get() = "$a ${op.symbol} $b"
    val spokenExpression: String get() = "$a ${op.spoken} $b"

    /** Duplicate key: reversed operands of commutative operations count as the same expression. */
    val key: String get() = questionKey(a, op, b)
}

fun questionKey(a: Int, op: ArithmeticOp, b: Int): String = when (op) {
    ArithmeticOp.ADD, ArithmeticOp.MULTIPLY -> "${minOf(a, b)}${op.symbol}${maxOf(a, b)}"
    else -> "$a${op.symbol}$b"
}

const val QUESTIONS_PER_LEVEL = 10
const val OPTIONS_PER_QUESTION = 4
