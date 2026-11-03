package com.azkar.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.azkar.app.R
import com.azkar.app.data.Store
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Kept for the existing unit tests.
object WidgetRenderMath {
    fun ringSweep(fraction: Float): Float = 360f * fraction.coerceIn(0f, 1f)
    fun beadDiameter(index: Int, focusedIndex: Int): Float = if (index == focusedIndex) 30f else 16f
    fun barFillHeight(top: Float, bottom: Float, fraction: Float): Float =
        (bottom - top) * fraction.coerceIn(0f, 1f)
}

object AzkarSessionClock {
    fun activeSession(context: Context, now: Calendar = Calendar.getInstance()): String {
        val schedules = Store.schedules()
        val morning = schedules.firstOrNull { it.open == "morning" }?.hour ?: 6
        val evening = schedules.firstOrNull { it.open == "evening" }?.hour ?: 17
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return if (hour >= morning && hour < evening) "MORNING" else "EVENING"
    }
}

/** Design sizes in dp (from the Compose spec). Used when the launcher reports no size and for previews. */
object AzkarWidgetSizes {
    fun design(f: AzkarWidgetFamily): Pair<Float, Float> = when (f) {
        AzkarWidgetFamily.H_SMALL, AzkarWidgetFamily.I_SMALL,
        AzkarWidgetFamily.D_SMALL, AzkarWidgetFamily.B_SMALL -> 158f to 158f
        AzkarWidgetFamily.H_MEDIUM, AzkarWidgetFamily.I_MEDIUM,
        AzkarWidgetFamily.D_MEDIUM, AzkarWidgetFamily.B_MEDIUM -> 338f to 158f
        AzkarWidgetFamily.H_LARGE, AzkarWidgetFamily.I_LARGE,
        AzkarWidgetFamily.D_LARGE, AzkarWidgetFamily.B_LARGE -> 338f to 354f
        AzkarWidgetFamily.J_STRIP_DHIKR, AzkarWidgetFamily.J_STRIP_PROGRESS,
        AzkarWidgetFamily.J_STRIP_DOTS, AzkarWidgetFamily.J_STRIP_NEXT -> 338f to 74f
        AzkarWidgetFamily.J_TALL_RING, AzkarWidgetFamily.J_TALL_TIMELINE -> 158f to 354f
    }
}

/**
 * Draws the 18 OLED widgets. All coordinates are dp (canvas is scaled), laid out relative to the
 * real widget width/height so nothing is stretched. Arabic uses StaticLayout (RTL, shrink to fit).
 */
class NewWidgetRenderer(private val ctx: Context) {
    private val gold = ctx.getColor(R.color.azkar_widget_gold)
    private val goldSoft = ctx.getColor(R.color.azkar_widget_gold_soft)
    private val haloC = ctx.getColor(R.color.azkar_widget_gold_halo)
    private val cream = ctx.getColor(R.color.azkar_widget_cream)
    private val muted = ctx.getColor(R.color.azkar_widget_muted)
    private val track = ctx.getColor(R.color.azkar_widget_track)
    private val ink = ctx.getColor(R.color.azkar_widget_ink)
    private val cardBorder = ctx.getColor(R.color.azkar_widget_card_border)
    private val inner = ctx.getColor(R.color.azkar_widget_inner)
    private val innerBorder = ctx.getColor(R.color.azkar_widget_inner_border)
    private val outline = ctx.getColor(R.color.azkar_widget_outline)
    private val chipC = ctx.getColor(R.color.azkar_widget_chip)
    private val beadOff = ctx.getColor(R.color.azkar_widget_bead_off)

    private val amiriBold = ResourcesCompat.getFont(ctx, R.font.amiri_bold) ?: Typeface.SERIF
    private val amiriReg = ResourcesCompat.getFont(ctx, R.font.amiri_regular) ?: Typeface.SERIF
    private val dmBold = ResourcesCompat.getFont(ctx, R.font.dm_sans_bold) ?: Typeface.DEFAULT_BOLD
    private val dmReg = ResourcesCompat.getFont(ctx, R.font.dm_sans_regular) ?: Typeface.DEFAULT

    // Letter-spacing breaks Arabic letter joining, so it is disabled in Arabic UI.
    private val arabicUi = ctx.resources.configuration.locales[0].language == "ar"

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val ap = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private fun str(id: Int, vararg a: Any): String = ctx.getString(id, *a)

    // ───────────────────────── entry ─────────────────────────

    fun render(family: AzkarWidgetFamily, m: WidgetModel, widthDp: Float, heightDp: Float): Bitmap {
        val w = widthDp.coerceAtLeast(1f)
        val h = heightDp.coerceAtLeast(1f)
        var s = ctx.resources.displayMetrics.density
        val px = w * h * s * s
        if (px > MAX_PIXELS) s *= sqrt(MAX_PIXELS / px) // RemoteViews bitmaps must stay small
        val bmp = Bitmap.createBitmap(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        card(c, w, h)
        if (m.n == 0) { empty(c, w, h); return bmp }
        when (family) {
            AzkarWidgetFamily.B_SMALL -> discSmall(c, w, h, m)
            AzkarWidgetFamily.B_MEDIUM -> discMedium(c, w, h, m)
            AzkarWidgetFamily.B_LARGE -> discLarge(c, w, h, m)
            AzkarWidgetFamily.D_SMALL -> sessionsSmall(c, w, h, m)
            AzkarWidgetFamily.D_MEDIUM -> sessionsMedium(c, w, h, m)
            AzkarWidgetFamily.D_LARGE -> sessionsLarge(c, w, h, m)
            AzkarWidgetFamily.H_SMALL -> beadsSmall(c, w, h, m)
            AzkarWidgetFamily.H_MEDIUM -> beadsMedium(c, w, h, m)
            AzkarWidgetFamily.H_LARGE -> beadsLarge(c, w, h, m)
            AzkarWidgetFamily.I_SMALL -> barsSmall(c, w, h, m)
            AzkarWidgetFamily.I_MEDIUM -> barsMedium(c, w, h, m)
            AzkarWidgetFamily.I_LARGE -> barsLarge(c, w, h, m)
            AzkarWidgetFamily.J_STRIP_DHIKR -> stripDhikr(c, w, h, m)
            AzkarWidgetFamily.J_STRIP_PROGRESS -> stripProgress(c, w, h, m)
            AzkarWidgetFamily.J_STRIP_DOTS -> stripDots(c, w, h, m)
            AzkarWidgetFamily.J_STRIP_NEXT -> stripNext(c, w, h, m)
            AzkarWidgetFamily.J_TALL_RING -> tallRing(c, w, h, m)
            AzkarWidgetFamily.J_TALL_TIMELINE -> tallTimeline(c, w, h, m)
        }
        return bmp
    }

    private fun empty(c: Canvas, w: Float, h: Float) {
        text(c, str(R.string.widget_no_azkar), w / 2, h / 2 - 8, 13f, cream, dmBold, Paint.Align.CENTER, 0f, w - 30)
        text(c, str(R.string.widget_tap_to_open), w / 2, h / 2 + 10, 11f, muted, dmReg, Paint.Align.CENTER, 0f, w - 30)
    }

    // ───────────────────────── B · tap disc ─────────────────────────

    private fun discSmall(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val k = min(w, h) / 158f
        val cx = w / 2; val cy = h / 2
        disc(c, cx, cy, 58f * k, 6f * k, 46f * k, frac(m))
        text(c, "${m.shown}", cx, cy - 3f * k, 34f * k, cream, dmBold, Paint.Align.CENTER)
        text(c, str(R.string.widget_tap_caps), cx, cy + 20f * k, 9f * k, gold, dmBold, Paint.Align.CENTER, spaced(0.14f))
    }

    private fun discMedium(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val k = (h / 158f).coerceIn(0.7f, 1.2f)
        val l = 16f; val r = w - 16f; val cy = h / 2
        val cx = l + 56f * k
        disc(c, cx, cy, 50f * k, 6f * k, 39f * k, frac(m))
        text(c, "${m.shown}", cx, cy - 5f * k, 28f * k, cream, dmBold, Paint.Align.CENTER)
        text(c, str(R.string.widget_of, m.target), cx, cy + 15f * k, 9f * k, muted, dmReg, Paint.Align.CENTER)
        val tx = l + 112f * k + 16f
        eyebrow(c, "${m.folderName} · ${str(R.string.widget_count_of, m.position, m.n)}", tx, 16f + 5f, Paint.Align.LEFT, maxW = r - tx)
        arabicBox(c, m.cur!!.arabic, tx, cy - 26f * k, r, cy + 22f * k, 26f * k, cream, Layout.Alignment.ALIGN_NORMAL)
        caption(c, str(R.string.widget_next_label, nextLabel(m)), tx, h - 16f - 5f, Paint.Align.LEFT, maxW = r - tx)
    }

    private fun discLarge(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val l = 18f; val r = w - 18f; val t = 18f; val b = h - 18f
        header(c, str(R.string.widget_counter), "${m.folderName} · ${str(R.string.widget_count_of, m.position, m.n)}", "${m.shown} / ${m.target}", l, r, t)
        val btnTop = b - 44f
        val top = t + 44f; val bot = btnTop - 12f
        val d = min(min(bot - top, w - 36f), 200f)
        val s = d / 200f
        val cx = w / 2; val cy = (top + bot) / 2
        disc(c, cx, cy, 92f * s, 8f * s, 76f * s, frac(m))
        arabicBox(c, m.cur!!.arabic, cx - 62f * s, cy - 56f * s, cx + 62f * s, cy - 12f * s, 26f * s, goldSoft)
        text(c, "${m.shown}", cx, cy + 14f * s, 46f * s, cream, dmBold, Paint.Align.CENTER)
        caption(c, str(R.string.widget_tap_hint), cx, cy + 46f * s, Paint.Align.CENTER)
        val x1 = l + 56f + 10f
        val each = ((r - x1) - 10f) / 2f
        ghost(c, RectF(l, btnTop, l + 56f, b), "−1", 15f)
        ghost(c, RectF(x1, btnTop, x1 + each, b), str(R.string.widget_reset))
        solid(c, RectF(x1 + each + 10f, btnTop, r, b), str(R.string.widget_next_dhikr), 13f)
    }

    private fun disc(c: Canvas, cx: Float, cy: Float, r: Float, sw: Float, innerR: Float, f: Float) {
        ring(c, cx, cy, r, sw, f)
        c.drawCircle(cx, cy, innerR, fill(inner))
        c.drawCircle(cx, cy, innerR, stroke(innerBorder, 1f))
    }

    // ───────────────────────── D · morning & evening ─────────────────────────

    private fun sessionModel(m: WidgetModel, keys: List<String>): WidgetModel {
        val f = Store.folders().firstOrNull { f -> keys.any { f.name.contains(it, ignoreCase = true) } } ?: return m
        return if (f.name == m.folderName) m else WidgetModel.load(ctx, AppWidgetManager.INVALID_APPWIDGET_ID, f.id)
    }

    private val morningKeys = listOf("morning", "صباح")
    private val eveningKeys = listOf("evening", "مساء")
    private fun morningActive() = AzkarSessionClock.activeSession(ctx) == "MORNING"

    private fun sessionsSmall(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val k = min(w, h) / 158f
        eyebrow(c, str(R.string.widget_today), w / 2, 14f + 5f, Paint.Align.CENTER)
        val total = 56f * k * 2 + 18f * k
        val left = (w - total) / 2
        val ringCy = h / 2 - 3f * k
        val morning = sessionModel(m, morningKeys)
        val evening = sessionModel(m, eveningKeys)
        smallSession(c, left + 28f * k, ringCy, k, morning, str(R.string.widget_morning))
        smallSession(c, left + 56f * k + 18f * k + 28f * k, ringCy, k, evening, str(R.string.widget_evening))
    }

    private fun smallSession(c: Canvas, cx: Float, cy: Float, k: Float, s: WidgetModel, label: String) {
        ring(c, cx, cy, 24f * k, 5f * k, overall(s))
        if (s.finished) check(c, cx, cy, 18f * k, gold)
        else text(c, "${s.done}", cx, cy, 18f * k, cream, dmBold, Paint.Align.CENTER)
        caption(c, label, cx, cy + 28f * k + 13f * k, Paint.Align.CENTER, 10f * k)
    }

    private fun sessionsMedium(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val pad = 12f
        val cw = (w - pad * 2 - 10f) / 2
        val morning = sessionModel(m, morningKeys)
        val evening = sessionModel(m, eveningKeys)
        val am = morningActive()
        miniCard(c, RectF(pad, pad, pad + cw, h - pad), str(R.string.widget_morning), morning, am)
        miniCard(c, RectF(pad + cw + 10f, pad, w - pad, h - pad), str(R.string.widget_evening), evening, !am)
    }

    private fun miniCard(c: Canvas, r: RectF, title: String, s: WidgetModel, active: Boolean) {
        panel(c, r, 18f, active)
        val l = r.left + 12f; val rr = r.right - 12f; val t = r.top + 12f; val b = r.bottom - 12f
        eyebrow(c, title, l, t + 5f, Paint.Align.LEFT, if (active) gold else muted, 0.1f, rr - l - 28f)
        if (s.finished) check(c, rr - 7f, t + 5f, 14f, gold)
        else if (active) text(c, str(R.string.widget_now), rr, t + 5f, 10f, gold, dmReg, Paint.Align.RIGHT)
        val cy = (r.top + r.bottom) / 2
        bigWithSuffix(c, l, cy, "${s.done}", "/ ${s.n}", 34f, 12f)
        bar(c, l, b - 16f, rr, 4f, overall(s))
        caption(c, if (s.finished) str(R.string.widget_completed) else str(R.string.widget_next_label, nextLabel(s)), l, b - 5f, Paint.Align.LEFT, 10f, maxW = rr - l)
    }

    private fun sessionsLarge(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val pad = 16f
        val ch = (h - pad * 2 - 10f) / 2
        val am = morningActive()
        bigCard(c, RectF(pad, pad, w - pad, pad + ch), str(R.string.widget_morning_azkar), sessionModel(m, morningKeys), am)
        bigCard(c, RectF(pad, pad + ch + 10f, w - pad, h - pad), str(R.string.widget_evening_azkar), sessionModel(m, eveningKeys), !am)
    }

    private fun bigCard(c: Canvas, r: RectF, title: String, s: WidgetModel, active: Boolean) {
        panel(c, r, 20f, active)
        val l = r.left + 14f; val rr = r.right - 14f; val t = r.top + 14f; val b = r.bottom - 14f
        eyebrow(c, title, l, t + 6f, Paint.Align.LEFT, if (active) gold else muted, 0.1f, rr - l - 70f)
        if (s.finished) {
            check(c, rr - 40f, t + 6f, 12f, gold)
            text(c, str(R.string.widget_done), rr, t + 6f, 11f, gold, dmBold, Paint.Align.RIGHT)
        } else chip(c, "${s.done} / ${s.n}", rr, t - 4f)
        val arabicColor = if (s.finished) muted else cream
        arabicBox(c, s.cur?.arabic ?: "", l, t + 20f, rr, b - 26f, 22f, arabicColor)
        bar(c, l, b - 16f, rr, 4f, overall(s))
        caption(c, str(R.string.widget_recited, s.done, s.n), l, b - 5f, Paint.Align.LEFT, maxW = rr - l, color = if (active) cream else muted)
    }

    // ───────────────────────── H · bead string ─────────────────────────

    private fun beadsSmall(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val k = min(w, h) / 158f
        eyebrow(c, str(R.string.widget_tasbih), w / 2, 14f + 5f, Paint.Align.CENTER)
        val rowW = 130f * k
        val l = (w - rowW) / 2
        beadRow(c, l, l + rowW, h / 2, listOf(20f * k, 54f * k, 20f * k), 1, 4f * k, "${m.beadFilled}", 19f * k)
        caption(c, str(R.string.widget_of_tap, m.beadTotal), w / 2, h - 14f - 5f, Paint.Align.CENTER)
    }

    private fun beadsMedium(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val k = (h / 158f).coerceIn(0.75f, 1.2f)
        val l = 16f; val r = w - 16f; val t = 16f; val b = h - 16f
        eyebrow(c, "${str(R.string.widget_tasbih)} · ${str(R.string.widget_round_short, m.round, m.rounds)}", l, t + 5f, Paint.Align.LEFT, maxW = r - l - 60f)
        caption(c, "${m.shown} / ${m.target}", r, t + 5f, Paint.Align.RIGHT)
        val sizes = listOf(14f, 20f, 28f, 52f, 28f, 20f, 14f).map { it * k }
        beadRow(c, l, r, h / 2 - 2f * k, sizes, 3, 4f * k, "${m.beadFilled}", 18f * k)
        arabicBox(c, m.cur!!.arabic, l, b - 34f, r, b, 24f, cream)
    }

    private fun beadsLarge(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val l = 16f; val r = w - 16f; val t = 16f; val b = h - 16f
        header(c, str(R.string.widget_tasbih), "${m.folderName} · ${str(R.string.widget_count_of, m.position, m.n)}", "${m.shown} / ${m.target}", l, r, t)
        val btnTop = b - 44f
        val segTop = btnTop - 14f
        val y1 = t + 38f
        val avail = segTop - y1
        val k = (min(w / 338f, h / 354f)).coerceIn(0.7f, 1.2f)
        val sizes = listOf(12f, 18f, 26f, 36f, 64f, 36f, 26f, 18f, 12f).map { it * k }
        beadRow(c, l, r, y1 + avail * 0.22f, sizes, 4, 5f * k, "${m.beadFilled}", 24f * k)
        arabicBox(c, m.cur!!.arabic, l, y1 + avail * 0.42f, r, y1 + avail * 0.80f, 34f * k, goldSoft)
        caption(c, str(R.string.widget_beads_round, m.round, m.rounds, m.beadTotal), w / 2, y1 + avail * 0.90f, Paint.Align.CENTER, maxW = r - l)
        segments(c, l, segTop, r, 4f, min(m.rounds, 8), min(m.round, 8))
        ghost(c, RectF(l, btnTop, l + 56f, b), "−1", 15f)
        solid(c, RectF(l + 66f, btnTop, r, b), "+1")
    }

    private fun beadRow(c: Canvas, l: Float, r: Float, cy: Float, sizes: List<Float>, focus: Int, halo: Float, number: String, numSize: Float) {
        c.drawRect(l, cy - 1f, r, cy + 1f, fill(track))
        val total = sizes.sum() + (sizes.size - 1) * 4f
        val sc = min(1f, (r - l) / total)
        val ds = sizes.map { it * sc }
        val gap = ((r - l) - ds.sum()) / (ds.size - 1)
        var x = l
        ds.forEachIndexed { i, d ->
            val cx = x + d / 2
            when {
                i == focus -> {
                    c.drawCircle(cx, cy, d / 2 + halo, fill(haloC))
                    c.drawCircle(cx, cy, d / 2, fill(gold))
                    text(c, number, cx, cy, numSize * sc, ink, dmBold, Paint.Align.CENTER)
                }
                i < focus -> c.drawCircle(cx, cy, d / 2, fill(gold))
                else -> c.drawCircle(cx, cy, d / 2, fill(beadOff))
            }
            x += d + gap
        }
    }

    // ───────────────────────── I · bars ─────────────────────────

    private fun barsSmall(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val k = min(w, h) / 158f
        val l = 14f; val r = w - 14f; val t = 14f; val b = h - 14f
        eyebrow(c, m.folderName, l, t + 5f, Paint.Align.LEFT, maxW = r - l - 44f)
        caption(c, str(R.string.widget_n_done, m.done), r, t + 5f, Paint.Align.RIGHT)
        val bottom = b - 30f * k
        bars(c, l, r, bottom - 56f * k, bottom, 4f, m, false)
        bigWithSuffix(c, l, b - 13f * k, "${m.position}", " / ${m.n}", 26f * k, 12f)
        caption(c, str(R.string.widget_tap_plus), r, b - 4f, Paint.Align.RIGHT, 10f)
    }

    private fun barsMedium(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val l = 16f; val r = w - 16f; val t = 16f; val b = h - 16f
        eyebrow(c, m.folderName, l, t + 5f, Paint.Align.LEFT, maxW = 84f)
        bigWithSuffix(c, l, b - 30f, "${m.position}", " /${m.n}", 40f, 14f)
        caption(c, str(R.string.widget_now_of, m.shown, m.target), l, b - 5f, Paint.Align.LEFT, 10f, maxW = 84f)
        val bl = l + 84f + 16f
        bars(c, bl, r, t, b - 18f, 5f, m, false)
        caption(c, "1", bl, b - 5f, Paint.Align.LEFT, 9f)
        caption(c, "${m.n}", r, b - 5f, Paint.Align.RIGHT, 9f)
    }

    private fun barsLarge(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val l = 16f; val r = w - 16f; val t = 16f; val b = h - 16f
        val inProgress = if (!m.finished && m.shown > 0) 1 else 0
        val left = (m.n - m.done - inProgress).coerceAtLeast(0)
        header(c, m.folderName, str(R.string.widget_i_status, m.done, inProgress, left), "${m.position} / ${m.n}", l, r, t)
        val rowTop = b - 64f
        val barsTop = t + 48f
        val barsBottom = rowTop - 30f
        val first = bars(c, l, r, barsTop, barsBottom, 6f, m, true)
        numberRow(c, l, r, barsBottom + 14f, m, first)
        val rr = RectF(l, rowTop, r, b)
        c.drawRoundRect(rr, 18f, 18f, fill(inner))
        c.drawRoundRect(RectF(rr.left + .5f, rr.top + .5f, rr.right - .5f, rr.bottom - .5f), 18f, 18f, stroke(innerBorder, 1f))
        val btnW = 64f
        solid(c, RectF(r - 10f - btnW, rowTop + 10f, r - 10f, b - 10f), "+1")
        caption(c, "${m.shown}/${m.target}", r - 10f - btnW - 12f, (rowTop + b) / 2, Paint.Align.RIGHT, 12f)
        arabicBox(c, m.cur!!.arabic, l + 16f, rowTop + 6f, r - 10f - btnW - 70f, b - 6f, 24f, cream, Layout.Alignment.ALIGN_OPPOSITE)
    }

    /** Returns index of the first bar shown. */
    private fun bars(c: Canvas, l: Float, r: Float, top: Float, bottom: Float, radius: Float, m: WidgetModel, highlight: Boolean): Int {
        val count = min(m.n, 24).coerceAtLeast(1)
        val first = (m.curIdx - count / 2).coerceIn(0, max(0, m.n - count))
        val gap = if (count > 14) 3f else 4f
        val bw = ((r - l) - gap * (count - 1)) / count
        for (i in 0 until count) {
            val idx = first + i
            val x = l + i * (bw + gap)
            val f = when {
                m.finished || idx < m.idx -> 1f
                idx == m.idx -> m.shown / (m.items[idx].target.toFloat().coerceAtLeast(1f))
                else -> 0f
            }
            val rr = RectF(x, top, x + bw, bottom)
            val rad = min(radius, bw / 2)
            c.drawRoundRect(rr, rad, rad, fill(chipC))
            if (f > 0f) {
                c.save()
                val clip = Path().apply { addRoundRect(rr, rad, rad, Path.Direction.CW) }
                c.clipPath(clip)
                c.drawRect(x, bottom - (bottom - top) * f.coerceIn(0f, 1f), x + bw, bottom, fill(gold))
                c.restore()
            }
            if (highlight && idx == m.curIdx) {
                c.drawRoundRect(RectF(rr.left + .5f, rr.top + .5f, rr.right - .5f, rr.bottom - .5f), rad, rad, stroke(gold, 1f))
            }
        }
        return first
    }

    private fun numberRow(c: Canvas, l: Float, r: Float, cy: Float, m: WidgetModel, first: Int) {
        val count = min(m.n, 24).coerceAtLeast(1)
        val gap = if (count > 14) 3f else 4f
        val bw = ((r - l) - gap * (count - 1)) / count
        for (i in 0 until count) {
            val idx = first + i
            val cur = idx == m.curIdx
            if (bw < 13f && !cur && i != 0 && i != count - 1) continue
            text(c, "${idx + 1}", l + i * (bw + gap) + bw / 2, cy, 9f, if (cur) gold else muted, if (cur) dmBold else dmReg, Paint.Align.CENTER)
        }
    }

    // ───────────────────────── J · strips ─────────────────────────

    private fun stripDhikr(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val cy = h / 2
        val d = min(46f, h - 20f)
        val btn = RectF(w - 14f - d, cy - d / 2, w - 14f, cy + d / 2)
        solid(c, btn, "+1")
        val countR = btn.left - 12f
        caption(c, "${m.shown} / ${m.target}", countR, cy, Paint.Align.RIGHT, 12f)
        val tw = measure("${m.shown} / ${m.target}", 12f, dmReg)
        arabicBox(c, m.cur!!.arabic, 20f, cy - h * 0.42f, countR - tw - 12f, cy + h * 0.42f, min(25f, h * 0.36f), cream, Layout.Alignment.ALIGN_NORMAL)
    }

    private fun stripProgress(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val l = 20f; val r = w - 20f
        eyebrow(c, "${m.folderName} ${str(R.string.widget_azkar_word)}", l, h / 2 - 9f, Paint.Align.LEFT, maxW = r - l - 60f)
        caption(c, str(R.string.widget_count_of, m.position, m.n), r, h / 2 - 9f, Paint.Align.RIGHT)
        bar(c, l, h / 2 + 5f, r, 4f, overall(m))
    }

    private fun stripDots(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val cy = h / 2
        val l = 20f
        val label = "${m.position} / ${m.n}"
        text(c, label, w - 20f, cy, 12f, gold, dmBold, Paint.Align.RIGHT)
        val avail = (w - 20f - measure(label, 12f, dmBold) - 14f) - l
        val n = m.n
        val stride = min(14f, avail / max(1, n))
        val dr = if (stride < 14f) max(1.5f, stride * 0.28f) else 4f
        for (i in 0 until n) {
            val x = l + i * stride + stride / 2
            val done = m.finished || i < m.idx
            val cur = !m.finished && i == m.idx
            when {
                cur -> {
                    c.drawCircle(x, cy, dr * 2 - 1f, stroke(gold, 2f))
                    c.drawCircle(x, cy, dr / 2 + 0.5f, fill(gold))
                }
                done -> c.drawCircle(x, cy, dr, fill(gold))
                else -> c.drawCircle(x, cy, dr, fill(track))
            }
        }
    }

    private fun stripNext(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val cy = h / 2
        val label = str(R.string.widget_next).uppercase()
        val pw = measure(label, 9f, dmBold) + 18f
        c.drawRoundRect(RectF(14f, cy - 10f, 14f + pw, cy + 10f), 10f, 10f, fill(chipC))
        text(c, label, 14f + pw / 2, cy, 9f, gold, dmBold, Paint.Align.CENTER, spaced(0.1f))
        val ni = if (m.curIdx + 1 < m.n) m.curIdx + 1 else m.curIdx
        val aw = min(120f, w * 0.34f)
        arabicLine(c, m.items[ni].arabic, w - 20f - aw, w - 20f, cy, 19f, muted, Layout.Alignment.ALIGN_NORMAL)
        text(c, m.labels[ni], 14f + pw + 10f, cy, 13f, cream, dmReg, Paint.Align.LEFT, 0f, (w - 20f - aw - 8f) - (14f + pw + 10f))
    }

    // ───────────────────────── J · tall ─────────────────────────

    private fun tallRing(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val s = min(h / 354f, w / 158f).coerceIn(0.7f, 1.3f)
        val l = 14f; val r = w - 14f
        val hs = listOf(12f, 112f * s, 38f * s, 13f, 44f)
        val gap = ((h - 30f) - hs.sum()) / 4f
        var y = 16f
        val ys = hs.map { val top = y; y += it + gap; top }
        eyebrow(c, m.folderName, w / 2, ys[0] + 6f, Paint.Align.CENTER, maxW = r - l)
        val cx = w / 2; val cy = ys[1] + 56f * s
        ring(c, cx, cy, 48f * s, 7f * s, frac(m))
        text(c, "${m.shown}", cx, cy - 2f * s, 34f * s, cream, dmBold, Paint.Align.CENTER)
        text(c, str(R.string.widget_of, m.target), cx, cy + 20f * s, 10f * s, muted, dmReg, Paint.Align.CENTER)
        arabicBox(c, m.cur!!.arabic, l, ys[2], r, ys[2] + hs[2], 27f * s, goldSoft)
        caption(c, str(R.string.widget_next_label, nextLabel(m)), w / 2, ys[3] + 6f, Paint.Align.CENTER, maxW = r - l)
        solid(c, RectF(l, ys[4], r, ys[4] + 44f), "+1")
    }

    private fun tallTimeline(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val l = 14f; val r = w - 14f; val rowH = 46f
        eyebrow(c, m.folderName, l, 16f + 6f, Paint.Align.LEFT, maxW = r - l - 40f)
        caption(c, "${m.position}/${m.n}", r, 16f + 6f, Paint.Align.RIGHT)
        val maxRows = max(1, ((h - 14f - 40f) / rowH).toInt())
        val rows = min(m.n, maxRows)
        val first = (m.curIdx - rows / 2).coerceIn(0, max(0, m.n - rows))
        val listTop = h - 14f - rows * rowH
        val x = l + 7f
        val y0 = listTop + rowH / 2
        val yN = listTop + (rows - 1) * rowH + rowH / 2
        c.drawRect(x - 1f, y0, x + 1f, yN, fill(track))
        val curRow = (m.curIdx - first).coerceIn(0, rows - 1)
        c.drawRect(x - 1f, y0, x + 1f, y0 + curRow * rowH, fill(gold))
        for (i in 0 until rows) {
            val idx = first + i
            val cy = listTop + i * rowH + rowH / 2
            val done = m.finished || idx < m.idx
            val cur = !m.finished && idx == m.idx
            when {
                done -> c.drawCircle(x, cy, 7f, fill(gold))
                cur -> {
                    c.drawCircle(x, cy, 7f, fill(ink))
                    c.drawCircle(x, cy, 6f, stroke(gold, 2f))
                    c.drawCircle(x, cy, 2f, fill(gold))
                }
                else -> {
                    c.drawCircle(x, cy, 7f, fill(ink))
                    c.drawCircle(x, cy, 6.25f, stroke(outline, 1.5f))
                }
            }
            arabicLine(
                c, m.items[idx].arabic, l + 14f + 12f, r, cy,
                if (cur) 21f else 17f, if (cur) goldSoft else if (done) muted else cream,
                Layout.Alignment.ALIGN_OPPOSITE
            )
        }
    }

    // ───────────────────────── shared pieces ─────────────────────────

    private fun nextLabel(m: WidgetModel): String = m.labels.getOrNull(m.curIdx + 1) ?: str(R.string.widget_done)
    private fun frac(m: WidgetModel) = if (m.target == 0) 0f else m.shown / m.target.toFloat()
    private fun overall(m: WidgetModel) = if (m.n == 0) 0f else ((m.done + if (m.finished) 0f else frac(m)) / m.n).coerceIn(0f, 1f)
    private fun spaced(em: Float) = if (arabicUi) 0f else em

    private fun card(c: Canvas, w: Float, h: Float) {
        val rad = min(26f, min(w, h) / 2f)
        c.drawRoundRect(RectF(.5f, .5f, w - .5f, h - .5f), rad, rad, fill(ink))
        c.drawRoundRect(RectF(.5f, .5f, w - .5f, h - .5f), rad, rad, stroke(cardBorder, 1f))
    }

    private fun panel(c: Canvas, r: RectF, rad: Float, active: Boolean) {
        c.drawRoundRect(r, rad, rad, fill(inner))
        c.drawRoundRect(RectF(r.left + .5f, r.top + .5f, r.right - .5f, r.bottom - .5f), rad, rad, stroke(if (active) gold else innerBorder, 1f))
    }

    private fun header(c: Canvas, title: String, sub: String, chipText: String, l: Float, r: Float, t: Float) {
        text(c, title, l, t + 9f, 15f, cream, dmBold, Paint.Align.LEFT, 0f, r - l - 70f)
        caption(c, sub, l, t + 26.5f, Paint.Align.LEFT, maxW = r - l - 70f)
        chip(c, chipText, r, t)
    }

    private fun chip(c: Canvas, s: String, right: Float, top: Float) {
        val wd = measure(s, 11f, dmBold) + 20f
        c.drawRoundRect(RectF(right - wd, top, right, top + 21f), 10.5f, 10.5f, fill(chipC))
        text(c, s, right - wd / 2, top + 10.5f, 11f, gold, dmBold, Paint.Align.CENTER)
    }

    private fun ghost(c: Canvas, r: RectF, label: String, size: Float = 13f) {
        val rad = r.height() / 2
        c.drawRoundRect(RectF(r.left + .5f, r.top + .5f, r.right - .5f, r.bottom - .5f), rad, rad, stroke(outline, 1f))
        text(c, label, r.centerX(), r.centerY(), size, cream, dmBold, Paint.Align.CENTER, 0f, r.width() - 8f)
    }

    private fun solid(c: Canvas, r: RectF, label: String, size: Float = 14f) {
        val rad = r.height() / 2
        c.drawRoundRect(r, rad, rad, fill(gold))
        text(c, label, r.centerX(), r.centerY(), size, ink, dmBold, Paint.Align.CENTER, 0f, r.width() - 8f)
    }

    private fun bar(c: Canvas, l: Float, t: Float, r: Float, h: Float, f: Float) {
        c.drawRoundRect(RectF(l, t, r, t + h), h / 2, h / 2, fill(track))
        val pr = f.coerceIn(0f, 1f)
        if (pr > 0f) c.drawRoundRect(RectF(l, t, l + (r - l) * pr, t + h), h / 2, h / 2, fill(gold))
    }

    private fun segments(c: Canvas, l: Float, t: Float, r: Float, h: Float, total: Int, filled: Int) {
        val gap = 4f
        val sw = (r - l - gap * (total - 1)) / total
        for (i in 0 until total) {
            val x = l + i * (sw + gap)
            c.drawRoundRect(RectF(x, t, x + sw, t + h), h / 2, h / 2, fill(if (i < filled) gold else track))
        }
    }

    private fun ring(c: Canvas, cx: Float, cy: Float, r: Float, sw: Float, f: Float) {
        c.drawCircle(cx, cy, r, stroke(track, sw))
        val pr = f.coerceIn(0f, 1f)
        if (pr > 0f) c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, WidgetRenderMath.ringSweep(pr), false, stroke(gold, sw, Paint.Cap.ROUND))
    }

    private val checkPath: Path by lazy { PathParser.createPathFromPathData("M5 12.5l4.5 4.5L19 7.5") }

    private fun check(c: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        c.save(); c.translate(cx - size / 2, cy - size / 2); c.scale(size / 24f, size / 24f)
        val paint = stroke(color, 3f, Paint.Cap.ROUND); paint.strokeJoin = Paint.Join.ROUND
        c.drawPath(checkPath, paint)
        c.restore()
    }

    private fun bigWithSuffix(c: Canvas, x: Float, cy: Float, big: String, suffix: String, bigSize: Float, sufSize: Float) {
        text(c, big, x, cy, bigSize, cream, dmBold, Paint.Align.LEFT)
        val bw = measure(big, bigSize, dmBold)
        text(c, suffix, x + bw + 2f, cy + bigSize * 0.18f, sufSize, muted, dmReg, Paint.Align.LEFT)
    }

    // ───────────────────────── text ─────────────────────────

    private fun fill(color: Int): Paint { p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = color; return p }
    private fun stroke(color: Int, w: Float, cap: Paint.Cap = Paint.Cap.BUTT): Paint {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = w; p.strokeCap = cap; return p
    }

    private fun measure(s: String, size: Float, tf: Typeface): Float {
        tp.reset(); tp.isAntiAlias = true; tp.textSize = size; tp.typeface = tf
        return tp.measureText(s)
    }

    private fun eyebrow(c: Canvas, s: String, x: Float, cy: Float, align: Paint.Align, color: Int = gold, em: Float = 0.12f, maxW: Float = 0f) =
        text(c, s.uppercase(), x, cy, 10f, color, dmBold, align, spaced(em), maxW)

    private fun caption(c: Canvas, s: String, x: Float, cy: Float, align: Paint.Align, size: Float = 11f, color: Int = muted, maxW: Float = 0f) =
        text(c, s, x, cy, size, color, dmReg, align, 0f, maxW)

    /** Latin UI text, vertically centred on [cy]. */
    private fun text(
        c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int, tf: Typeface,
        align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f, maxW: Float = 0f,
    ) {
        tp.reset(); tp.isAntiAlias = true
        tp.textSize = size; tp.color = color; tp.typeface = tf; tp.textAlign = align; tp.letterSpacing = spacing
        var out = s
        if (maxW > 0f && tp.measureText(out) > maxW) out = TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString()
        val fm = tp.fontMetrics
        c.drawText(out, x, cy - (fm.ascent + fm.descent) / 2f, tp)
    }

    private fun layout(s: String, w: Int, size: Float, lh: Float, color: Int, align: Layout.Alignment, maxLines: Int): StaticLayout {
        val t = ap
        t.reset(); t.isAntiAlias = true
        t.typeface = amiriBold; t.textSize = size; t.color = color
        val fm = t.fontMetrics
        val b = StaticLayout.Builder.obtain(s, 0, s.length, t, w)
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setLineSpacing(size * lh - (fm.descent - fm.ascent), 1f)
        if (maxLines > 0) b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setEllipsizedWidth(w)
        return b.build()
    }

    /** Arabic paragraph in a box, centered vertically, font shrinks until it fits. */
    private fun arabicBox(
        c: Canvas, s: String, l: Float, t: Float, r: Float, b: Float, size: Float, color: Int,
        align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER, lh: Float = 1.4f,
    ) {
        if (r - l < 4f || b - t < 4f) return
        val w = max(1, (r - l).toInt())
        var sz = size
        var lay = layout(s, w, sz, lh, color, align, 0)
        while (lay.height > (b - t) && sz > size * 0.45f) {
            sz *= 0.93f
            lay = layout(s, w, sz, lh, color, align, 0)
        }
        val y = t + ((b - t) - lay.height) / 2f
        c.save(); c.translate(l, y); lay.draw(c); c.restore()
    }

    /** Single Arabic line centred on [cy], ellipsized. */
    private fun arabicLine(c: Canvas, s: String, l: Float, r: Float, cy: Float, size: Float, color: Int, align: Layout.Alignment) {
        if (r - l < 4f) return
        val lay = layout(s, max(1, (r - l).toInt()), size, 1.2f, color, align, 1)
        c.save(); c.translate(l, cy - lay.height / 2f); lay.draw(c); c.restore()
    }

    private companion object { const val MAX_PIXELS = 1_100_000f }
}
