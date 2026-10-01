package com.calcquest.kids.domain.generation

import com.calcquest.kids.domain.quests.ArithmeticOp

/**
 * Hints and explanations that are specific to the actual expression.
 * Hints never reveal the final answer; explanations (shown after a correct answer)
 * include the full equation and the same strategy worked through.
 */
object HintProvider {

    fun hint(a: Int, op: ArithmeticOp, b: Int): String = when (op) {
        ArithmeticOp.ADD -> additionHint(a, b)
        ArithmeticOp.SUBTRACT -> subtractionHint(a, b)
        ArithmeticOp.MULTIPLY -> multiplicationHint(a, b)
        ArithmeticOp.DIVIDE -> divisionHint(a, b)
    }

    fun explanation(a: Int, op: ArithmeticOp, b: Int): String {
        val result = op.apply(a, b)
        val equation = "$a ${op.symbol} $b = $result."
        val steps = when (op) {
            ArithmeticOp.ADD -> additionSteps(a, b)
            ArithmeticOp.SUBTRACT -> subtractionSteps(a, b)
            ArithmeticOp.MULTIPLY -> multiplicationSteps(a, b)
            ArithmeticOp.DIVIDE -> divisionSteps(a, b)
        }
        return if (steps.isEmpty()) equation else "$equation $steps"
    }

    // ---------------- Addition ----------------

    /** Bridging through the next ten: 8 + 7 -> 8 + 2 = 10, then + 5. */
    private data class Bridge(val start: Int, val added: Int, val toTen: Int, val rest: Int, val ten: Int)

    private fun additionBridge(a: Int, b: Int): Bridge? {
        // Start from the larger number and add the smaller one (which must be a single digit).
        val start = maxOf(a, b)
        val added = minOf(a, b)
        if (added !in 2..9) return null
        val ones = start % 10
        if (ones == 0) return null
        val toTen = 10 - ones
        if (added <= toTen) return null
        return Bridge(start, added, toTen, added - toTen, start + toTen)
    }

    private fun additionHint(a: Int, b: Int): String {
        additionBridge(a, b)?.let {
            return "Break ${it.added} into ${it.toTen} and ${it.rest}. " +
                "Add ${it.toTen} to reach ${it.ten}, then add ${it.rest}."
        }
        val big = maxOf(a, b)
        val small = minOf(a, b)
        if (small >= 10) {
            val tens = small / 10 * 10
            val ones = small % 10
            return if (ones == 0) {
                "Add $tens to $big by counting on in tens."
            } else {
                "Split $small into $tens and $ones. Add $tens to $big first, then add $ones."
            }
        }
        return "Start at the bigger number, $big, and count on $small more."
    }

    private fun additionSteps(a: Int, b: Int): String {
        additionBridge(a, b)?.let {
            return "${it.start} + ${it.toTen} = ${it.ten}, and ${it.ten} + ${it.rest} = ${it.ten + it.rest}."
        }
        val big = maxOf(a, b)
        val small = minOf(a, b)
        if (small >= 10) {
            val tens = small / 10 * 10
            val ones = small % 10
            return if (ones == 0) {
                "$big + $tens = ${big + tens}."
            } else {
                "$big + $tens = ${big + tens}, and ${big + tens} + $ones = ${big + small}."
            }
        }
        return "Counting on $small from $big gives ${big + small}."
    }

    // ---------------- Subtraction ----------------

    /** Bridging back through a ten: 15 − 7 -> 15 − 5 = 10, then − 2. */
    private data class BackBridge(val first: Int, val rest: Int, val ten: Int)

    private fun subtractionBridge(a: Int, b: Int): BackBridge? {
        if (b !in 2..9 || a <= 10) return null
        val ones = a % 10
        if (ones == 0 || ones >= b) return null
        return BackBridge(ones, b - ones, a - ones)
    }

    private fun subtractionHint(a: Int, b: Int): String {
        if (b == 0) return "Taking away 0 leaves $a just as it is."
        if (a == b) return "What is left when you take away everything you have?"
        subtractionBridge(a, b)?.let {
            return "Break $b into ${it.first} and ${it.rest}. " +
                "Take away ${it.first} to reach ${it.ten}, then take away ${it.rest}."
        }
        if (b >= 10) {
            val tens = b / 10 * 10
            val ones = b % 10
            return if (ones == 0) {
                "Count back from $a in tens: take away $tens."
            } else {
                "Split $b into $tens and $ones. Take away $tens first, then take away $ones."
            }
        }
        return "Think: what number plus $b makes $a? You can also count back $b from $a."
    }

    private fun subtractionSteps(a: Int, b: Int): String {
        if (b == 0 || a == b) return ""
        subtractionBridge(a, b)?.let {
            return "$a − ${it.first} = ${it.ten}, and ${it.ten} − ${it.rest} = ${it.ten - it.rest}."
        }
        if (b >= 10) {
            val tens = b / 10 * 10
            val ones = b % 10
            return if (ones == 0) {
                "Check: ${a - b} + $b = $a."
            } else {
                "$a − $tens = ${a - tens}, and ${a - tens} − $ones = ${a - b}."
            }
        }
        return "Check: ${a - b} + $b = $a."
    }

    // ---------------- Multiplication ----------------

    /** Chooses an even "number of groups" when possible so the groups split in half. */
    private fun groups(a: Int, b: Int): Pair<Int, Int> =
        if (a % 2 != 0 && b % 2 == 0) b to a else a to b

    private fun multiplicationHint(a: Int, b: Int): String {
        if (a == 1 || b == 1) {
            val other = if (a == 1) b else a
            return "1 group of $other is just $other."
        }
        val (g, size) = groups(a, b)
        return if (g % 2 == 0) {
            val half = g / 2
            "Think of $g groups of $size. You can split them into $half groups and $half groups."
        } else {
            "Think of $g groups of $size. You can split them into ${g - 1} groups and 1 more group."
        }
    }

    private fun multiplicationSteps(a: Int, b: Int): String {
        if (a == 1 || b == 1) return ""
        val (g, size) = groups(a, b)
        return if (g % 2 == 0) {
            val half = g / 2
            val part = half * size
            "$half groups of $size make $part, and $part + $part = ${part * 2}."
        } else {
            val part = (g - 1) * size
            "${g - 1} groups of $size make $part, and $part + $size = ${part + size}."
        }
    }

    // ---------------- Division ----------------

    private fun divisionHint(a: Int, b: Int): String = when {
        b == 1 -> "Sharing $a into 1 group keeps all $a together."
        a == b -> "How many groups of $b fit into $b?"
        else -> "Think: $b times which number makes $a?"
    }

    private fun divisionSteps(a: Int, b: Int): String = "Check: $b × ${a / b} = $a."

    /** Dot groups are only drawn for small products to avoid rendering hundreds of objects. */
    fun dotGroups(a: Int, op: ArithmeticOp, b: Int): Pair<Int, Int>? {
        if (op != ArithmeticOp.MULTIPLY) return null
        val (g, size) = groups(a, b)
        return if (g * size <= 30 && g <= 6) g to size else null
    }
}
