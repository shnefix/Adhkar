package com.azkar.app.widgets

import android.content.Context
import com.azkar.app.data.ReaderItem
import com.azkar.app.data.Store
import android.text.format.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.min

enum class WidgetKind { SMALL, MEDIUM, LARGE }

private val ALL_KINDS = setOf(WidgetKind.SMALL, WidgetKind.MEDIUM, WidgetKind.LARGE)

/**
 * The four designs. [kinds] = sizes the renderer can draw (widgets already placed keep working);
 * [offered] = sizes the pickers still offer for new widgets.
 */
enum class WidgetStyle(val label: String, val kinds: Set<WidgetKind>, val offered: Set<WidgetKind> = kinds) {
    CLASSIC("Classic", ALL_KINDS, setOf(WidgetKind.SMALL)),
    CHECKLIST("Checklist", setOf(WidgetKind.MEDIUM, WidgetKind.LARGE), setOf(WidgetKind.MEDIUM)),
    TASBIH("Tasbih beads", setOf(WidgetKind.SMALL, WidgetKind.LARGE)),
    CALLIGRAPHY("Calligraphy", setOf(WidgetKind.SMALL, WidgetKind.MEDIUM));
}

/** Everything a renderer needs for one widget, already read from [Store]. */
class WidgetModel(
    val folderName: String,
    val items: List<ReaderItem>,
    val labels: List<String>,   // short left-hand label per zikr (its note, or "×N")
    val idx: Int,               // index of the zikr being recited; == n when the whole folder is finished
    val count: Int,             // recitations done on the current zikr
    val date: String,
) {
    val n = items.size
    val finished = n > 0 && idx >= n
    val curIdx = if (n == 0) 0 else min(idx, n - 1)
    val cur: ReaderItem? = items.getOrNull(curIdx)
    val target = cur?.target ?: 1
    val shown = if (finished) target else count.coerceIn(0, target)
    val done = if (finished) n else idx
    val position = if (n == 0) 0 else min(idx + 1, n)

    // Tasbih beads: one ring = up to 33 beads
    val beadTotal = min(33, target).coerceAtLeast(1)
    val beadFilled = when {
        shown == 0 -> 0
        shown % beadTotal == 0 -> beadTotal
        else -> shown % beadTotal
    }
    val round = if (shown == 0) 1 else (shown - 1) / beadTotal + 1
    val rounds = ceil(target / beadTotal.toDouble()).toInt().coerceAtLeast(1)

    companion object {
        fun load(ctx: Context, widgetId: Int, folder: String): WidgetModel =
            WidgetStore.progress(ctx, widgetId).let { (i, c) -> forProgress(folder, i, c) }

        /**
         * [folder] as far as it was read today anywhere (reader, or any widget's taps), for widgets that
         * show a folder they aren't counting themselves. [own] is this widget's progress when it counts [folder].
         */
        fun today(folder: String, own: WidgetModel? = null): WidgetModel {
            val done = com.azkar.app.data.Tracking.today(folder)?.done ?: 0
            return if (own != null && own.done >= done) own else forProgress(folder, done, 0)
        }

        private fun forProgress(folder: String, i: Int, c: Int): WidgetModel {
            val items = Store.readerItems(folder)
            val idx = i.coerceIn(0, items.size)
            val cnt = if (idx < items.size) c.coerceIn(0, (items[idx].target - 1).coerceAtLeast(0)) else 0
            val labels = items.map { it0 ->
                Store.zikr(it0.id)?.note?.trim()?.takeIf { s -> s.isNotEmpty() } ?: "×${it0.target}"
            }
            return WidgetModel(
                Store.folderName(folder), items, labels, idx, cnt,
                DateFormat.format("EEE, d MMM", Date()).toString()
            )
        }
    }
}
