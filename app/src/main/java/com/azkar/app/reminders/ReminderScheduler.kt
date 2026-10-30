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
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.azkar.app.MainActivity
import com.azkar.app.R
import com.azkar.app.data.Builtins
import com.azkar.app.data.Store
import java.util.Calendar

data class ReminderDef(
    val key: String, val title: String, val collection: String,
    val hour: Int, val minute: Int, val default: Boolean, val label: String
)

object ReminderScheduler {
    const val CHANNEL = "azkar_reminders_v2"

    val defs = listOf(
        ReminderDef("m", "Morning azkar", "morning", 6, 0, true, "Every day · 6:00 AM"),
        ReminderDef("e", "Evening azkar", "evening", 17, 0, true, "Every day · 5:00 PM"),
        ReminderDef("s", "Before sleep", "sleep", 22, 0, false, "Every day · 10:00 PM")
    )

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Daily reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Morning, evening and sleep azkar reminders"
            enableVibration(true)
            setShowBadge(true)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun pending(ctx: Context, d: ReminderDef): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, defs.indexOf(d),
            Intent(ctx, ReminderReceiver::class.java).putExtra("key", d.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun rescheduleAll(ctx: Context) {
        defs.forEach { if (Store.reminderOn(it.key, it.default)) schedule(ctx, it) else cancel(ctx, it) }
    }

    fun schedule(ctx: Context, d: ReminderDef) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, d.hour); set(Calendar.MINUTE, d.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending(ctx, d))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending(ctx, d))
    }

    fun cancel(ctx: Context, d: ReminderDef) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx, d))
    }

    @SuppressLint("MissingPermission")
    fun notify(ctx: Context, d: ReminderDef) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, defs.indexOf(d),
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open", d.collection),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = "Time for your " + d.title.lowercase() + " · tap to read"
        val rv = RemoteViews(ctx.packageName, R.layout.notification_reminder).apply {
            setTextViewText(R.id.n_title, d.title)
            setTextViewText(R.id.n_body, body)
            setTextViewText(R.id.n_ar, Builtins.byKey(d.collection)?.homeArabic ?: "")
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell_1p75)
            .setColor(ctx.getColor(R.color.gold))
            .setContentTitle(d.title)
            .setContentText(body)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(100 + defs.indexOf(d), n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        ReminderScheduler.createChannel(ctx)
        val d = ReminderScheduler.defs.firstOrNull { it.key == intent.getStringExtra("key") } ?: return
        if (!Store.reminderOn(d.key, d.default)) return
        ReminderScheduler.notify(ctx, d)
        ReminderScheduler.schedule(ctx, d)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Store.init(ctx)
            ReminderScheduler.rescheduleAll(ctx)
        }
    }
}
