package com.azkar.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A source shows unless it is hidden for the whole app, for its folder, or for that one zikr. */
class SourceVisibilityTest {
    @Before
    fun setUp() = AppPrefs.attach(MemoryPrefs())

    @Test
    fun shownByDefault() {
        assertTrue(AppPrefs.showsSource("z1", "f1"))
        assertTrue(AppPrefs.showsSource("z1", null))
    }

    @Test
    fun hidingOneZikrLeavesTheOthers() {
        AppPrefs.setZikrSourceHidden("z1", true)
        assertFalse(AppPrefs.showsSource("z1", "f1"))
        assertTrue(AppPrefs.showsSource("z2", "f1"))
        AppPrefs.setZikrSourceHidden("z1", false)
        assertTrue(AppPrefs.showsSource("z1", "f1"))
    }

    @Test
    fun hidingAFolderHidesItsAzkarOnly() {
        AppPrefs.setFolderSourcesHidden("f1", true)
        assertFalse(AppPrefs.showsSource("z1", "f1"))
        assertTrue(AppPrefs.showsSource("z1", "f2"))
        assertTrue(AppPrefs.showsSource("z1", null))
    }

    @Test
    fun theAppSwitchHidesEverything() {
        AppPrefs.setSourcesOn(false)
        assertFalse(AppPrefs.showsSource("z1", "f1"))
        assertFalse(AppPrefs.showsSource("z1", null))
    }
}
