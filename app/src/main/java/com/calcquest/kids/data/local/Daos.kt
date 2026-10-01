package com.calcquest.kids.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculationDao {
    @Insert
    suspend fun insert(entity: CalculationEntity): Long

    @Query("SELECT * FROM calculations ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<CalculationEntity>>

    @Query("SELECT * FROM calculations ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latest(): CalculationEntity?

    @Query(
        "DELETE FROM calculations WHERE id NOT IN " +
            "(SELECT id FROM calculations ORDER BY createdAt DESC, id DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM calculations")
    suspend fun clear()
}

@Dao
interface QuestDao {
    // ----- attempts -----
    @Insert
    suspend fun insertAttempt(attempt: QuestAttemptEntity): Long

    @Update
    suspend fun updateAttempt(attempt: QuestAttemptEntity)

    @Query("SELECT * FROM quest_attempts WHERE id = :id")
    suspend fun attempt(id: Long): QuestAttemptEntity?

    @Query("SELECT * FROM quest_attempts WHERE id = :id")
    fun observeAttempt(id: Long): Flow<QuestAttemptEntity?>

    @Query(
        "SELECT * FROM quest_attempts WHERE topic = :topic AND difficulty = :difficulty " +
            "AND status = 'ACTIVE' ORDER BY id DESC"
    )
    suspend fun activeAttempts(topic: String, difficulty: String): List<QuestAttemptEntity>

    @Query(
        "SELECT id FROM quest_attempts WHERE topic = :topic AND difficulty = :difficulty " +
            "ORDER BY startedAt DESC, id DESC LIMIT 1"
    )
    suspend fun latestAttemptId(topic: String, difficulty: String): Long?

    @Query(
        "SELECT a.id AS id, a.topic AS topic, a.difficulty AS difficulty, " +
            "(SELECT COUNT(*) FROM quest_questions q WHERE q.attemptId = a.id AND q.solvedAt IS NOT NULL) AS solved " +
            "FROM quest_attempts a WHERE a.status = 'ACTIVE'"
    )
    fun observeActiveAttempts(): Flow<List<ActiveAttemptRow>>

    @Query(
        "SELECT id, topic, difficulty, completedAt FROM quest_attempts " +
            "WHERE status = 'COMPLETED' AND topic = :topic AND difficulty = :difficulty"
    )
    suspend fun finishedAttempts(topic: String, difficulty: String): List<FinishedAttemptRow>

    @Query("DELETE FROM quest_attempts WHERE id IN (:ids)")
    suspend fun deleteAttempts(ids: List<Long>)

    @Query("DELETE FROM quest_questions WHERE attemptId IN (:ids)")
    suspend fun deleteQuestionsOf(ids: List<Long>)

    // ----- questions -----
    @Insert
    suspend fun insertQuestions(questions: List<QuestQuestionEntity>)

    @Update
    suspend fun updateQuestion(question: QuestQuestionEntity)

    @Query("SELECT * FROM quest_questions WHERE id = :id")
    suspend fun question(id: Long): QuestQuestionEntity?

    @Query("SELECT * FROM quest_questions WHERE attemptId = :attemptId ORDER BY position")
    suspend fun questions(attemptId: Long): List<QuestQuestionEntity>

    @Query("SELECT * FROM quest_questions WHERE attemptId = :attemptId ORDER BY position")
    fun observeQuestions(attemptId: Long): Flow<List<QuestQuestionEntity>>

    @Query("UPDATE quest_questions SET hintUsed = 1 WHERE id = :id")
    suspend fun markHintUsed(id: Long)

    @Query("UPDATE quest_questions SET timerDurationMillis = :duration WHERE id = :id AND timerDurationMillis IS NULL")
    suspend fun assignTimerDuration(id: Long, duration: Long)

    @Query("UPDATE quest_questions SET timerRemainingMillis = :remaining, timerExpired = :expired WHERE id = :id")
    suspend fun saveTimer(id: Long, remaining: Long, expired: Boolean)

    // ----- progress -----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgress(progress: LevelProgressEntity)

    @Query("SELECT * FROM level_progress")
    fun observeProgress(): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM level_progress")
    suspend fun allProgress(): List<LevelProgressEntity>

    // ----- resets -----
    @Query("DELETE FROM level_progress WHERE difficulty = :difficulty")
    suspend fun deleteProgressFor(difficulty: String)

    @Query("DELETE FROM quest_questions WHERE attemptId IN (SELECT id FROM quest_attempts WHERE difficulty = :difficulty)")
    suspend fun deleteQuestionsForDifficulty(difficulty: String)

    @Query("DELETE FROM quest_attempts WHERE difficulty = :difficulty")
    suspend fun deleteAttemptsForDifficulty(difficulty: String)

    @Query("DELETE FROM level_progress")
    suspend fun deleteAllProgress()

    @Query("DELETE FROM quest_questions")
    suspend fun deleteAllQuestions()

    @Query("DELETE FROM quest_attempts")
    suspend fun deleteAllAttempts()
}
