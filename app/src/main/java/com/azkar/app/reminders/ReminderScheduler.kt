package com.azkar.app.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.azkar.app.MainActivity
import com.azkar.app.R
import com.azkar.app.data.Schedule
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.Store
import com.azkar.app.data.Tracking
import com.azkar.app.launchWhenReady

object ReminderScheduler {
    const val CHANNEL = "azkar_reminders_v2"
    private const val ACTION = "com.azkar.app.REMINDER"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = ctx.getString(R.string.channel_description)
            enableVibration(true)
            setShowBadge(true)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun alarms(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Exact alarms need a user grant on Android 12+. */
    fun canExact(ctx: Context) = Build.VERSION.SDK_INT < 31 || alarms(ctx).canScheduleExactAlarms()

    // One alarm per schedule id (unique data URI), so editing replaces and deleting cancels exactly that alarm.
    private fun intentFor(ctx: Context, id: String) =
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION).setData(Uri.parse("azkar://reminder/$id")).putExtra("id", id)

    private fun pending(ctx: Context, id: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, 0, intentFor(ctx, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Alarms from the old fixed reminders (request codes 0..2, no action). */
    private fun cancelLegacy(ctx: Context) {
        for (i in 0..2) {
            PendingIntent.getBroadcast(ctx, i, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let { alarms(ctx).cancel(it); it.cancel() }
        }
    }

    fun rescheduleAll(ctx: Context) {
        cancelLegacy(ctx)
        Store.schedules().forEach { apply(ctx, it) }
        scheduleNudges(ctx)
    }

    // ---------- "Not finished yet" (tracked morning / evening only) ----------
    private const val NUDGE = "nudge_"
    private const val NUDGE_NOTIFICATION_BASE = 70_000

    /** Sets or cancels the reminder before Dhuhr / Maghrib for each session, by the tracking and nudge switches. */
    fun scheduleNudges(ctx: Context) {
        val now = System.currentTimeMillis()
        PrayerSessions.Session.values().forEach { s ->
            val id = NUDGE + s.scheduleId
            val at = if (AppPrefs.notificationsOn() && AppPrefs.nudgeOn() && Tracking.isTracked(s.folderId))
                PrayerSessions.nextNudge(s, now, AppPrefs.nudgeLeadMinutes() * 60_000L) else null
            if (at == null) cancel(ctx, id)
            else if (canExact(ctx)) alarms(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, id))
            else alarms(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, id))
        }
    }

    /** Session behind a nudge alarm id, or null for an ordinary reminder. */
    fun nudgeSession(id: String): PrayerSessions.Session? =
        if (id.startsWith(NUDGE)) PrayerSessions.forSchedule(id.removePrefix(NUDGE)) else null

    @SuppressLint("MissingPermission")
    fun notifyNudge(ctx: Context, s: PrayerSessions.Session) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val now = System.currentTimeMillis()
        val items = Store.readerItems(s.folderId)
        val done = Tracking.today(s.folderId)?.done ?: 0
        if (items.isEmpty() || done >= items.size) return              // finished (or nothing to read): stay quiet
        val end = PrayerSessions.endAfter(s, now) ?: return
        val morning = s == PrayerSessions.Session.MORNING
        val nid = NUDGE_NOTIFICATION_BASE + s.ordinal   // outside the 100..65635 range used by reminders, so they never replace each other
        val open = PendingIntent.getActivity(
            ctx, nid,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("open", s.folderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endLabel = android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(end))
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell_1p75)
            .setColor(ctx.getColor(R.color.gold))
            .setContentTitle(ctx.getString(if (morning) R.string.nudge_title_morning else R.string.nudge_title_evening))
            .setContentText(ctx.getString(if (morning) R.string.nudge_text_morning else R.string.nudge_text_evening, done, items.size, endLabel))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(end - now)
            .build()
        NotificationManagerCompat.from(ctx).notify(nid, n)
    }

    /** Schedules or cancels one reminder according to the global and individual switches. */
    fun apply(ctx: Context, s: Schedule) {
        if (AppPrefs.notificationsOn() && s.enabled) schedule(ctx, s) else cancel(ctx, s.id)
    }

    fun schedule(ctx: Context, s: Schedule) {
        val am = alarms(ctx)
        // Morning / evening / before sleep follow Fajr / Asr / Isha when a location is known; otherwise the fixed time.
        val at = PrayerSessions.nextPrayerReminder(s.id, System.currentTimeMillis())
            ?: ReminderTime.next(s.hour, s.minute)
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, s.id))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, s.id))
    }

    fun cancel(ctx: Context, id: String) {
        PendingIntent.getBroadcast(ctx, 0, intentFor(ctx, id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { alarms(ctx).cancel(it); it.cancel() }
    }

    @SuppressLint("MissingPermission")
    fun notify(ctx: Context, s: Schedule) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        // A morning / evening reminder only lives until Dhuhr / Maghrib; one arriving after that is dropped.
        val now = System.currentTimeMillis()
        val endsAt = PrayerSessions.forSchedule(s.id)?.let { PrayerSessions.endAfter(it, now) }
        val nid = 100 + (s.id.hashCode() and 0xFFFF)
        // A deleted folder just opens the app; a before-sleep reminder without a folder opens the sleep azkar.
        val folder = s.open?.takeIf { Store.folder(it) != null }
            ?: PrayerSessions.SLEEP_FOLDER.takeIf { s.id == PrayerSessions.SLEEP_SCHEDULE && Store.folder(it) != null }
        val open = PendingIntent.getActivity(
            ctx, nid,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { folder?.let { putExtra("open", it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Android's own template (no custom view): Android 12+ always draws a system frame around custom
        // views, so a custom dark box can't fill the notification. The folder's Arabic name joins the title.
        val ar = folder?.let { Store.folder(it)?.name }   // no folder (or a deleted one): title alone
        val title = if (ar.isNullOrEmpty()) s.title else "${s.title} · $ar"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell_1p75)
            .setColor(ctx.getColor(R.color.gold))
            .setContentTitle(title)
            .setContentText(s.message.ifBlank { ctx.getString(R.string.reminder_default_message) })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply { endsAt?.let { setTimeoutAfter(it - now) } }
            .build()
        NotificationManagerCompat.from(ctx).notify(nid, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        launchWhenReady(ctx) {
            ReminderScheduler.createChannel(ctx)
            val id = intent.getStringExtra("id") ?: return@launchWhenReady
            ReminderScheduler.nudgeSession(id)?.let { session ->
                // Only while that session is still running; the check for "finished" is in notifyNudge.
                if (AppPrefs.notificationsOn() && AppPrefs.nudgeOn() && Tracking.isTracked(session.folderId) &&
                    PrayerSessions.inSession(session, System.currentTimeMillis()) == true
                ) ReminderScheduler.notifyNudge(ctx, session)
                ReminderScheduler.scheduleNudges(ctx)
                return@launchWhenReady
            }
            val s = Store.schedule(id) ?: return@launchWhenReady
            if (!AppPrefs.notificationsOn() || !s.enabled) return@launchWhenReady
            // A delayed alarm that lands after Dhuhr / Maghrib skips today's reminder.
            val late = PrayerSessions.forSchedule(id)?.let { PrayerSessions.inSession(it, System.currentTimeMillis()) } == false
            if (!late) ReminderScheduler.notify(ctx, s)
            ReminderScheduler.schedule(ctx, s)
        }
    }
}

/** Re-arms every reminder after a reboot, an app update, a new exact-alarm grant, or a clock / time zone change. */
class BootReceiver : BroadcastReceiver() {
    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        launchWhenReady(ctx) {
            ReminderScheduler.createChannel(ctx)
            ReminderScheduler.rescheduleAll(ctx)
        }
    }
}
