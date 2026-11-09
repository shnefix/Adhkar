package com.azkar.app.widgets

import android.content.Context
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.ReaderItem
import com.azkar.app.data.Store
import com.azkar.app.data.Tracking
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Per-widget settings and progress. Progress resets itself every new day. */
object WidgetStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("azkar_widgets", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    data class Config(
        val style: WidgetStyle,
        val folder: String,
        val appearance: Appearance = Appearance()
    )

    data class Appearance(
        val fontSizeSp: Float = 28f,
        val lineHeight: Float = 1.5f,
        val alignment: Int = 1,
        val textColor: Int = 0,
        val backgroundColor: Int = 0,
        val cornerRadiusDp: Float = 22f,
        val showCounter: Boolean = true,
        val tapAction: TapAction = TapAction.INCREMENT,
        val refreshMinutes: Int = 60
    )

    enum class TapAction { INCREMENT, OPEN_APP, OPEN_READER, NEXT_ZIKR }

    fun savePendingPin(ctx: Context, kind: WidgetKind, style: WidgetStyle) {
        sp(ctx).edit()
            .putString("pending_pin_kind", kind.name)
            .putString("pending_pin_style", style.name)
            .apply()
    }

    fun consumePendingPin(ctx: Context): Pair<WidgetKind, WidgetStyle>? {
        val prefs = sp(ctx)
        val kind = runCatching { WidgetKind.valueOf(prefs.getString("pending_pin_kind", "") ?: "") }.getOrNull()
        val style = runCatching { WidgetStyle.valueOf(prefs.getString("pending_pin_style", "") ?: "") }.getOrNull()
        clearPendingPin(ctx)
        return if (kind != null && style != null) kind to style else null
    }

    fun clearPendingPin(ctx: Context) {
        sp(ctx).edit().remove("pending_pin_kind").remove("pending_pin_style").apply()
    }

    fun config(ctx: Context, id: Int, kind: WidgetKind): Config {
        val s = sp(ctx)
        val savedStyle = s.getString("style_$id", null)
        val style = try {
            savedStyle?.let(WidgetStyle::valueOf)
        } catch (e: IllegalArgumentException) {
            Log.w("WidgetStore", "Invalid widget style '$savedStyle' for $id", e)
            null
        }
            ?.takeIf { kind in it.kinds } ?: defaultStyle(kind)
        val folder = s.getString("folder_$id", null) ?: Store.folders().firstOrNull()?.id ?: Store.ANYTIME
        return Config(style, folder, Appearance(
            fontSizeSp = s.getFloat("font_size_$id", 28f),
            lineHeight = s.getFloat("line_height_$id", 1.5f),
            alignment = s.getInt("alignment_$id", 1),
            textColor = s.getInt("text_color_$id", 0),
            backgroundColor = s.getInt("background_color_$id", 0),
            cornerRadiusDp = s.getFloat("corner_$id", 22f),
            showCounter = s.getBoolean("counter_$id", true),
            tapAction = runCatching { TapAction.valueOf(s.getString("tap_$id", TapAction.INCREMENT.name)!!) }
                .getOrDefault(TapAction.INCREMENT),
            refreshMinutes = s.getInt("refresh_$id", 60)
        ))
    }

    /** Sets the refresh interval a widget starts with, unless the user already chose one. */
    fun setDefaultRefresh(ctx: Context, id: Int, minutes: Int) {
        if (!sp(ctx).contains("refresh_$id")) sp(ctx).edit().putInt("refresh_$id", minutes).apply()
    }

    fun setConfig(
        ctx: Context,
        id: Int,
        style: WidgetStyle,
        folder: String,
        appearance: Appearance = config(ctx, id, kindFor(style)).appearance
    ) {
        val edit = sp(ctx).edit()
            .putString("style_$id", style.name)
            .putString("folder_$id", folder)
            .putFloat("font_size_$id", appearance.fontSizeSp)
            .putFloat("line_height_$id", appearance.lineHeight)
            .putInt("alignment_$id", appearance.alignment)
            .putInt("text_color_$id", appearance.textColor)
            .putInt("background_color_$id", appearance.backgroundColor)
            .putFloat("corner_$id", appearance.cornerRadiusDp)
            .putBoolean("counter_$id", appearance.showCounter)
            .putString("tap_$id", appearance.tapAction.name)
            .putInt("refresh_$id", appearance.refreshMinutes)
        edit.apply()   // same process: readers see the new values at once
        saveProgress(ctx, id, 0, 0)
    }

    /** Style a widget of [kind] gets before (or without) a saved choice: the first one the pickers offer. */
    fun defaultStyle(kind: WidgetKind): WidgetStyle =
        WidgetStyle.values().firstOrNull { kind in it.offered } ?: WidgetStyle.CLASSIC

    private fun kindFor(style: WidgetStyle): WidgetKind =
        style.kinds.firstOrNull() ?: WidgetKind.SMALL

    /** (index, count) for today; (0, 0) if the saved progress is from a previous day. */
    fun progress(ctx: Context, id: Int): Pair<Int, Int> {
        val s = sp(ctx)
        if (s.getString("day_$id", "") != today()) return 0 to 0
        return s.getInt("idx_$id", 0) to s.getInt("cnt_$id", 0)
    }

    fun saveProgress(ctx: Context, id: Int, idx: Int, count: Int) {
        sp(ctx).edit().putString("day_$id", today()).putInt("idx_$id", idx).putInt("cnt_$id", count).apply()
    }

    /**
     * Today's position for every widget that shows [folder]: widgets follow the reader and each other.
     * [idx] / [count] use the widget's meaning: [idx] azkar are finished, [count] taps into the next one.
     */
    fun syncFolderProgress(ctx: Context, folder: String, idx: Int, count: Int) {
        val s = sp(ctx)
        val ids = s.all.mapNotNull { (k, v) -> if (k.startsWith("folder_") && v == folder) k.removePrefix("folder_").toIntOrNull() else null }
        if (ids.isEmpty()) return
        val e = s.edit()
        val day = today()
        ids.forEach { e.putString("day_$it", day).putInt("idx_$it", idx).putInt("cnt_$it", count) }
        e.apply()
    }

    fun clear(ctx: Context, id: Int) {
        sp(ctx).edit().remove("style_$id").remove("folder_$id")
            .remove("font_size_$id").remove("line_height_$id").remove("alignment_$id")
            .remove("text_color_$id").remove("background_color_$id").remove("corner_$id")
            .remove("counter_$id").remove("tap_$id").remove("refresh_$id")
            .remove("day_$id").remove("idx_$id").remove("cnt_$id").apply()
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
        share(ctx, id, cfg.folder, items, idx, cnt)
        Tracking.record(cfg.folder, idx, items.size)   // no-op unless the folder is tracked
    }

    fun reset(ctx: Context, id: Int, kind: WidgetKind) {
        val cfg = WidgetStore.config(ctx, id, kind)
        share(ctx, id, cfg.folder, Store.readerItems(cfg.folder), 0, 0)
    }

    /**
     * Saves a widget's new position and shares it: other widgets on the same folder move with it, and so does
     * where the reader resumes, for folders that remember their position.
     */
    private fun share(ctx: Context, id: Int, folder: String, items: List<ReaderItem>, idx: Int, cnt: Int) {
        WidgetStore.saveProgress(ctx, id, idx, cnt)
        WidgetStore.syncFolderProgress(ctx, folder, idx, cnt)
        if (!Store.rememberPosition(folder)) return
        val current = items.getOrNull(idx)
        if (current == null) AppPrefs.clearPosition(folder) else AppPrefs.savePosition(folder, current.id, cnt)
    }

    fun undo(ctx: Context, id: Int, kind: WidgetKind) {
        val cfg = WidgetStore.config(ctx, id, kind)
        val items = Store.readerItems(cfg.folder)
        if (items.isEmpty()) return
        var (idx, cnt) = WidgetStore.progress(ctx, id)
        if (idx >= items.size) {
            idx = (items.size - 1).coerceAtLeast(0)
            cnt = items[idx].target
        }
        if (cnt > 0) cnt-- else if (idx > 0) {
            idx--
            cnt = (items[idx].target - 1).coerceAtLeast(0)
        }
        share(ctx, id, cfg.folder, items, idx, cnt)
    }

    /** One tap = one whole zikr: the next zikr comes up (after the last, the folder starts again). */
    fun step(ctx: Context, id: Int, kind: WidgetKind) {
        val cfg = WidgetStore.config(ctx, id, kind)
        val items = Store.readerItems(cfg.folder)
        if (items.isEmpty()) return
        val (idx, _) = WidgetStore.progress(ctx, id)
        val nextIdx = if (idx >= items.size) 0 else idx + 1
        share(ctx, id, cfg.folder, items, nextIdx, 0)
        Tracking.record(cfg.folder, nextIdx, items.size)   // no-op unless the folder is tracked
    }

    fun next(ctx: Context, id: Int, kind: WidgetKind) {
        val cfg = WidgetStore.config(ctx, id, kind)
        val items = Store.readerItems(cfg.folder)
        if (items.isEmpty()) return
        val (idx, _) = WidgetStore.progress(ctx, id)
        share(ctx, id, cfg.folder, items, (idx + 1).coerceAtMost(items.size), 0)
    }
}
