package com.calcquest.kids.domain.quests

/** Persistable answer state of one question (mirrors the Room row, without Android types). */
data class QuestionAnswerState(
    val correctAnswer: Int,
    val options: List<Int>,
    val attemptedWrong: List<Int>,
    val firstSelectedAnswer: Int?,
    val solvedAt: Long?,
) {
    val isSolved: Boolean get() = solvedAt != null
    val solvedOnFirstTry: Boolean get() = isSolved && firstSelectedAnswer == correctAnswer
}

sealed interface AnswerOutcome {
    /** Correct answer; [firstTry] is tracked for review only and never blocks unlocking. */
    data class Correct(val firstTry: Boolean) : AnswerOutcome
    data object Incorrect : AnswerOutcome
    /** Repeated tap, already-solved question, already-tried option or unknown value. */
    data object Ignored : AnswerOutcome
}

data class AnswerResult(val state: QuestionAnswerState, val outcome: AnswerOutcome)

object AnswerRules {

    /**
     * Pure answer transition. The repository runs it inside a database transaction, so a
     * question can be solved only once and repeated taps never change counts.
     */
    fun answer(state: QuestionAnswerState, selected: Int, now: Long): AnswerResult {
        if (state.isSolved) return AnswerResult(state, AnswerOutcome.Ignored)
        if (selected !in state.options) return AnswerResult(state, AnswerOutcome.Ignored)
        if (selected in state.attemptedWrong) return AnswerResult(state, AnswerOutcome.Ignored)

        val first = state.firstSelectedAnswer ?: selected
        return if (selected == state.correctAnswer) {
            AnswerResult(
                state.copy(firstSelectedAnswer = first, solvedAt = now),
                AnswerOutcome.Correct(firstTry = first == state.correctAnswer),
            )
        } else {
            AnswerResult(
                state.copy(firstSelectedAnswer = first, attemptedWrong = state.attemptedWrong + selected),
                AnswerOutcome.Incorrect,
            )
        }
    }

    /** A level completes only when every one of its questions is solved. */
    fun isLevelComplete(questions: List<QuestionAnswerState>): Boolean =
        questions.size == QUESTIONS_PER_LEVEL && questions.all { it.isSolved }

    fun firstTryCount(questions: List<QuestionAnswerState>): Int =
        questions.count { it.solvedOnFirstTry }
}

/** Encodes small integer lists for storage as canonical comma-separated strings. */
object IntListCodec {
    fun encode(values: List<Int>): String = values.joinToString(",")
    fun decode(raw: String): List<Int> =
        if (raw.isBlank()) emptyList() else raw.split(',').mapNotNull { it.trim().toIntOrNull() }
}
