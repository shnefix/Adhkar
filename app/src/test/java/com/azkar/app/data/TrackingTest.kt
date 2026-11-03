package com.azkar.app.data

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class TrackingTest {
    private val folder = "f"

    /** Noon on 3 Nov 2026 plus [offset] days. */
    private fun day(offset: Int) = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.NOVEMBER, 3, 12, 0); add(Calendar.DAY_OF_YEAR, offset)
    }

    private fun finish(offset: Int) = Tracking.record(folder, 5, 5, day(offset).timeInMillis)

    @Before
    fun setUp() {
        Tracking.attach(MemoryPrefs())
        Tracking.setTracked(folder, true)
    }

    @Test
    fun streakCountsConsecutiveFinishedDays() {
        finish(-2); finish(-1); finish(0)
        assertEquals(3, Tracking.streak(listOf(folder), day(0)))
    }

    @Test
    fun unfinishedTodayDoesNotBreakTheStreak() {
        finish(-2); finish(-1)
        Tracking.record(folder, 2, 5, day(0).timeInMillis)
        assertEquals(2, Tracking.streak(listOf(folder), day(0)))
    }

    @Test
    fun aMissedDayEndsTheStreak() {
        finish(-3); finish(-1)
        assertEquals(1, Tracking.streak(listOf(folder), day(0)))
    }

    @Test
    fun aDayKeepsItsBestResult() {
        Tracking.record(folder, 5, 5, day(0).timeInMillis)
        Tracking.record(folder, 1, 5, day(0).timeInMillis)
        assertEquals(Tracking.Progress(5, 5), Tracking.history(folder)[Tracking.dayKey(day(0))])
    }

    @Test
    fun untrackedFoldersKeepNoHistory() {
        Tracking.setTracked(folder, false)
        finish(0)
        assertEquals(emptyMap<String, Tracking.Progress>(), Tracking.history(folder))
        assertEquals(Tracking.Progress(5, 5), Tracking.today(folder, day(0).timeInMillis))
    }
}
