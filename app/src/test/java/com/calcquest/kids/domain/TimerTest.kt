package com.calcquest.kids.domain

import com.calcquest.kids.domain.timer.ElapsedClock
import com.calcquest.kids.domain.timer.QuestionTimer
import com.calcquest.kids.domain.timer.TimerDurations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerTest {

    private class FakeClock(var now: Long = 1_000) : ElapsedClock {
        override fun elapsedMillis() = now
    }

    @Test fun countsFromMonotonicClockAndPauses() {
        val clock = FakeClock()
        val t = QuestionTimer(clock, 30_000)
        assertEquals(30_000, t.remainingMillis())
        t.resume()
        clock.now += 10_000
        assertEquals(20_000, t.remainingMillis())
        assertEquals(20_000, t.pause())
        clock.now += 60_000 // backgrounded / hint open: no time passes
        assertEquals(20_000, t.remainingMillis())
        t.resume()
        clock.now += 5_000
        assertEquals(15_000, t.remainingMillis())
    }

    @Test fun expiryIsReportedOnceAndNeverNegative() {
        val clock = FakeClock()
        val t = QuestionTimer(clock, 30_000)
        t.resume()
        clock.now += 29_999
        assertFalse(t.checkExpired())
        clock.now += 50_000
        assertTrue(t.checkExpired())
        assertFalse(t.checkExpired())
        assertTrue(t.isExpired)
        assertEquals(0, t.remainingMillis())
        t.resume() // no effect after expiry
        assertFalse(t.isRunning)
    }

    @Test fun recoveryFromPersistedSnapshotStartsPaused() {
        val clock = FakeClock(5)
        val restored = QuestionTimer(clock, 60_000, initialRemainingMillis = 42_000)
        assertFalse(restored.isRunning)
        clock.now += 100_000 // device restart / process death: elapsedRealtime may change arbitrarily
        assertEquals(42_000, restored.remainingMillis())
        val expired = QuestionTimer(clock, 60_000, initialRemainingMillis = 0, initiallyExpired = true)
        assertTrue(expired.isExpired)
    }

    @Test fun stopWhenSolved() {
        val clock = FakeClock()
        val t = QuestionTimer(clock, 120_000)
        t.resume()
        clock.now += 1_000
        assertEquals(119_000, t.stop())
        t.resume()
        clock.now += 10_000
        assertEquals(119_000, t.remainingMillis())
        assertTrue(t.isStopped)
    }

    @Test fun durationsSanitized() {
        assertEquals(listOf(30, 60, 120), TimerDurations.CHOICES)
        assertEquals(60, TimerDurations.sanitize(45))
        assertEquals(120, TimerDurations.sanitize(120))
    }
}
