package com.azkar.app.reminders

import com.azkar.app.reminders.PrayerSessions.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PrayerSessionsTest {
    private val cairo = 30.0444 to 31.2357
    private val zone = TimeZone.getTimeZone("Africa/Cairo")

    /** 3 Nov 2026 at [h]:[m] Cairo time. */
    private fun at(h: Int, m: Int = 0, day: Int = 3) = Calendar.getInstance(zone).apply {
        clear(); set(2026, Calendar.NOVEMBER, day, h, m)
    }.timeInMillis

    private val today = PrayerSessions.timesOn(at(12), cairo.first, cairo.second, zone)
    private val tomorrow = PrayerSessions.timesOn(at(12, day = 4), cairo.first, cairo.second, zone)

    @Test
    fun prayerTimesAreInDayOrder() {
        assertTrue(today.fajr.before(today.dhuhr))
        assertTrue(today.dhuhr.before(today.asr))
        assertTrue(today.asr.before(today.maghrib))
    }

    @Test
    fun morningReminderIsTenMinutesAfterFajr() {
        assertEquals(PrayerSessions.minute(today.fajr) + PrayerSessions.DELAY_MS, PrayerSessions.nextReminder(Session.MORNING, at(1), cairo, zone))
        // After today's has passed, the next one is tomorrow's.
        assertEquals(PrayerSessions.minute(tomorrow.fajr) + PrayerSessions.DELAY_MS, PrayerSessions.nextReminder(Session.MORNING, at(12), cairo, zone))
    }

    @Test
    fun eveningReminderIsTenMinutesAfterAsr() {
        assertEquals(PrayerSessions.minute(today.asr) + PrayerSessions.DELAY_MS, PrayerSessions.nextReminder(Session.EVENING, at(12), cairo, zone))
    }

    @Test
    fun chosenDelayMovesTheStart() {
        val delay = 45 * 60_000L
        assertEquals(PrayerSessions.minute(today.fajr) + delay, PrayerSessions.nextReminder(Session.MORNING, at(1), cairo, zone, delay))
        assertEquals(PrayerSessions.minute(today.asr), PrayerSessions.nextReminder(Session.EVENING, at(12), cairo, zone, 0L))
        assertEquals(
            (PrayerSessions.minute(today.asr) + delay) to PrayerSessions.minute(today.maghrib),
            PrayerSessions.window(Session.EVENING, at(12), cairo, zone, delay)
        )
    }

    @Test
    fun notFinishedReminderComesBeforeTheSessionEnds() {
        val lead = 45 * 60_000L
        assertEquals(PrayerSessions.minute(today.dhuhr) - lead, PrayerSessions.nextNudge(Session.MORNING, at(7), lead, cairo, zone, PrayerSessions.DELAY_MS))
        // After today's has passed, tomorrow's.
        assertEquals(PrayerSessions.minute(tomorrow.maghrib) - lead, PrayerSessions.nextNudge(Session.EVENING, at(20), lead, cairo, zone, PrayerSessions.DELAY_MS))
        // A lead longer than the session never lands before the session starts.
        assertEquals(PrayerSessions.minute(today.asr) + PrayerSessions.DELAY_MS,
            PrayerSessions.nextNudge(Session.EVENING, at(12), 10 * 60 * 60_000L, cairo, zone, PrayerSessions.DELAY_MS))
    }

    @Test
    fun beforeSleepFollowsIsha() {
        val delay = 120 * 60_000L
        assertEquals(PrayerSessions.minute(today.isha) + delay, PrayerSessions.nextAfterIsha(at(12), cairo, zone, delay))
        assertEquals(PrayerSessions.minute(tomorrow.isha) + delay, PrayerSessions.nextAfterIsha(at(23, 59), cairo, zone, delay))
        assertNull(PrayerSessions.nextAfterIsha(at(12), null, zone, delay))
    }

    @Test
    fun sessionsEndAtDhuhrAndMaghrib() {
        assertEquals(PrayerSessions.minute(today.dhuhr), PrayerSessions.endAfter(Session.MORNING, at(7), cairo, zone))
        assertEquals(PrayerSessions.minute(today.maghrib), PrayerSessions.endAfter(Session.EVENING, at(15, 30), cairo, zone))
        assertEquals(true, PrayerSessions.inSession(Session.MORNING, at(7), cairo, zone))
        assertEquals(false, PrayerSessions.inSession(Session.MORNING, at(13), cairo, zone))
        assertEquals(false, PrayerSessions.inSession(Session.EVENING, at(19), cairo, zone))
    }

    @Test
    fun morningPositionResetsOnceDhuhrPasses() {
        val saved = at(7)
        assertFalse(PrayerSessions.positionExpired("d_14", saved, PrayerSessions.minute(today.dhuhr) - 60_000, cairo, zone))
        assertTrue(PrayerSessions.positionExpired("d_14", saved, PrayerSessions.minute(today.dhuhr), cairo, zone))
    }

    @Test
    fun eveningPositionResetsOnceMaghribPasses() {
        val saved = at(15, 30)
        assertFalse(PrayerSessions.positionExpired("d_11", saved, PrayerSessions.minute(today.maghrib) - 60_000, cairo, zone))
        assertTrue(PrayerSessions.positionExpired("d_11", saved, PrayerSessions.minute(today.maghrib) + 60_000, cairo, zone))
    }

    @Test
    fun otherFoldersAndOldPositionsNeverExpire() {
        assertFalse(PrayerSessions.positionExpired("d_13", at(7), at(20, day = 4), cairo, zone))
        assertFalse(PrayerSessions.positionExpired("d_14", 0L, at(20, day = 4), cairo, zone))
    }

    @Test
    fun withoutLocationEverythingFallsBack() {
        assertNull(PrayerSessions.nextReminder(Session.MORNING, at(1), null, zone))
        assertNull(PrayerSessions.inSession(Session.MORNING, at(7), null, zone))
        // Without a location the morning azkar still start over, at a typical Dhuhr time (12:00).
        assertFalse(PrayerSessions.positionExpired("d_14", at(7), at(11), null, zone))
        assertTrue(PrayerSessions.positionExpired("d_14", at(7), at(12, 1), null, zone))
    }

    @Test
    fun hanafiAsrIsLaterThanTheStandardOne() {
        val standard = PrayerSessions.timesOn(at(12), cairo.first, cairo.second, zone, PrayerSessions.parameters("EGYPTIAN", false))
        val hanafi = PrayerSessions.timesOn(at(12), cairo.first, cairo.second, zone, PrayerSessions.parameters("EGYPTIAN", true))
        assertTrue(hanafi.asr.after(standard.asr))
        assertEquals(PrayerSessions.minute(standard.fajr), PrayerSessions.minute(hanafi.fajr))
    }

    @Test
    fun methodChangesFajrAndUnknownMethodFallsBackToEgyptian() {
        val egyptian = PrayerSessions.timesOn(at(12), cairo.first, cairo.second, zone, PrayerSessions.parameters("EGYPTIAN", false))
        val ummAlQura = PrayerSessions.timesOn(at(12), cairo.first, cairo.second, zone, PrayerSessions.parameters("UMM_AL_QURA", false))
        val unknown = PrayerSessions.timesOn(at(12), cairo.first, cairo.second, zone, PrayerSessions.parameters("NOT_A_METHOD", false))
        assertTrue(ummAlQura.fajr.after(egyptian.fajr))   // 18.5° vs 19.5°: a later dawn
        assertEquals(PrayerSessions.minute(egyptian.fajr), PrayerSessions.minute(unknown.fajr))
    }
}
