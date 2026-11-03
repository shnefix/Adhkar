package com.azkar.app.data

import android.content.SharedPreferences
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

/** In-memory SharedPreferences: enough for objects that only read and write strings. */
private class MemoryPrefs : SharedPreferences {
    private val values = HashMap<String, Any?>()

    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?) = values[key] as Set<String>? ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removed = HashSet<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed.add(key) }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            removed.forEach { values.remove(it) }
            values.putAll(pending)
            return true
        }
        override fun apply() { commit() }
    }
}
