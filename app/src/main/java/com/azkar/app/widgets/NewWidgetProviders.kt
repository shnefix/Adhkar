package com.azkar.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.widget.RemoteViews
import com.azkar.app.R
import com.azkar.app.launchWhenReady

/** [tracking] families draw [TrackingModel] and are only offered while a folder is tracked. */
enum class AzkarWidgetFamily(val layout: Int, val info: Int, val kind: WidgetKind, val tracking: Boolean = false) {
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
    B_MEDIUM(R.layout.azkar_widget_b_medium, R.xml.azkar_widget_b_medium_info, WidgetKind.MEDIUM),
    C_STREAK_SMALL(R.layout.azkar_widget_tracking, R.xml.azkar_widget_c_streak_small_info, WidgetKind.SMALL, true),
    C_STREAK_MEDIUM(R.layout.azkar_widget_tracking, R.xml.azkar_widget_c_streak_medium_info, WidgetKind.MEDIUM, true),
    C_STREAK_LARGE(R.layout.azkar_widget_tracking, R.xml.azkar_widget_c_streak_large_info, WidgetKind.LARGE, true),
    G_COUNTDOWN_SMALL(R.layout.azkar_widget_tracking, R.xml.azkar_widget_g_countdown_small_info, WidgetKind.SMALL, true),
    F_BENTO_SMALL(R.layout.azkar_widget_tracking, R.xml.azkar_widget_f_bento_small_info, WidgetKind.SMALL, true),
    T_TODAY_MEDIUM(R.layout.azkar_widget_tracking, R.xml.azkar_widget_t_today_medium_info, WidgetKind.MEDIUM, true)
}

abstract class BaseAzkarWidgetProvider(private val family: AzkarWidgetFamily) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetClockReceiver.scheduleMidnight(context)
        launchWhenReady(context) {
            // Widgets that show a countdown would be up to an hour out of date with the usual hourly refresh.
            if (family in NewWidgetUpdater.COUNTDOWN_FAMILIES) {
                ids.forEach { WidgetStore.setDefaultRefresh(context, it, NewWidgetUpdater.COUNTDOWN_REFRESH_MINUTES) }
            }
            NewWidgetUpdater.updateIds(context, manager, family, ids)
            ids.forEach { NewWidgetUpdater.scheduleRefresh(context, family, it) }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        launchWhenReady(context) { NewWidgetUpdater.update(context, manager, family, id) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetStore.clear(context, it); NewWidgetUpdater.cancelRefresh(context, family, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Only redraws are accepted here: this provider is exported, so taps go to WidgetActionReceiver.
        if (intent.action in setOf(ACTION_REFRESH, ACTION_BOUNDARY) && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            launchWhenReady(context) { NewWidgetUpdater.update(context, AppWidgetManager.getInstance(context), family, id) }
        } else if (intent.action !in WidgetActionReceiver.ACTIONS) {
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
        AzkarWidgetFamily.B_MEDIUM to BMediumWidgetProvider::class.java,
        AzkarWidgetFamily.C_STREAK_SMALL to CStreakSmallWidgetProvider::class.java,
        AzkarWidgetFamily.C_STREAK_MEDIUM to CStreakMediumWidgetProvider::class.java,
        AzkarWidgetFamily.C_STREAK_LARGE to CStreakLargeWidgetProvider::class.java,
        AzkarWidgetFamily.G_COUNTDOWN_SMALL to GCountdownSmallWidgetProvider::class.java,
        AzkarWidgetFamily.F_BENTO_SMALL to FBentoSmallWidgetProvider::class.java,
        AzkarWidgetFamily.T_TODAY_MEDIUM to TTodayMediumWidgetProvider::class.java
    )

    fun providerClass(family: AzkarWidgetFamily): Class<*> = providers.getValue(family)

    /** Families showing time left; they start with [COUNTDOWN_REFRESH_MINUTES] instead of hourly. */
    val COUNTDOWN_FAMILIES = setOf(AzkarWidgetFamily.G_COUNTDOWN_SMALL, AzkarWidgetFamily.F_BENTO_SMALL, AzkarWidgetFamily.T_TODAY_MEDIUM)
    const val COUNTDOWN_REFRESH_MINUTES = 15

    /**
     * Tracking widgets are only listed by the launcher while tracking is on: their providers start disabled
     * in the manifest and are enabled here once a folder is tracked (disabling removes placed ones).
     */
    fun syncTrackingProviders(context: Context) {
        val state = if (com.azkar.app.data.Tracking.anyTracked()) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        AzkarWidgetFamily.values().filter { it.tracking }.forEach { family ->
            val component = ComponentName(context, providers.getValue(family))
            if (context.packageManager.getComponentEnabledSetting(component) != state) {
                context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }

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
        if (family.tracking) {
            val t = TrackingModel.load(context, config.folder)
            rv.setImageViewBitmap(R.id.azkar_widget_image, NewWidgetRenderer(context).render(family, model, width, height, t))
            rv.setContentDescription(R.id.azkar_widget_image, context.getString(R.string.widget_tracking_description, t.title, t.streak, t.best))
            // A tracking widget has nothing to count: a tap opens the session it shows (or the app).
            rv.setOnClickPendingIntent(R.id.azkar_widget_hit, openApp(context, family, id, t.primaryFolder))
            scheduleBoundary(context, family, id, t)
        } else {
            rv.setImageViewBitmap(R.id.azkar_widget_image, NewWidgetRenderer(context).render(family, model, width, height))
            rv.setContentDescription(
                R.id.azkar_widget_image,
                if (model.n == 0) context.getString(R.string.widget_no_azkar_description)
                else "${model.folderName}: " + context.getString(R.string.widget_count_of, model.shown, model.target)
            )
            // The progress strip shows no repeat count, so on the morning and evening folders a tap moves to the next zikr.
            val stepFolder = config.folder == com.azkar.app.data.DefaultFolders.MORNING || config.folder == com.azkar.app.data.DefaultFolders.EVENING
            val tapAction = if (family == AzkarWidgetFamily.J_STRIP_PROGRESS && stepFolder) ACTION_STEP else ACTION_COUNT
            rv.setOnClickPendingIntent(R.id.azkar_widget_hit, action(context, family, id, tapAction))
            if (family == AzkarWidgetFamily.H_LARGE) {
                rv.setOnClickPendingIntent(R.id.azkar_widget_undo, action(context, family, id, ACTION_UNDO))
            }
        }
        manager.updateAppWidget(id, rv)
    }

    private fun openApp(context: Context, family: AzkarWidgetFamily, id: Int, folder: String?): PendingIntent {
        val intent = Intent(context, com.azkar.app.MainActivity::class.java)
            .setData(Uri.parse("azkar://new-widget/${family.name}/$id/open"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { folder?.let { putExtra("open", it) } }
        return PendingIntent.getActivity(context, id * 1000 + family.ordinal * 10 + 9, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Redraws a tracking widget when a session starts or ends, so "now" / "next" flip on time. */
    private fun scheduleBoundary(context: Context, family: AzkarWidgetFamily, id: Int, t: TrackingModel) {
        val next = t.sessions.flatMap { listOfNotNull(it.start, it.end, it.nextStart) }.filter { it > t.now }.minOrNull()
            ?: return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarm.set(android.app.AlarmManager.RTC, next + 1_000L, action(context, family, id, ACTION_BOUNDARY))
    }

    private fun action(context: Context, family: AzkarWidgetFamily, id: Int, action: String): PendingIntent {
        val target = if (action in WidgetActionReceiver.ACTIONS) WidgetActionReceiver::class.java else providers.getValue(family)
        val intent = Intent(context, target).setAction(action)
            .setData(Uri.parse("azkar://new-widget/${family.name}/$id/$action"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(WidgetActionReceiver.EXTRA_FAMILY, family.name)
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
        if (family.tracking) alarm.cancel(action(context, family, id, ACTION_BOUNDARY))
    }
}
