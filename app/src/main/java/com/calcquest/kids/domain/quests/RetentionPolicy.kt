package com.calcquest.kids.domain.quests

data class FinishedAttemptRef(val id: Long, val topic: Topic, val difficulty: Difficulty, val completedAt: Long)

/**
 * Keeps the latest [keepPerLevel] finished attempts for every topic/difficulty.
 * Completion records live in a separate table and are never touched here, so pruning
 * history can never re-lock a level.
 */
object RetentionPolicy {
    const val KEEP_PER_LEVEL = 20

    fun attemptsToDelete(
        finished: List<FinishedAttemptRef>,
        keepPerLevel: Int = KEEP_PER_LEVEL,
    ): List<Long> = finished
        .groupBy { it.topic to it.difficulty }
        .values
        .flatMap { group ->
            group.sortedWith(compareByDescending<FinishedAttemptRef> { it.completedAt }.thenByDescending { it.id })
                .drop(keepPerLevel)
                .map { it.id }
        }
}
