package com.calcquest.kids.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Decimals are stored as canonical plain strings (no exponent, no trailing zeros). */
@Entity(tableName = "calculations", indices = [Index(value = ["createdAt"])])
data class CalculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstOperand: String,
    val operator: String,
    val secondOperand: String,
    val result: String,
    val rounded: Boolean,
    val createdAt: Long,
)

/** One row per completed topic/difficulty. Independent of attempt retention. */
@Entity(tableName = "level_progress", primaryKeys = ["topic", "difficulty"])
data class LevelProgressEntity(
    val topic: String,
    val difficulty: String,
    val completedAt: Long?,
)

object AttemptStatus {
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
}

@Entity(
    tableName = "quest_attempts",
    indices = [Index(value = ["topic", "difficulty", "status"])],
)
data class QuestAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val difficulty: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: String,
)

@Entity(
    tableName = "quest_questions",
    foreignKeys = [
        ForeignKey(
            entity = QuestAttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["attemptId", "position"], unique = true)],
)
data class QuestQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attemptId: Long,
    /** 0-based position inside the attempt. */
    val position: Int,
    val firstOperand: Int,
    /** Operator symbol (+, −, ×, ÷). */
    val operator: String,
    val secondOperand: Int,
    val correctAnswer: Int,
    /** Four comma-separated integers in display order. */
    val answerOptions: String,
    /** Comma-separated incorrect values already tried. */
    val attemptedOptionValues: String,
    val firstSelectedAnswer: Int?,
    val solvedAt: Long?,
    val hintUsed: Boolean,
    /** Challenge Timer duration fixed when the question was first shown; 0 = timer off. */
    val timerDurationMillis: Long?,
    /** Snapshot taken whenever the timer pauses; null = not started yet. */
    val timerRemainingMillis: Long?,
    val timerExpired: Boolean,
)

data class ActiveAttemptRow(
    val id: Long,
    val topic: String,
    val difficulty: String,
    val solved: Int,
)

data class FinishedAttemptRow(
    val id: Long,
    val topic: String,
    val difficulty: String,
    val completedAt: Long?,
)
