package com.azkar.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.azkar.app.MainActivity
import com.azkar.app.R
import com.azkar.app.launchWhenReady

const val ACTION_COUNT = "com.azkar.app.widget.COUNT"
const val ACTION_RESET = "com.azkar.app.widget.RESET"
const val ACTION_NEXT = "com.azkar.app.widget.NEXT"
const val ACTION_UNDO = "com.azkar.app.widget.UNDO"
const val ACTION_REFRESH = "com.azkar.app.widget.REFRESH"
const val ACTION_BOUNDARY = "com.azkar.app.widget.BOUNDARY"   // tracking widgets: a session started or ended

abstract class BaseWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        WidgetClockReceiver.scheduleMidnight(ctx)
        launchWhenReady(ctx) {
            ids.forEach {
                WidgetUpdater.update(ctx, mgr, it, kind)
                WidgetUpdater.scheduleRefresh(ctx, it, kind)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        launchWhenReady(ctx) { WidgetUpdater.update(ctx, mgr, id, kind) }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach {
            WidgetUpdater.cancelRefresh(ctx, it, kind)
            WidgetStore.clear(ctx, it)
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Only redraws are accepted here: this provider is exported, so taps go to WidgetActionReceiver.
        when (intent.action) {
            ACTION_REFRESH -> {
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                launchWhenReady(ctx) { WidgetUpdater.update(ctx, AppWidgetManager.getInstance(ctx), id, kind) }
            }
            in WidgetActionReceiver.ACTIONS -> Unit
            else -> super.onReceive(ctx, intent)
        }
    }
}

class SmallWidgetProvider : BaseWidgetProvider(WidgetKind.SMALL)      // 2x2
class MediumWidgetProvider : BaseWidgetProvider(WidgetKind.MEDIUM)    // 4x2
class LargeWidgetProvider : BaseWidgetProvider(WidgetKind.LARGE)      // 4x4

object WidgetUpdater {
    private val DEFAULT_DP = mapOf(
        WidgetKind.SMALL to (158f to 158f),
        WidgetKind.MEDIUM to (338f to 158f),
        WidgetKind.LARGE to (338f to 354f),
    )

    fun providerClass(kind: WidgetKind): Class<*> = when (kind) {
        WidgetKind.SMALL -> SmallWidgetProvider::class.java
        WidgetKind.MEDIUM -> MediumWidgetProvider::class.java
        WidgetKind.LARGE -> LargeWidgetProvider::class.java
    }

    fun kindOf(ctx: Context, id: Int): WidgetKind {
        val cn = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)?.provider?.className
        return WidgetKind.values().firstOrNull { providerClass(it).name == cn } ?: WidgetKind.SMALL
    }

    /** Re-draws every widget (call it when the app's data may have changed, e.g. MainActivity.onStop). */
    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        WidgetKind.values().forEach { k ->
            mgr.getAppWidgetIds(ComponentName(ctx, providerClass(k))).forEach { update(ctx, mgr, it, k) }
        }
        NewWidgetUpdater.updateAll(ctx)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, kind: WidgetKind) {
        val cfg = WidgetStore.config(ctx, id, kind)
        val model = WidgetModel.load(ctx, id, cfg.folder)

        // Real size of this widget on the launcher grid, in dp.
        val o = mgr.getAppWidgetOptions(id)
        val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val def = DEFAULT_DP.getValue(kind)
        val w = (if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
            .takeIf { it > 0 }?.toFloat() ?: def.first
        val h = (if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT))
            .takeIf { it > 0 }?.toFloat() ?: def.second

        val bmp = WidgetRenderer(ctx).render(
            cfg.style, kind, model, w, h, ctx.resources.displayMetrics.density, cfg.appearance
        )

        val layout = when {
            cfg.style == WidgetStyle.CLASSIC && kind == WidgetKind.LARGE -> R.layout.widget_classic_large
            cfg.style == WidgetStyle.TASBIH && kind == WidgetKind.LARGE -> R.layout.widget_tasbih_large
            else -> R.layout.widget_plain
        }
        val rv = RemoteViews(ctx.packageName, layout)
        rv.setImageViewBitmap(R.id.img, bmp)
        rv.setContentDescription(
            R.id.img,
            if (model.n == 0) ctx.getString(R.string.widget_no_azkar_description)
            else "${model.folderName}: ${model.shown} of ${model.target}"
        )
        rv.setContentDescription(R.id.hit_open, ctx.getString(R.string.widget_action_open))
        rv.setContentDescription(R.id.hit_count, ctx.getString(R.string.widget_action_count))

        val primary = openApp(ctx, id, cfg.folder)
        rv.setOnClickPendingIntent(R.id.hit_open, primary)
        val count = action(ctx, id, kind, ACTION_COUNT)
        val click = when (cfg.appearance.tapAction) {
            WidgetStore.TapAction.INCREMENT -> count
            WidgetStore.TapAction.NEXT_ZIKR -> action(ctx, id, kind, ACTION_NEXT)
            WidgetStore.TapAction.OPEN_APP, WidgetStore.TapAction.OPEN_READER -> openApp(ctx, id, cfg.folder)
        }
        rv.setOnClickPendingIntent(R.id.hit_count, click)
        if (layout == R.layout.widget_classic_large) {
            rv.setContentDescription(R.id.hit_count2, ctx.getString(R.string.widget_action_count))
            rv.setContentDescription(R.id.btn_plus, ctx.getString(R.string.widget_action_count))
            rv.setOnClickPendingIntent(R.id.hit_count2, click)
            rv.setOnClickPendingIntent(R.id.btn_plus, click)
        }

        if (layout == R.layout.widget_tasbih_large) {
            rv.setContentDescription(R.id.btn_main, ctx.getString(R.string.widget_action_count))
            rv.setContentDescription(R.id.btn_reset, ctx.getString(R.string.widget_action_reset))
            rv.setOnClickPendingIntent(R.id.btn_main, click)
            rv.setOnClickPendingIntent(R.id.btn_reset, action(ctx, id, kind, ACTION_RESET))
        }

        mgr.updateAppWidget(id, rv)
    }

    fun preview(ctx: Context, kind: WidgetKind, style: WidgetStyle, folder: String): RemoteViews {
        val bmp = previewBitmap(ctx, kind, style, folder)
        val layout = when {
            style == WidgetStyle.CLASSIC && kind == WidgetKind.LARGE -> R.layout.widget_classic_large
            style == WidgetStyle.TASBIH && kind == WidgetKind.LARGE -> R.layout.widget_tasbih_large
            else -> R.layout.widget_plain
        }
        return RemoteViews(ctx.packageName, layout).apply { setImageViewBitmap(R.id.img, bmp) }
    }

    fun previewBitmap(ctx: Context, kind: WidgetKind, style: WidgetStyle, folder: String): android.graphics.Bitmap {
        val cfg = WidgetStore.Config(style, folder)
        val model = WidgetModel.load(ctx, AppWidgetManager.INVALID_APPWIDGET_ID, folder)
        val dimensions = DEFAULT_DP.getValue(kind)
        return WidgetRenderer(ctx).render(
            style, kind, model, dimensions.first, dimensions.second,
            ctx.resources.displayMetrics.density, cfg.appearance
        )
    }

    fun scheduleRefresh(ctx: Context, id: Int, kind: WidgetKind) {
        val minutes = WidgetStore.config(ctx, id, kind).appearance.refreshMinutes
        if (minutes <= 0) {
            cancelRefresh(ctx, id, kind)
            return
        }
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pi = action(ctx, id, kind, ACTION_REFRESH)
        alarm.setInexactRepeating(
            android.app.AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + minutes * 60_000L,
            minutes * 60_000L,
            pi
        )
    }

    fun cancelRefresh(ctx: Context, id: Int, kind: WidgetKind) {
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarm.cancel(action(ctx, id, kind, ACTION_REFRESH))
    }

    private fun action(ctx: Context, id: Int, kind: WidgetKind, action: String): PendingIntent {
        val target = if (action in WidgetActionReceiver.ACTIONS) WidgetActionReceiver::class.java else providerClass(kind)
        val i = Intent(ctx, target).setAction(action)
            .setData(Uri.parse("azkar://widget/$id/$action"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(WidgetActionReceiver.EXTRA_KIND, kind.name)
        return PendingIntent.getBroadcast(ctx, id * 2 + (if (action == ACTION_COUNT) 0 else 1), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Opens the app straight into this folder's reader (MainActivity already handles the "open" extra). */
    private fun openApp(ctx: Context, id: Int, folder: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open", folder)
        return PendingIntent.getActivity(ctx, 100000 + id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
