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
import com.azkar.app.data.Store

const val ACTION_COUNT = "com.azkar.app.widget.COUNT"
const val ACTION_RESET = "com.azkar.app.widget.RESET"

abstract class BaseWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Store.init(ctx)
        ids.forEach { WidgetUpdater.update(ctx, mgr, it, kind) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        Store.init(ctx)
        WidgetUpdater.update(ctx, mgr, id, kind)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { WidgetStore.clear(ctx, it) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_COUNT, ACTION_RESET -> {
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                Store.init(ctx)
                if (intent.action == ACTION_COUNT) WidgetLogic.tap(ctx, id, kind) else WidgetLogic.reset(ctx, id)
                WidgetUpdater.update(ctx, AppWidgetManager.getInstance(ctx), id, kind)
            }
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
        Store.init(ctx)
        val mgr = AppWidgetManager.getInstance(ctx)
        WidgetKind.values().forEach { k ->
            mgr.getAppWidgetIds(ComponentName(ctx, providerClass(k))).forEach { update(ctx, mgr, it, k) }
        }
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

        val bmp = WidgetRenderer(ctx).render(cfg.style, kind, model, w, h, ctx.resources.displayMetrics.density)

        val layout = when {
            cfg.style == WidgetStyle.CLASSIC && kind == WidgetKind.LARGE -> R.layout.widget_classic_large
            cfg.style == WidgetStyle.TASBIH && kind == WidgetKind.LARGE -> R.layout.widget_tasbih_large
            else -> R.layout.widget_plain
        }
        val rv = RemoteViews(ctx.packageName, layout)
        rv.setImageViewBitmap(R.id.img, bmp)
        rv.setContentDescription(
            R.id.img,
            if (model.n == 0) "Azkar widget, no azkar" else "${model.folderName}: ${model.shown} of ${model.target}"
        )

        rv.setOnClickPendingIntent(R.id.hit_open, openApp(ctx, id, cfg.folder))
        val count = action(ctx, id, kind, ACTION_COUNT)
        rv.setOnClickPendingIntent(R.id.hit_count, count)
        if (layout == R.layout.widget_classic_large) {
            rv.setOnClickPendingIntent(R.id.hit_count2, count)
            rv.setOnClickPendingIntent(R.id.btn_plus, count)
        }
        if (layout == R.layout.widget_tasbih_large) {
            rv.setOnClickPendingIntent(R.id.btn_main, count)
            rv.setOnClickPendingIntent(R.id.btn_reset, action(ctx, id, kind, ACTION_RESET))
        }
        mgr.updateAppWidget(id, rv)
    }

    private fun action(ctx: Context, id: Int, kind: WidgetKind, action: String): PendingIntent {
        val i = Intent(ctx, providerClass(kind)).setAction(action)
            .setData(Uri.parse("azkar://widget/$id/$action"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
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
