package com.calcquest.kids.data.repository

import androidx.room.withTransaction
import com.calcquest.kids.data.local.ActiveAttemptRow
import com.calcquest.kids.data.local.AppDatabase
import com.calcquest.kids.data.local.AttemptStatus
import com.calcquest.kids.data.local.LevelProgressEntity
import com.calcquest.kids.data.local.QuestAttemptEntity
import com.calcquest.kids.data.local.QuestQuestionEntity
import com.calcquest.kids.domain.generation.QuestionGenerator
import com.calcquest.kids.domain.quests.AnswerOutcome
import com.calcquest.kids.domain.quests.AnswerRules
import com.calcquest.kids.domain.quests.ArithmeticOp
import com.calcquest.kids.domain.quests.CompletionRecord
import com.calcquest.kids.domain.quests.Difficulty
import com.calcquest.kids.domain.quests.FinishedAttemptRef
import com.calcquest.kids.domain.quests.IntListCodec
import com.calcquest.kids.domain.quests.QuestionAnswerState
import com.calcquest.kids.domain.quests.RetentionPolicy
import com.calcquest.kids.domain.quests.Topic
import com.calcquest.kids.domain.quests.questionKey
import com.calcquest.kids.domain.timer.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AnswerResponse(val outcome: AnswerOutcome, val levelCompleted: Boolean)

class QuestRepository(
    private val db: AppDatabase,
    private val generator: QuestionGenerator,
    private val clock: WallClock,
) {
    private val dao = db.questDao()

    val completionRecords: Flow<List<CompletionRecord>> = dao.observeProgress().map { rows ->
        rows.mapNotNull { it.toRecord() }
    }

    val activeAttempts: Flow<List<ActiveAttemptRow>> = dao.observeActiveAttempts()

    suspend fun completionRecordsNow(): List<CompletionRecord> = dao.allProgress().mapNotNull { it.toRecord() }

    fun observeAttempt(id: Long): Flow<QuestAttemptEntity?> = dao.observeAttempt(id)

    fun observeQuestions(attemptId: Long): Flow<List<QuestQuestionEntity>> = dao.observeQuestions(attemptId)

    suspend fun activeAttemptId(topic: Topic, difficulty: Difficulty): Long? =
        dao.activeAttempts(topic.name, difficulty.name).firstOrNull()?.id

    /**
     * Creates a fresh attempt and persists all 10 questions (with option order) before the
     * first one is displayed. Any unfinished attempt for the same level/difficulty is
     * cleared, which never touches completion records.
     */
    suspend fun startNewAttempt(topic: Topic, difficulty: Difficulty): Long = db.withTransaction {
        val existing = dao.activeAttempts(topic.name, difficulty.name).map { it.id }
        if (existing.isNotEmpty()) {
            dao.deleteQuestionsOf(existing)
            dao.deleteAttempts(existing)
        }
        val previousKeys = dao.latestAttemptId(topic.name, difficulty.name)
            ?.let { dao.questions(it) }
            ?.map { questionKey(it.firstOperand, ArithmeticOp.fromSymbol(it.operator), it.secondOperand) }
            ?.toSet()
            ?: emptySet()

        val questions = generator.generateAttempt(topic, difficulty, previousKeys)
        val attemptId = dao.insertAttempt(
            QuestAttemptEntity(
                topic = topic.name,
                difficulty = difficulty.name,
                startedAt = clock.nowMillis(),
                completedAt = null,
                status = AttemptStatus.ACTIVE,
            )
        )
        dao.insertQuestions(
            questions.mapIndexed { index, q ->
                QuestQuestionEntity(
                    attemptId = attemptId,
                    position = index,
                    firstOperand = q.a,
                    operator = q.op.symbol,
                    secondOperand = q.b,
                    correctAnswer = q.answer,
                    answerOptions = IntListCodec.encode(q.options),
                    attemptedOptionValues = "",
                    firstSelectedAnswer = null,
                    solvedAt = null,
                    hintUsed = false,
                    timerDurationMillis = null,
                    timerRemainingMillis = null,
                    timerExpired = false,
                )
            }
        )
        attemptId
    }

    /** Applies an answer atomically; completes the level when the 10th question is solved. */
    suspend fun answer(questionId: Long, selected: Int): AnswerResponse = db.withTransaction {
        val row = dao.question(questionId) ?: return@withTransaction AnswerResponse(AnswerOutcome.Ignored, false)
        val attempt = dao.attempt(row.attemptId) ?: return@withTransaction AnswerResponse(AnswerOutcome.Ignored, false)
        if (attempt.status != AttemptStatus.ACTIVE) return@withTransaction AnswerResponse(AnswerOutcome.Ignored, false)

        val now = clock.nowMillis()
        val result = AnswerRules.answer(row.toAnswerState(), selected, now)
        if (result.outcome == AnswerOutcome.Ignored) return@withTransaction AnswerResponse(AnswerOutcome.Ignored, false)

        dao.updateQuestion(
            row.copy(
                attemptedOptionValues = IntListCodec.encode(result.state.attemptedWrong),
                firstSelectedAnswer = result.state.firstSelectedAnswer,
                solvedAt = result.state.solvedAt,
            )
        )

        var completed = false
        if (result.outcome is AnswerOutcome.Correct) {
            val all = dao.questions(row.attemptId).map { it.toAnswerState() }
            if (AnswerRules.isLevelComplete(all)) {
                completed = true
                dao.updateAttempt(attempt.copy(status = AttemptStatus.COMPLETED, completedAt = now))
                // IGNORE keeps the original completion time on replays.
                dao.insertProgress(LevelProgressEntity(attempt.topic, attempt.difficulty, now))
                pruneFinished(attempt.topic, attempt.difficulty)
            }
        }
        AnswerResponse(result.outcome, completed)
    }

    private suspend fun pruneFinished(topic: String, difficulty: String) {
        val refs = dao.finishedAttempts(topic, difficulty).mapNotNull { row ->
            val t = Topic.fromName(row.topic) ?: return@mapNotNull null
            FinishedAttemptRef(row.id, t, Difficulty.fromName(row.difficulty), row.completedAt ?: 0L)
        }
        val toDelete = RetentionPolicy.attemptsToDelete(refs)
        if (toDelete.isNotEmpty()) {
            dao.deleteQuestionsOf(toDelete)
            dao.deleteAttempts(toDelete)
        }
    }

    suspend fun markHintUsed(questionId: Long) = dao.markHintUsed(questionId)

    /** Fixes the timer duration for a question the first time it is shown. */
    suspend fun assignTimerDuration(questionId: Long, durationMillis: Long) =
        dao.assignTimerDuration(questionId, durationMillis)

    suspend fun saveTimer(questionId: Long, remainingMillis: Long, expired: Boolean) =
        dao.saveTimer(questionId, remainingMillis, expired)

    suspend fun resetDifficulty(difficulty: Difficulty) = db.withTransaction {
        dao.deleteQuestionsForDifficulty(difficulty.name)
        dao.deleteAttemptsForDifficulty(difficulty.name)
        dao.deleteProgressFor(difficulty.name)
    }

    suspend fun resetAll() = db.withTransaction {
        dao.deleteAllQuestions()
        dao.deleteAllAttempts()
        dao.deleteAllProgress()
    }

    private fun LevelProgressEntity.toRecord(): CompletionRecord? {
        val t = Topic.fromName(topic) ?: return null
        return CompletionRecord(t, Difficulty.fromName(difficulty))
    }
}

fun QuestQuestionEntity.toAnswerState() = QuestionAnswerState(
    correctAnswer = correctAnswer,
    options = IntListCodec.decode(answerOptions),
    attemptedWrong = IntListCodec.decode(attemptedOptionValues),
    firstSelectedAnswer = firstSelectedAnswer,
    solvedAt = solvedAt,
)

val QuestQuestionEntity.op: ArithmeticOp get() = ArithmeticOp.fromSymbol(operator)
val QuestQuestionEntity.options: List<Int> get() = IntListCodec.decode(answerOptions)
val QuestQuestionEntity.attemptedWrong: List<Int> get() = IntListCodec.decode(attemptedOptionValues)
val QuestQuestionEntity.isSolved: Boolean get() = solvedAt != null
