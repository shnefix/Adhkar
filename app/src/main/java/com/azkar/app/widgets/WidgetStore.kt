package com.azkar.app.widgets

import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import com.azkar.app.data.Store
import com.azkar.app.R
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Per-widget settings and progress. Progress resets itself every new day. */
object WidgetStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("azkar_widgets", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    enum class Refresh(val minutes: Long, val label: Int) {
        ON_CHANGE(0, R.string.widget_refresh_on_change),
        HOURLY(60, R.string.widget_refresh_hourly),
        EVERY_SIX_HOURS(360, R.string.widget_refresh_six_hours),
        DAILY(1440, R.string.widget_refresh_daily)
    }

    enum class TapAction(val label: Int) {
        OPEN(R.string.widget_tap_open), COUNT(R.string.widget_tap_count)
    }

    data class Config(
        val style: WidgetStyle,
        val folder: String,
        val theme: Int?,
        val refresh: Refresh,
        val tapAction: TapAction
    )

    fun config(ctx: Context, id: Int, kind: WidgetKind): Config {
        val s = sp(ctx)
        val savedStyle = s.getString("style_$id", null)
        val style = try {
            savedStyle?.let(WidgetStyle::valueOf)
        } catch (e: IllegalArgumentException) {
            Log.w("WidgetStore", "Invalid widget style '$savedStyle' for $id", e)
            null
        }
            ?.takeIf { kind in it.kinds } ?: WidgetStyle.CLASSIC
        val folder = s.getString("folder_$id", null) ?: Store.folders().firstOrNull()?.id ?: "anytime"
        val theme = if (s.contains("theme_$id")) s.getInt("theme_$id", com.azkar.app.ThemeMode.CURRENT) else null
        val refresh = runCatching { Refresh.valueOf(s.getString("refresh_$id", Refresh.ON_CHANGE.name)!!) }
            .getOrDefault(Refresh.ON_CHANGE)
        val tap = runCatching { TapAction.valueOf(s.getString("tap_$id", TapAction.OPEN.name)!!) }
            .getOrDefault(TapAction.OPEN)
        return Config(style, folder, theme, refresh, tap)
    }

    fun setConfig(
        ctx: Context,
        id: Int,
        style: WidgetStyle,
        folder: String,
        theme: Int?,
        refresh: Refresh = Refresh.ON_CHANGE,
        tapAction: TapAction = TapAction.OPEN
    ) {
        val edit = sp(ctx).edit()
            .putString("style_$id", style.name)
            .putString("folder_$id", folder)
            .putString("refresh_$id", refresh.name)
            .putString("tap_$id", tapAction.name)
        if (theme == null) edit.remove("theme_$id") else edit.putInt("theme_$id", theme)
        edit.commit()
        saveProgress(ctx, id, 0, 0)
        scheduleRefresh(ctx, id, refresh)
    }

    /** (index, count) for today; (0, 0) if the saved progress is from a previous day. */
    fun progress(ctx: Context, id: Int): Pair<Int, Int> {
        val s = sp(ctx)
        if (s.getString("day_$id", "") != today()) return 0 to 0
        return s.getInt("idx_$id", 0) to s.getInt("cnt_$id", 0)
    }

    fun saveProgress(ctx: Context, id: Int, idx: Int, count: Int) {
        sp(ctx).edit().putString("day_$id", today()).putInt("idx_$id", idx).putInt("cnt_$id", count).apply()
    }

    fun clear(ctx: Context, id: Int) {
        cancelRefresh(ctx, id)
        sp(ctx).edit().remove("style_$id").remove("folder_$id").remove("theme_$id").remove("day_$id").remove("idx_$id").remove("cnt_$id").apply()
    }

    fun scheduleRefresh(ctx: Context, id: Int, refresh: Refresh) {
        cancelRefresh(ctx, id)
        if (refresh == Refresh.ON_CHANGE) return
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val pi = refreshPending(ctx, id, true)!!
        alarm.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + refresh.minutes * 60_000L,
            refresh.minutes * 60_000L,
            pi
        )
    }

    fun cancelRefresh(ctx: Context, id: Int) {
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        refreshPending(ctx, id)?.let { alarm.cancel(it); it.cancel() }
    }

    private fun refreshPending(ctx: Context, id: Int, create: Boolean = false): PendingIntent? =
        PendingIntent.getBroadcast(
            ctx, id, refreshIntent(ctx, id),
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or PendingIntent.FLAG_IMMUTABLE
        )

    private fun refreshIntent(ctx: Context, id: Int): Intent =
        Intent(ctx, WidgetRefreshReceiver::class.java).setData(Uri.parse("azkar://widget-refresh/$id"))
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
}

/** What a tap does. */
object WidgetLogic {
    /** +1 on the current zikr; moves on when it is complete; after the last one the next tap starts over. */
    fun tap(ctx: Context, id: Int, kind: WidgetKind) {
        val cfg = WidgetStore.config(ctx, id, kind)
        val items = Store.readerItems(cfg.folder)
        if (items.isEmpty()) return
        var (idx, cnt) = WidgetStore.progress(ctx, id)
        idx = idx.coerceIn(0, items.size)
        if (idx >= items.size) { idx = 0; cnt = 0 }
        else {
            cnt++
            if (cnt >= items[idx].target) { idx++; cnt = 0 }
        }
        WidgetStore.saveProgress(ctx, id, idx, cnt)
    }

    fun reset(ctx: Context, id: Int) = WidgetStore.saveProgress(ctx, id, 0, 0)
}
