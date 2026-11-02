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
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.azkar.app.MainActivity
import com.azkar.app.R
import com.azkar.app.ThemeMode
import com.azkar.app.data.Builtins
import com.azkar.app.data.Schedule
import com.azkar.app.data.Store

object ReminderScheduler {
    const val CHANNEL = "azkar_reminders_v2"
    private const val ACTION = "com.azkar.app.REMINDER"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Daily reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Your azkar reminders"
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
    }

    /** Schedules or cancels one reminder according to the global and individual switches. */
    fun apply(ctx: Context, s: Schedule) {
        if (Store.notificationsOn() && s.enabled) schedule(ctx, s) else cancel(ctx, s.id)
    }

    fun schedule(ctx: Context, s: Schedule) {
        val am = alarms(ctx)
        val at = ReminderTime.next(s.hour, s.minute)
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
        val nid = 100 + (s.id.hashCode() and 0xFFFF)
        val open = PendingIntent.getActivity(
            ctx, nid,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { s.open?.let { putExtra("open", it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ar = s.open?.let { Builtins.byKey(it)?.homeArabic } ?: ""
        // The system UI inflates RemoteViews with the device's night setting, not ours,
        // so each theme has its own layout built only from colours that never flip.
        val layout = if (Store.theme() == ThemeMode.OLED) R.layout.notification_reminder_oled else R.layout.notification_reminder
        val rv = RemoteViews(ctx.packageName, layout).apply {
            setTextViewText(R.id.n_title, s.title)
            setTextViewText(R.id.n_body, s.message)
            setTextViewText(R.id.n_ar, ar)
            setViewVisibility(R.id.n_ar, if (ar.isEmpty()) View.GONE else View.VISIBLE)
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell_1p75)
            .setColor(ctx.getColor(R.color.gold))
            .setContentTitle(s.title)
            .setContentText(s.message)
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(nid, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        ReminderScheduler.createChannel(ctx)
        val id = intent.getStringExtra("id") ?: return          // ignores old-style alarms
        val s = Store.schedule(id) ?: return                    // deleted schedule: nothing to show
        if (!Store.notificationsOn() || !s.enabled) return
        ReminderScheduler.notify(ctx, s)
        ReminderScheduler.schedule(ctx, s)                      // next day
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Store.init(ctx)
            ReminderScheduler.createChannel(ctx)
            ReminderScheduler.rescheduleAll(ctx)
        }
    }
}