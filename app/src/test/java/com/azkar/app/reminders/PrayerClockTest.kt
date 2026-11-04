package com.azkar.app.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PrayerClockTest {
    private val cairo = 30.0444 to 31.2357
    private val zone = TimeZone.getTimeZone("Africa/Cairo")

    /** 3 Nov 2026 at [h]:[m] Cairo time. */
    private fun at(h: Int, m: Int = 0, day: Int = 3) = Calendar.getInstance(zone).apply {
        clear(); set(2026, Calendar.NOVEMBER, day, h, m)
    }.timeInMillis

    @Test
    fun rulesRoundTripAsText() {
        val r = TimeRule(Moment.FAJR, -30)
        assertEquals(r, TimeRule.decode(r.encode()))
        assertNull(TimeRule.decode("NEVER"))
        assertNull(TimeRule.decode(null))
    }

    @Test
    fun offsetsMoveTheMomentBeforeOrAfterThePrayer() {
        val fajr = PrayerClock.momentOn(Moment.FAJR, at(12), cairo, zone)
        assertEquals(fajr - 30 * 60_000L, PrayerClock.on(TimeRule(Moment.FAJR, -30), at(12), cairo, zone))
        assertEquals(fajr + 20 * 60_000L, PrayerClock.on(TimeRule(Moment.FAJR, 20), at(12), cairo, zone))
    }

    @Test
    fun nextIsTodayIfStillAheadOtherwiseTomorrow() {
        val rule = TimeRule(Moment.DHUHR, 10)
        assertEquals(PrayerClock.on(rule, at(12), cairo, zone), PrayerClock.next(rule, at(8), cairo, zone))
        assertEquals(PrayerClock.on(rule, at(12, day = 4), cairo, zone), PrayerClock.next(rule, at(15), cairo, zone))
    }

    @Test
    fun midnightIsTheStartOfTheNextDay() {
        assertEquals(at(0, day = 4), PrayerClock.next(TimeRule(Moment.MIDNIGHT), at(23), null, zone))
    }

    @Test
    fun passedBetweenSeesTheMomentOnlyInsideTheWindow() {
        val rule = TimeRule(Moment.DHUHR)
        val dhuhr = PrayerClock.on(rule, at(12), cairo, zone)
        assertFalse(PrayerClock.passedBetween(rule, at(7), dhuhr - 60_000, cairo, zone))
        assertTrue(PrayerClock.passedBetween(rule, at(7), dhuhr, cairo, zone))
        assertTrue(PrayerClock.passedBetween(rule, at(7), at(7, day = 6), cairo, zone))
    }

    @Test
    fun withoutLocationTypicalTimesAreUsed() {
        assertEquals(at(12), PrayerClock.momentOn(Moment.DHUHR, at(8), null, zone))
        assertEquals(at(18), PrayerClock.momentOn(Moment.MAGHRIB, at(8), null, zone))
    }
}
