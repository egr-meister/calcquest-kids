package com.calcquest.kids.domain.quests

sealed interface LevelStatus {
    data object Available : LevelStatus
    data class InProgress(val solved: Int) : LevelStatus
    data object Completed : LevelStatus
    /** Must finish [requiredTopic] (an earlier enabled level) first. */
    data class Locked(val requiredTopic: Topic) : LevelStatus
    data object DisabledByParent : LevelStatus
}

data class LevelNode(
    val topic: Topic,
    val status: LevelStatus,
    /** Solved questions in the active attempt, or 10 for a completed level without one. */
    val solvedCount: Int,
    val hasActiveAttempt: Boolean,
) {
    val isPlayable: Boolean
        get() = status is LevelStatus.Available || status is LevelStatus.InProgress || status is LevelStatus.Completed
}

data class RouteState(
    val levels: List<LevelNode>,
    /** The first enabled level that is not complete, or null when the route is finished. */
    val nextRequired: Topic?,
    val isRouteComplete: Boolean,
)

/** Persisted completion record for one topic at one difficulty. */
data class CompletionRecord(val topic: Topic, val difficulty: Difficulty)

object RouteCalculator {

    fun completedFor(records: Collection<CompletionRecord>, difficulty: Difficulty): Set<Topic> =
        records.filter { it.difficulty == difficulty }.map { it.topic }.toSet()

    fun nextRequired(enabled: Set<Topic>, completed: Set<Topic>): Topic? =
        Topic.routeOrder.firstOrNull { it in enabled && it !in completed }

    /**
     * Sequential unlocking over enabled topics only. Disabled topics never block the route,
     * and completed levels always stay replayable.
     */
    fun compute(
        enabled: Set<Topic>,
        completed: Set<Topic>,
        activeSolved: Map<Topic, Int> = emptyMap(),
    ): RouteState {
        val next = nextRequired(enabled, completed)
        var firstIncompleteBefore: Topic? = null
        val levels = Topic.routeOrder.map { topic ->
            val active = activeSolved[topic]
            val blocker = firstIncompleteBefore
            val status: LevelStatus = when {
                topic !in enabled -> LevelStatus.DisabledByParent
                topic in completed -> LevelStatus.Completed
                blocker != null -> LevelStatus.Locked(blocker)
                active != null -> LevelStatus.InProgress(active)
                else -> LevelStatus.Available
            }
            if (topic in enabled && topic !in completed && firstIncompleteBefore == null) {
                firstIncompleteBefore = topic
            }
            LevelNode(
                topic = topic,
                status = status,
                solvedCount = active ?: if (topic in completed) QUESTIONS_PER_LEVEL else 0,
                hasActiveAttempt = active != null,
            )
        }
        return RouteState(levels, next, isRouteComplete = enabled.isNotEmpty() && next == null)
    }

    /**
     * Returns the topic that would newly become the required level *before* the current one
     * if [newEnabled] were saved, so parents can be told before they confirm.
     */
    fun earlierRequirementIntroduced(
        oldEnabled: Set<Topic>,
        newEnabled: Set<Topic>,
        completed: Set<Topic>,
    ): Topic? {
        val oldNext = nextRequired(oldEnabled, completed)
        val newNext = nextRequired(newEnabled, completed) ?: return null
        if (newNext == oldNext) return null
        return if (oldNext == null || newNext.level < oldNext.level) newNext else null
    }

    /** Next playable level after finishing [current], used by "Continue to next level". */
    fun nextAfter(current: Topic, enabled: Set<Topic>, completed: Set<Topic>): Topic? {
        val next = nextRequired(enabled, completed) ?: return null
        return if (next != current) next else null
    }
}
