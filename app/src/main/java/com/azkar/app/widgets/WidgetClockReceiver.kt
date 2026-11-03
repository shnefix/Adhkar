package com.azkar.app.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.azkar.app.launchWhenReady
import java.util.Calendar

/**
 * Redraws every widget when the day rolls over or the clock/timezone changes, so daily progress
 * resets on time. DATE_CHANGED isn't delivered to manifest receivers on API 26+, hence the alarm.
 */
class WidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        scheduleMidnight(context)
        launchWhenReady(context) { WidgetUpdater.updateAll(context) }
    }

    companion object {
        private const val ACTION_MIDNIGHT = "com.azkar.app.widget.MIDNIGHT"
        private val HANDLED = setOf(
            ACTION_MIDNIGHT,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED
        )

        /** Replaces any pending rollover alarm with one just after the next local midnight. */
        fun scheduleMidnight(context: Context) {
            val at = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, WidgetClockReceiver::class.java).setAction(ACTION_MIDNIGHT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
        }
    }
}
