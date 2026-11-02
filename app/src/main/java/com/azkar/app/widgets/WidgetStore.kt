package com.azkar.app.widgets

import android.content.Context
import com.azkar.app.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Per-widget settings and progress. Progress resets itself every new day. */
object WidgetStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("azkar_widgets", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    data class Config(val style: WidgetStyle, val folder: String)

    fun config(ctx: Context, id: Int, kind: WidgetKind): Config {
        val s = sp(ctx)
        val style = runCatching { WidgetStyle.valueOf(s.getString("style_$id", "") ?: "") }.getOrNull()
            ?.takeIf { kind in it.kinds } ?: WidgetStyle.CLASSIC
        val folder = s.getString("folder_$id", null) ?: Store.folders().firstOrNull()?.id ?: "anytime"
        return Config(style, folder)
    }

    fun setConfig(ctx: Context, id: Int, style: WidgetStyle, folder: String) {
        sp(ctx).edit().putString("style_$id", style.name).putString("folder_$id", folder).apply()
        saveProgress(ctx, id, 0, 0)
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
        sp(ctx).edit().remove("style_$id").remove("folder_$id").remove("day_$id").remove("idx_$id").remove("cnt_$id").apply()
    }
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
