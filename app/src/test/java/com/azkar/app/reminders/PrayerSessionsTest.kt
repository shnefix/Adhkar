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
        assertFalse(PrayerSessions.positionExpired("d_14", at(7), at(20, day = 4), null, zone))
    }
}
