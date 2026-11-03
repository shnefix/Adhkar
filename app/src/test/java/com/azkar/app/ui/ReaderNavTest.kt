package com.azkar.app.ui

import com.azkar.app.ui.ReaderNav.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderNavTest {
    @Test
    fun ringCountsUpToTheTargetThenStops() {
        val nav = ReaderNav(listOf(3))
        assertTrue(nav.tapRing())
        assertTrue(nav.tapRing())
        assertFalse(nav.isDone)
        assertTrue(nav.tapRing())
        assertTrue(nav.isDone)
        assertFalse(nav.tapRing())
        assertEquals(3, nav.count)
    }

    @Test
    fun singleRecitationIsDoneWithoutCounting() {
        val nav = ReaderNav(listOf(1))
        assertTrue(nav.isDone)
        assertFalse(nav.tapRing())
    }

    @Test
    fun nextMovesAndResetsTheCount() {
        val nav = ReaderNav(listOf(3, 1))
        nav.tapRing()
        assertEquals(Outcome.MOVED, nav.next())
        assertEquals(1, nav.index)
        assertEquals(0, nav.count)
    }

    @Test
    fun lastZikrFinishesOnlyOnceItsCountIsComplete() {
        val nav = ReaderNav(listOf(1, 2), start = 1)
        assertEquals(Outcome.BOUNCE, nav.next())
        nav.tapRing(); nav.tapRing()
        assertTrue(nav.canFinish())
        assertEquals(Outcome.FINISH, nav.next())
    }

    @Test
    fun prevNeverFinishesAndBouncesAtTheStart() {
        val nav = ReaderNav(listOf(1, 1))
        assertEquals(Outcome.BOUNCE, nav.prev())
        nav.next()
        assertEquals(Outcome.MOVED, nav.prev())
        assertEquals(0, nav.index)
    }

    @Test
    fun startIndexIsClampedIntoRange() {
        assertEquals(1, ReaderNav(listOf(1, 1), start = 9).index)
        assertEquals(0, ReaderNav(listOf(1, 1), start = -4).index)
    }
}
