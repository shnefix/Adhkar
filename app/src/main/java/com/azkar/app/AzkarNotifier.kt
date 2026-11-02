package com.azkar.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.azkar.app.reminders.ReminderScheduler

object AzkarNotifier {

    private const val CHANNEL_ID = ReminderScheduler.CHANNEL
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        ReminderScheduler.createChannel(context)
    }

    fun showBeforeSleep(context: Context) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open", "sleep")
        }
        val tapIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pill = RemoteViews(context.packageName, R.layout.notification_pill).apply {
            setTextViewText(R.id.title_en, context.getString(R.string.before_sleep_en))
            setTextViewText(R.id.title_ar, context.getString(R.string.before_sleep_ar))
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_star)
            .setColor(0xFFF0B94F.toInt())
            .setCustomContentView(pill)
            .setCustomBigContentView(pill)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        context.getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
