package com.azkar.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
const val ACTION_STEP = "com.azkar.app.widget.STEP"   // finish the current zikr and show the next one
const val ACTION_REFRESH = "com.azkar.app.widget.REFRESH"
const val ACTION_KAHF = "com.azkar.app.widget.KAHF"   // Friday widget: Surat al-Kahf read / not read
val ACTION_COUNTERS = List(4) { "com.azkar.app.widget.COUNTER_$it" }   // counters widget: +1 on cell N
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
    fun updateAll(ctx: Context, except: Int = AppWidgetManager.INVALID_APPWIDGET_ID) {
        val mgr = AppWidgetManager.getInstance(ctx)
        WidgetKind.values().forEach { k ->
            mgr.getAppWidgetIds(ComponentName(ctx, providerClass(k))).forEach { if (it != except) update(ctx, mgr, it, k) }
        }
        NewWidgetUpdater.updateAll(ctx, except)
    }

    /** Real size of this widget on the launcher grid, in dp (falls back to the default size for its kind). */
    fun sizeDp(ctx: Context, mgr: AppWidgetManager, id: Int, kind: WidgetKind): Pair<Float, Float> =
        WidgetSizing.current(ctx, mgr.getAppWidgetOptions(id), DEFAULT_DP.getValue(kind)).let { it.width to it.height }

    /** Draws widget [id] exactly as the home screen shows it, but with [cfg] (e.g. an unapplied draft). */
    fun renderBitmap(ctx: Context, id: Int, kind: WidgetKind, cfg: WidgetStore.Config): android.graphics.Bitmap {
        val model = WidgetModel.load(ctx, id, cfg.folder)
        val (w, h) = sizeDp(ctx, AppWidgetManager.getInstance(ctx), id, kind)
        return WidgetRenderer(ctx).render(cfg.style, kind, model, w, h, ctx.resources.displayMetrics.density, cfg.appearance)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, kind: WidgetKind) {
        val cfg = WidgetStore.config(ctx, id, kind)
        val model = WidgetModel.load(ctx, id, cfg.folder)
        val options = mgr.getAppWidgetOptions(id)
        val plan = WidgetSizing.plan(ctx, options, DEFAULT_DP.getValue(kind), id)
        // The card is a picture of its own, stretched to fill: exact when the launcher gave sizes, else its best guess.
        val guess = WidgetSizing.current(ctx, options, DEFAULT_DP.getValue(kind))
        val maxPixels = WidgetSizing.maxPixels(ctx, plan.sizes.size * 2)
        val density = ctx.resources.displayMetrics.density
        val renderer = WidgetRenderer(ctx)

        val layout = when {
            cfg.style == WidgetStyle.CLASSIC && kind == WidgetKind.LARGE -> R.layout.widget_classic_large
            cfg.style == WidgetStyle.TASBIH && kind == WidgetKind.LARGE -> R.layout.widget_tasbih_large
            else -> R.layout.widget_plain
        }
        val description = if (model.n == 0) ctx.getString(R.string.widget_no_azkar_description)
            else "${model.folderName}: " + ctx.getString(R.string.widget_count_of, model.shown, model.target)
        val primary = openApp(ctx, id, cfg.folder)
        val click = when (cfg.appearance.tapAction) {
            WidgetStore.TapAction.INCREMENT -> action(ctx, id, kind, ACTION_COUNT)
            WidgetStore.TapAction.NEXT_ZIKR -> action(ctx, id, kind, ACTION_NEXT)
            WidgetStore.TapAction.OPEN_APP, WidgetStore.TapAction.OPEN_READER -> primary
        }
        val reset = action(ctx, id, kind, ACTION_RESET)

        val rv = WidgetSizing.views(plan.sizes) { size ->
            RemoteViews(ctx.packageName, layout).apply {
                val card = if (plan.exact) size else guess
                setImageViewBitmap(R.id.img_bg, renderer.renderCard(card.width, card.height, density, cfg.appearance, maxPixels))
                val picture = renderer.render(
                    cfg.style, kind, model, size.width, size.height, density * plan.scale, cfg.appearance, maxPixels, withCard = false
                )
                setImageViewBitmap(R.id.img, WidgetSizing.prepare(ctx, picture))
                setContentDescription(R.id.img, description)
                setContentDescription(R.id.hit_open, ctx.getString(R.string.widget_action_open))
                setContentDescription(R.id.hit_count, ctx.getString(R.string.widget_action_count))
                setOnClickPendingIntent(R.id.hit_open, primary)
                setOnClickPendingIntent(R.id.hit_count, click)
                setOnClickPendingIntent(R.id.hit_bg, click)
                if (layout == R.layout.widget_classic_large) {
                    setContentDescription(R.id.hit_count2, ctx.getString(R.string.widget_action_count))
                    setContentDescription(R.id.btn_plus, ctx.getString(R.string.widget_action_count))
                    setOnClickPendingIntent(R.id.hit_count2, click)
                    setOnClickPendingIntent(R.id.btn_plus, click)
                }
                if (layout == R.layout.widget_tasbih_large) {
                    setContentDescription(R.id.btn_main, ctx.getString(R.string.widget_action_count))
                    setContentDescription(R.id.btn_reset, ctx.getString(R.string.widget_action_reset))
                    setOnClickPendingIntent(R.id.btn_main, click)
                    setOnClickPendingIntent(R.id.btn_reset, reset)
                }
            }
        }
        mgr.updateAppWidget(id, rv)
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
