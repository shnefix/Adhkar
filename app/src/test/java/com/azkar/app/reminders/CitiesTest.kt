package com.azkar.app.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CitiesTest {
    @Test
    fun everyCityHasAUniqueIdValidCoordinatesAndBothNames() {
        assertEquals(Cities.ALL.size, Cities.ALL.map { it.id }.toSet().size)
        Cities.ALL.forEach {
            assertTrue(it.id, Cities.valid(it.lat, it.lng))
            assertTrue(it.id, it.en.isNotBlank() && it.ar.isNotBlank())
        }
    }

    @Test
    fun coordinatesOutsideTheGlobeAreRejected() {
        assertFalse(Cities.valid(91.0, 0.0))
        assertFalse(Cities.valid(0.0, -181.0))
    }

    @Test
    fun citiesGivePrayerTimesInDayOrder() {
        val makkah = Cities.byId("makkah")!!
        val t = PrayerSessions.timesOn(System.currentTimeMillis(), makkah.lat, makkah.lng)
        assertTrue(t.fajr.before(t.dhuhr) && t.dhuhr.before(t.asr) && t.asr.before(t.maghrib))
    }
}
