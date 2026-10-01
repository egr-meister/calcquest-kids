package com.calcquest.kids.domain

import com.calcquest.kids.domain.quests.AnswerOutcome
import com.calcquest.kids.domain.quests.AnswerRules
import com.calcquest.kids.domain.quests.CompletionRecord
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.FinishedAttemptRef
import com.calcquest.kids.domain.quests.IntListCodec
import com.calcquest.kids.domain.quests.LevelStatus
import com.calcquest.kids.domain.quests.QuestionAnswerState
import com.calcquest.kids.domain.quests.RetentionPolicy
import com.calcquest.kids.domain.quests.RouteCalculator
import com.calcquest.kids.domain.quests.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestRulesTest {

    private val fresh = QuestionAnswerState(15, listOf(14, 15, 16, 5), emptyList(), null, null)

    @Test fun incorrectThenRetryThenCorrect() {
        val wrong = AnswerRules.answer(fresh, 14, now = 1)
        assertEquals(AnswerOutcome.Incorrect, wrong.outcome)
        assertEquals(listOf(14), wrong.state.attemptedWrong)
        assertFalse(wrong.state.isSolved)
        // Same wrong option again is ignored (it is disabled in the UI).
        assertEquals(AnswerOutcome.Ignored, AnswerRules.answer(wrong.state, 14, 2).outcome)
        val right = AnswerRules.answer(wrong.state, 15, now = 3)
        assertEquals(AnswerOutcome.Correct(firstTry = false), right.outcome)
        assertEquals(3L, right.state.solvedAt)
        assertEquals(14, right.state.firstSelectedAnswer)
        assertFalse(right.state.solvedOnFirstTry)
    }

    @Test fun repeatedCorrectTapsSolveOnce() {
        val first = AnswerRules.answer(fresh, 15, now = 10)
        assertEquals(AnswerOutcome.Correct(firstTry = true), first.outcome)
        val again = AnswerRules.answer(first.state, 15, now = 11)
        assertEquals(AnswerOutcome.Ignored, again.outcome)
        assertEquals(10L, again.state.solvedAt)
        assertEquals(AnswerOutcome.Ignored, AnswerRules.answer(fresh, 99, 1).outcome)
    }

    @Test fun levelCompletesOnlyWhenAllTenSolvedRegardlessOfAccuracy() {
        val solvedLate = fresh.copy(firstSelectedAnswer = 14, attemptedWrong = listOf(14), solvedAt = 5)
        val nine = List(9) { solvedLate }
        assertFalse(AnswerRules.isLevelComplete(nine + fresh))
        assertTrue(AnswerRules.isLevelComplete(nine + solvedLate))
        assertEquals(0, AnswerRules.firstTryCount(nine + solvedLate))
    }

    @Test fun codecRoundTrip() {
        assertEquals(listOf(3, 0, 12), IntListCodec.decode(IntListCodec.encode(listOf(3, 0, 12))))
        assertEquals(emptyList<Int>(), IntListCodec.decode(""))
    }

    @Test fun firstEnabledLevelAvailableOthersLocked() {
        val r = RouteCalculator.compute(Topic.entries.toSet(), emptySet())
        assertEquals(LevelStatus.Available, r.levels[0].status)
        assertEquals(LevelStatus.Locked(Topic.ADDITION), r.levels[1].status)
        assertEquals(LevelStatus.Locked(Topic.ADDITION), r.levels[3].status)
        assertEquals(Topic.ADDITION, r.nextRequired)
        assertFalse(r.isRouteComplete)
    }

    @Test fun disabledTopicsDoNotBlockProgression() {
        val enabled = setOf(Topic.ADDITION, Topic.SUBTRACTION, Topic.DIVISION)
        val r = RouteCalculator.compute(enabled, setOf(Topic.ADDITION, Topic.SUBTRACTION))
        assertEquals(LevelStatus.DisabledByParent, r.levels[2].status)
        assertEquals(LevelStatus.Available, r.levels[3].status)
        assertEquals(Topic.DIVISION, r.nextRequired)
        // Only Division enabled: it is available immediately.
        val only = RouteCalculator.compute(setOf(Topic.DIVISION), emptySet())
        assertEquals(LevelStatus.Available, only.levels[3].status)
    }

    @Test fun inProgressAndRouteComplete() {
        val r = RouteCalculator.compute(Topic.entries.toSet(), setOf(Topic.ADDITION), mapOf(Topic.SUBTRACTION to 4))
        assertEquals(LevelStatus.InProgress(4), r.levels[1].status)
        assertEquals(4, r.levels[1].solvedCount)
        val done = RouteCalculator.compute(setOf(Topic.ADDITION, Topic.DIVISION), setOf(Topic.ADDITION, Topic.DIVISION))
        assertTrue(done.isRouteComplete)
        assertNull(done.nextRequired)
        assertEquals(LevelStatus.Completed, done.levels[0].status)
        assertTrue(done.levels[0].isPlayable) // replayable
    }

    @Test fun difficultySpecificProgress() {
        val records = listOf(
            CompletionRecord(Topic.ADDITION, Difficulty.EASY),
            CompletionRecord(Topic.SUBTRACTION, Difficulty.EASY),
        )
        val hard = RouteCalculator.compute(Topic.entries.toSet(), RouteCalculator.completedFor(records, Difficulty.HARD))
        assertEquals(LevelStatus.Available, hard.levels[0].status)
        assertEquals(LevelStatus.Locked(Topic.ADDITION), hard.levels[1].status)
        val easy = RouteCalculator.compute(Topic.entries.toSet(), RouteCalculator.completedFor(records, Difficulty.EASY))
        assertEquals(LevelStatus.Available, easy.levels[2].status)
    }

    @Test fun replayKeepsCompletion() {
        // Completed level with a fresh replay attempt in progress stays Completed.
        val r = RouteCalculator.compute(Topic.entries.toSet(), setOf(Topic.ADDITION), mapOf(Topic.ADDITION to 2))
        assertEquals(LevelStatus.Completed, r.levels[0].status)
        assertEquals(2, r.levels[0].solvedCount)
        assertTrue(r.levels[0].hasActiveAttempt)
        assertEquals(LevelStatus.Available, r.levels[1].status)
    }

    @Test fun warnsWhenEnablingIncompleteEarlierTopic() {
        val completed = setOf(Topic.ADDITION, Topic.SUBTRACTION)
        val old = setOf(Topic.ADDITION, Topic.SUBTRACTION, Topic.DIVISION)
        assertEquals(
            Topic.MULTIPLICATION,
            RouteCalculator.earlierRequirementIntroduced(old, Topic.entries.toSet(), completed),
        )
        assertNull(RouteCalculator.earlierRequirementIntroduced(old, old - Topic.DIVISION, completed))
        // Enabling a later topic after the current one does not warn.
        assertNull(
            RouteCalculator.earlierRequirementIntroduced(
                setOf(Topic.ADDITION), setOf(Topic.ADDITION, Topic.DIVISION), emptySet(),
            )
        )
        // Re-enabling a topic that is already complete never warns.
        assertNull(RouteCalculator.earlierRequirementIntroduced(old, Topic.entries.toSet(), completed + Topic.MULTIPLICATION))
    }

    @Test fun retentionKeepsLatestTwentyAndNeverTouchesCompletion() {
        val finished = (1L..25L).map { FinishedAttemptRef(it, Topic.ADDITION, Difficulty.EASY, completedAt = it * 100) } +
            (100L..104L).map { FinishedAttemptRef(it, Topic.ADDITION, Difficulty.HARD, completedAt = it) }
        val delete = RetentionPolicy.attemptsToDelete(finished)
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), delete)
        // Completion records are separate: route is unchanged after pruning.
        val records = listOf(CompletionRecord(Topic.ADDITION, Difficulty.EASY))
        val route = RouteCalculator.compute(Topic.entries.toSet(), RouteCalculator.completedFor(records, Difficulty.EASY))
        assertEquals(LevelStatus.Completed, route.levels[0].status)
    }
}
