package com.calcquest.kids.domain.timer

/** Monotonic clock (SystemClock.elapsedRealtime in the app, a fake in tests). */
fun interface ElapsedClock {
    fun elapsedMillis(): Long
}

/** Wall clock for timestamps stored in the database. */
fun interface WallClock {
    fun nowMillis(): Long
}

/** Allowed Challenge Timer durations, in seconds. */
object TimerDurations {
    val CHOICES = listOf(30, 60, 120)
    const val DEFAULT_SECONDS = 60
    fun sanitize(seconds: Int): Int = if (seconds in CHOICES) seconds else DEFAULT_SECONDS
}

/**
 * Soft per-question countdown.
 *
 * While running, remaining time is derived from the monotonic clock
 * (`remainingAtStart - (now - startedAt)`); a stored counter is never decremented.
 * The persisted value is only the snapshot taken when the timer pauses.
 */
class QuestionTimer(
    private val clock: ElapsedClock,
    val durationMillis: Long,
    initialRemainingMillis: Long = durationMillis,
    initiallyExpired: Boolean = false,
) {
    private var remainingAtStart: Long = initialRemainingMillis.coerceIn(0, durationMillis)
    private var startedAt: Long? = null

    var isExpired: Boolean = initiallyExpired || remainingAtStart == 0L
        private set

    var isStopped: Boolean = false
        private set

    val isRunning: Boolean get() = startedAt != null

    fun remainingMillis(): Long {
        val start = startedAt ?: return remainingAtStart
        val elapsed = (clock.elapsedMillis() - start).coerceAtLeast(0)
        return (remainingAtStart - elapsed).coerceAtLeast(0)
    }

    /** Starts or resumes counting. No effect once expired or stopped. */
    fun resume() {
        if (isExpired || isStopped || startedAt != null) return
        startedAt = clock.elapsedMillis()
    }

    /** Pauses and returns the snapshot that should be persisted. */
    fun pause(): Long {
        val remaining = remainingMillis()
        startedAt = null
        remainingAtStart = remaining
        if (remaining == 0L) isExpired = true
        return remaining
    }

    /**
     * Checks for expiry while running. Returns true exactly once, on the transition to expired.
     */
    fun checkExpired(): Boolean {
        if (isExpired || startedAt == null) return false
        if (remainingMillis() == 0L) {
            pause()
            isExpired = true
            return true
        }
        return false
    }

    /** Stops for good (question solved). */
    fun stop(): Long {
        val remaining = pause()
        isStopped = true
        return remaining
    }
}
