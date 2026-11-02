package com.azkar.app.reminders

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReminderTimeTest {
    @Test
    fun schedulesTomorrowWhenTodaysTimeHasPassed() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 10, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next = Calendar.getInstance().apply { timeInMillis = ReminderTime.next(17, 0, now.timeInMillis) }
        assertTrue(next.after(now))
        assertTrue(next.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun preservesValidLocalTimeAcrossDaylightSavingTransitions() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 28, 23, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next = Calendar.getInstance().apply { timeInMillis = ReminderTime.next(8, 0, now.timeInMillis) }
        assertTrue(next.get(Calendar.HOUR_OF_DAY) == 8)
        assertTrue(next.after(now))
    }
}
