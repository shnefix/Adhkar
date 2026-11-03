package com.azkar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePrefsTest {
    private val prefs = MemoryPrefs()
    private val home = HomePrefs().apply { attach(prefs) }

    @Test
    fun orderKeepsSavedFoldersAndAppendsNewOnes() {
        home.setOrder(listOf("b", "a"))
        assertEquals(listOf("b", "a", "c"), home.order(listOf("a", "b", "c")))
    }

    @Test
    fun orderDropsFoldersThatNoLongerExist() {
        home.setOrder(listOf("gone", "b", "a"))
        assertEquals(listOf("b", "a"), home.order(listOf("a", "b")))
    }

    @Test
    fun hiddenAndPlacementSurviveANewInstance() {
        home.setHidden(setOf("x"))
        home.setPlacement(Placement.TOP)
        val again = HomePrefs().apply { attach(prefs) }
        assertEquals(setOf("x"), again.hidden())
        assertEquals(Placement.TOP, again.placement())
    }

    @Test
    fun resetForgetsOrderAndPlacementAndHidesOnlyTheDefaults() {
        home.setOrder(listOf("b", "a"))
        home.setPlacement(Placement.BOTTOM)
        home.setHidden(setOf("a"))
        home.reset(setOf("z"))
        assertEquals(listOf("a", "b"), home.order(listOf("a", "b")))
        assertEquals(Placement.MIXED, home.placement())
        assertEquals(setOf("z"), home.hidden())
    }

    @Test
    fun hiddenZikrsAreSavedAndPublished() {
        home.setZikrsHidden(listOf("z1", "z2"), true)
        home.setZikrsHidden(listOf("z1"), false)
        assertEquals(setOf("z2"), home.hiddenZikrs.value)
        val again = HomePrefs().apply { attach(prefs); loadHiddenZikrs() }
        assertTrue("z2" in again.hiddenZikrs.value)
    }
}
