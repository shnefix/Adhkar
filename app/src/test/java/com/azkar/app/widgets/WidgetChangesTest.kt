package com.azkar.app.widgets

import com.azkar.app.reminders.PrayerSessions.Session
import com.azkar.app.ui.pickerFamilies
import com.azkar.app.ui.pickerStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetChangesTest {
    private val hour = 3_600_000L
    private val morning = AzkarSessionClock.Window(Session.MORNING, 5 * hour, 12 * hour)
    private val evening = AzkarSessionClock.Window(Session.EVENING, 15 * hour, 19 * hour)
    private val windows = listOf(morning, evening)

    // ── merged morning / evening / tahlil widget ──

    @Test
    fun morningWindowShowsMorning() {
        assertEquals(Session.MORNING, AzkarSessionClock.pick(windows, 5 * hour))
        assertEquals(Session.MORNING, AzkarSessionClock.pick(windows, 9 * hour))
    }

    @Test
    fun eveningWindowShowsEvening() {
        assertEquals(Session.EVENING, AzkarSessionClock.pick(windows, 15 * hour))
        assertEquals(Session.EVENING, AzkarSessionClock.pick(windows, 18 * hour + 59 * 60_000L))
    }

    @Test
    fun betweenWindowsShowsNeither_soTheLastWidgetIsDrawn() {
        assertNull(AzkarSessionClock.pick(windows, 3 * hour))
        assertNull(AzkarSessionClock.pick(windows, 12 * hour))   // the morning window is half open
        assertNull(AzkarSessionClock.pick(windows, 13 * hour))
        assertNull(AzkarSessionClock.pick(windows, 19 * hour))
        assertNull(AzkarSessionClock.pick(windows, 23 * hour))
        assertNull(AzkarSessionClock.pick(emptyList(), 9 * hour))
    }

    @Test
    fun nextBoundaryIsTheNextOpenOrClose() {
        assertEquals(5 * hour, AzkarSessionClock.boundary(windows, 1 * hour))
        assertEquals(12 * hour, AzkarSessionClock.boundary(windows, 5 * hour))
        assertEquals(15 * hour, AzkarSessionClock.boundary(windows, 12 * hour))
        assertEquals(19 * hour, AzkarSessionClock.boundary(windows, 16 * hour))
        assertNull(AzkarSessionClock.boundary(windows, 20 * hour))
    }

    @Test
    fun onlyOneMorningEveningWidgetRemains() {
        assertTrue(AzkarWidgetFamily.values().none { it.name == "M8_MOON" })
        assertEquals(WidgetTap.SESSION, AzkarWidgetFamily.M8_SUNRISE.tap)
        assertEquals(WidgetKind.MEDIUM, AzkarWidgetFamily.M8_SUNRISE.kind)
    }

    @Test
    fun tahlilIsStillTheLastMediumWidget() {
        assertEquals(AzkarWidgetFamily.M7_TAHLIL_FULL, AzkarWidgetFamily.values().last { it.kind == WidgetKind.MEDIUM })
    }

    // ── small widgets: tap enters the folder ──

    @Test
    fun starAndRosetteOpenTheirFolder() {
        assertEquals(WIDGET_FOLDER, AzkarWidgetFamily.G5_STAR_SMALL.openFolder)
        assertEquals(WIDGET_FOLDER, AzkarWidgetFamily.G_ROSETTE_SMALL.openFolder)
        assertEquals(WidgetTap.OPEN, AzkarWidgetFamily.G5_STAR_SMALL.tap)
        assertEquals(WidgetTap.OPEN, AzkarWidgetFamily.G_ROSETTE_SMALL.tap)
    }

    // ── small widgets: picker order ──

    @Test
    fun smallStylesHaveCalligraphyBeforeTasbih() {
        assertEquals(listOf(WidgetStyle.CLASSIC, WidgetStyle.CALLIGRAPHY, WidgetStyle.TASBIH), pickerStyles(WidgetKind.SMALL))
    }

    @Test
    fun otherSizesKeepTheirStyleOrder() {
        assertEquals(WidgetStyle.values().filter { WidgetKind.MEDIUM in it.offered }, pickerStyles(WidgetKind.MEDIUM))
    }

    @Test
    fun smallFamiliesSwapSecondAndThirdOfTheTrio() {
        val all = AzkarWidgetFamily.values().toList()
        val original = all.filter { it.kind == WidgetKind.SMALL }
        val ordered = pickerFamilies(WidgetKind.SMALL, all)
        assertEquals(original.size, ordered.size)
        assertEquals(original.toSet(), ordered.toSet())
        val d = original.indexOf(AzkarWidgetFamily.D_SMALL)
        val b = original.indexOf(AzkarWidgetFamily.B_SMALL)
        assertEquals(AzkarWidgetFamily.B_SMALL, ordered[d])
        assertEquals(AzkarWidgetFamily.D_SMALL, ordered[b])
        original.indices.filter { it != d && it != b }.forEach { assertEquals(original[it], ordered[it]) }
    }

    @Test
    fun smallPickerPositionsMatchTheRequest() {
        // Picker = 3 styles, then the families: positions 13 and 14 are the star and the rosette.
        val items = pickerStyles(WidgetKind.SMALL).size.let { styles ->
            List(styles) { null as AzkarWidgetFamily? } + pickerFamilies(WidgetKind.SMALL, AzkarWidgetFamily.values().toList())
        }
        assertEquals(AzkarWidgetFamily.B_SMALL, items[5 - 1])
        assertEquals(AzkarWidgetFamily.D_SMALL, items[6 - 1])
        assertEquals(AzkarWidgetFamily.G5_STAR_SMALL, items[13 - 1])
        assertEquals(AzkarWidgetFamily.G_ROSETTE_SMALL, items[14 - 1])
    }

    // ── second round ──

    @Test
    fun stripsAndTodayAreRetired() {
        assertEquals(setOf(AzkarWidgetFamily.J_STRIP_DHIKR, AzkarWidgetFamily.J_STRIP_PROGRESS, AzkarWidgetFamily.T_TODAY_MEDIUM), AzkarWidgetFamily.RETIRED)
    }

    @Test
    fun dialsOpenTasabihOutsideTheWindows() {
        assertEquals(SESSION_OR_TASABIH, AzkarWidgetFamily.A_DIAL_SMALL.openFolder)
        assertEquals(SESSION_OR_TASABIH, AzkarWidgetFamily.A_DIAL_MEDIUM.openFolder)
    }

    @Test
    fun mediumPickerNoLongerListsTheRetiredOnes() {
        val offered = AzkarWidgetFamily.values().filter { it !in AzkarWidgetFamily.RETIRED }
        val medium = pickerFamilies(WidgetKind.MEDIUM, offered)
        assertTrue(medium.none { it in AzkarWidgetFamily.RETIRED })
        assertEquals(AzkarWidgetFamily.M7_TAHLIL_FULL, medium.last())
        assertEquals(AzkarWidgetFamily.M8_SUNRISE, medium[medium.size - 2])
    }
}
