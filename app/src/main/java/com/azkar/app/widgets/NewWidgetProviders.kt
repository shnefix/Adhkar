package com.azkar.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.RemoteViews
import com.azkar.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class AzkarWidgetFamily(val layout: Int, val info: Int, val kind: WidgetKind) {
    H_SMALL(R.layout.azkar_widget_h_small, R.xml.azkar_widget_h_small_info, WidgetKind.SMALL),
    H_MEDIUM(R.layout.azkar_widget_h_medium, R.xml.azkar_widget_h_medium_info, WidgetKind.MEDIUM),
    H_LARGE(R.layout.azkar_widget_h_large, R.xml.azkar_widget_h_large_info, WidgetKind.LARGE),
    J_STRIP_DHIKR(R.layout.azkar_widget_j_strip_dhikr, R.xml.azkar_widget_j_strip_dhikr_info, WidgetKind.MEDIUM),
    J_STRIP_PROGRESS(R.layout.azkar_widget_j_strip_progress, R.xml.azkar_widget_j_strip_progress_info, WidgetKind.MEDIUM),
    J_TALL_RING(R.layout.azkar_widget_j_tall_ring, R.xml.azkar_widget_j_tall_ring_info, WidgetKind.LARGE),
    J_TALL_TIMELINE(R.layout.azkar_widget_j_tall_timeline, R.xml.azkar_widget_j_tall_timeline_info, WidgetKind.LARGE),
    D_SMALL(R.layout.azkar_widget_d_small, R.xml.azkar_widget_d_small_info, WidgetKind.SMALL),
    D_MEDIUM(R.layout.azkar_widget_d_medium, R.xml.azkar_widget_d_medium_info, WidgetKind.MEDIUM),
    D_LARGE(R.layout.azkar_widget_d_large, R.xml.azkar_widget_d_large_info, WidgetKind.LARGE),
    B_SMALL(R.layout.azkar_widget_b_small, R.xml.azkar_widget_b_small_info, WidgetKind.SMALL),
    B_MEDIUM(R.layout.azkar_widget_b_medium, R.xml.azkar_widget_b_medium_info, WidgetKind.MEDIUM)
}

abstract class BaseAzkarWidgetProvider(private val family: AzkarWidgetFamily) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        com.azkar.app.data.Store.init(context)
        WidgetClockReceiver.scheduleMidnight(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.azkar.app.data.Store.awaitReady()
                NewWidgetUpdater.updateIds(context, manager, family, ids)
                ids.forEach { NewWidgetUpdater.scheduleRefresh(context, family, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        com.azkar.app.data.Store.init(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.azkar.app.data.Store.awaitReady()
                NewWidgetUpdater.update(context, manager, family, id)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetStore.clear(context, it); NewWidgetUpdater.cancelRefresh(context, family, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (intent.action == ACTION_REFRESH && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            NewWidgetUpdater.update(context, AppWidgetManager.getInstance(context), family, id)
        } else if (intent.action in setOf(ACTION_COUNT, ACTION_RESET, ACTION_NEXT, ACTION_UNDO) &&
            id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    com.azkar.app.data.Store.init(context)
                    com.azkar.app.data.Store.awaitReady()
                    when (intent.action) {
                        ACTION_COUNT -> WidgetLogic.tap(context, id, family.kind)
                        ACTION_RESET -> WidgetLogic.reset(context, id)
                        ACTION_NEXT -> WidgetLogic.next(context, id, family.kind)
                        ACTION_UNDO -> WidgetLogic.undo(context, id, family.kind)
                    }
                    NewWidgetUpdater.updateAll(context)
                } finally {
                    pending.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }
}

object NewWidgetUpdater {
    private val providers = mapOf(
        AzkarWidgetFamily.H_SMALL to HSmallWidgetProvider::class.java,
        AzkarWidgetFamily.H_MEDIUM to HMediumWidgetProvider::class.java,
        AzkarWidgetFamily.H_LARGE to HLargeWidgetProvider::class.java,
        AzkarWidgetFamily.J_STRIP_DHIKR to JStripDhikrWidgetProvider::class.java,
        AzkarWidgetFamily.J_STRIP_PROGRESS to JStripProgressWidgetProvider::class.java,
        AzkarWidgetFamily.J_TALL_RING to JTallRingWidgetProvider::class.java,
        AzkarWidgetFamily.J_TALL_TIMELINE to JTallTimelineWidgetProvider::class.java,
        AzkarWidgetFamily.D_SMALL to DSmallWidgetProvider::class.java,
        AzkarWidgetFamily.D_MEDIUM to DMediumWidgetProvider::class.java,
        AzkarWidgetFamily.D_LARGE to DLargeWidgetProvider::class.java,
        AzkarWidgetFamily.B_SMALL to BSmallWidgetProvider::class.java,
        AzkarWidgetFamily.B_MEDIUM to BMediumWidgetProvider::class.java
    )

    fun providerClass(family: AzkarWidgetFamily): Class<*> = providers.getValue(family)

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        providers.forEach { (family, provider) ->
            manager.getAppWidgetIds(ComponentName(context, provider)).forEach {
                update(context, manager, family, it)
            }
        }
    }

    fun updateIds(context: Context, manager: AppWidgetManager, family: AzkarWidgetFamily, ids: IntArray) {
        ids.forEach { update(context, manager, family, it) }
    }

    fun update(context: Context, manager: AppWidgetManager, family: AzkarWidgetFamily, id: Int) {
        com.azkar.app.data.Store.init(context)
        val config = WidgetStore.config(context, id, family.kind)
        val model = WidgetModel.load(context, id, config.folder)

        val o = manager.getAppWidgetOptions(id)
        val land = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val def = AzkarWidgetSizes.design(family)
        val width = (if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
            .takeIf { it > 0 }?.toFloat() ?: def.first
        val height = (if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT))
            .takeIf { it > 0 }?.toFloat() ?: def.second

        val rv = RemoteViews(context.packageName, family.layout)
        rv.setImageViewBitmap(R.id.azkar_widget_image, NewWidgetRenderer(context).render(family, model, width, height))
        rv.setContentDescription(
            R.id.azkar_widget_image,
            if (model.n == 0) context.getString(R.string.widget_no_azkar_description)
            else "${model.folderName}: ${model.shown} of ${model.target}"
        )
        rv.setOnClickPendingIntent(R.id.azkar_widget_hit, action(context, family, id, ACTION_COUNT))
        if (family == AzkarWidgetFamily.H_LARGE) {
            rv.setOnClickPendingIntent(R.id.azkar_widget_undo, action(context, family, id, ACTION_UNDO))
        }
        scheduleRefresh(context, family, id)
        manager.updateAppWidget(id, rv)
    }

    private fun action(context: Context, family: AzkarWidgetFamily, id: Int, action: String): PendingIntent {
        val intent = Intent(context, providers.getValue(family)).setAction(action)
            .setData(Uri.parse("azkar://new-widget/${family.name}/$id/$action"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        return PendingIntent.getBroadcast(context, id * 1000 + family.ordinal * 10 + action.hashCode().and(7), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun scheduleRefresh(context: Context, family: AzkarWidgetFamily, id: Int) {
        val minutes = WidgetStore.config(context, id, family.kind).appearance.refreshMinutes
        if (minutes <= 0) {
            cancelRefresh(context, family, id)
            return
        }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarm.setInexactRepeating(
            android.app.AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + minutes * 60_000L,
            minutes * 60_000L,
            action(context, family, id, ACTION_REFRESH)
        )
    }

    fun cancelRefresh(context: Context, family: AzkarWidgetFamily, id: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarm.cancel(action(context, family, id, ACTION_REFRESH))
    }
}
