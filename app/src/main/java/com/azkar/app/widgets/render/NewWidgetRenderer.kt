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
import com.azkar.app.data.Tracking
import com.azkar.app.reminders.PrayerSessions
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

/** Which of morning / evening is current: the prayer-time windows when a location is known, else the reminder times. */
object AzkarSessionClock {
    /** One session's window today: prayer times when a location is set, else the reminder time plus four hours. */
    data class Window(val session: PrayerSessions.Session, val start: Long, val end: Long)

    fun windows(now: Long = System.currentTimeMillis()): List<Window> =
        listOf(PrayerSessions.Session.MORNING, PrayerSessions.Session.EVENING).mapNotNull { s ->
            PrayerSessions.window(s, now)?.let { Window(s, it.first, it.second) } ?: Store.schedule(s.scheduleId)?.let { sch ->
                val start = Calendar.getInstance().apply {
                    timeInMillis = now; set(Calendar.HOUR_OF_DAY, sch.hour); set(Calendar.MINUTE, sch.minute)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Window(s, start, start + 4 * 3_600_000L)
            }
        }

    /** The session whose window contains [now], or null between windows. */
    fun pick(windows: List<Window>, now: Long): PrayerSessions.Session? =
        windows.firstOrNull { now >= it.start && now < it.end }?.session

    /** The next moment after [now] that a window opens or closes, or null when there is none. */
    fun boundary(windows: List<Window>, now: Long): Long? =
        windows.flatMap { listOf(it.start, it.end) }.filter { it > now }.minOrNull()

    fun activeSession(now: Long = System.currentTimeMillis()): PrayerSessions.Session? = pick(windows(now), now)
    fun nextBoundary(now: Long = System.currentTimeMillis()): Long? = boundary(windows(now), now)

    fun morningActive(now: Long = System.currentTimeMillis()): Boolean {
        val morning = PrayerSessions.Session.MORNING
        val evening = PrayerSessions.Session.EVENING
        PrayerSessions.window(morning, now)?.let { m ->
            val e = PrayerSessions.window(evening, now) ?: return now < m.second
            return now < e.first           // from midnight until the evening window opens
        }
        val minute = Calendar.getInstance().apply { timeInMillis = now }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        fun at(s: PrayerSessions.Session) = Store.schedule(s.scheduleId)?.let { it.hour * 60 + it.minute }
        val m = at(morning) ?: return true
        val e = at(evening) ?: return true
        return minute in m until e
    }
}

/** Design sizes in dp (from the Compose spec). Used when the launcher reports no size and for previews. */
object AzkarWidgetSizes {
    fun design(f: AzkarWidgetFamily): Pair<Float, Float> = when (f) {
        AzkarWidgetFamily.H_SMALL,
        AzkarWidgetFamily.D_SMALL, AzkarWidgetFamily.B_SMALL,
        AzkarWidgetFamily.C_STREAK_SMALL, AzkarWidgetFamily.G_COUNTDOWN_SMALL, AzkarWidgetFamily.F_BENTO_SMALL -> 158f to 158f
        AzkarWidgetFamily.H_MEDIUM,
        AzkarWidgetFamily.D_MEDIUM, AzkarWidgetFamily.B_MEDIUM,
        AzkarWidgetFamily.C_STREAK_MEDIUM, AzkarWidgetFamily.T_TODAY_MEDIUM -> 338f to 158f
        AzkarWidgetFamily.H_LARGE,
        AzkarWidgetFamily.D_LARGE, AzkarWidgetFamily.C_STREAK_LARGE -> 338f to 354f
        // The strips are one row high and four cells wide (4×1), like their launcher info.
        AzkarWidgetFamily.J_STRIP_DHIKR, AzkarWidgetFamily.J_STRIP_PROGRESS -> 338f to 74f
        AzkarWidgetFamily.J_TALL_RING, AzkarWidgetFamily.J_TALL_TIMELINE -> 158f to 354f
        // Black & yellow designs: 206 mock-up px = 158 dp.
        else -> GoldWidgetRenderer.DESIGN.getValue(f).let { (w, h) -> w * 158f / 206f to h * 158f / 206f }
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
    private val goldMid = ctx.getColor(R.color.azkar_widget_gold_mid)
    private val missedC = ctx.getColor(R.color.azkar_widget_missed)

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

    /** [tracking] feeds the tracking families; previews may leave it null to show every tracked folder. */
    fun render(family: AzkarWidgetFamily, m: WidgetModel, widthDp: Float, heightDp: Float, tracking: TrackingModel? = null, widgetId: Int = 0): Bitmap {
        val w = widthDp.coerceAtLeast(1f)
        val h = heightDp.coerceAtLeast(1f)
        var s = ctx.resources.displayMetrics.density
        val px = w * h * s * s
        if (px > MAX_PIXELS) s *= sqrt(MAX_PIXELS / px) // RemoteViews bitmaps must stay small
        val bmp = Bitmap.createBitmap(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        card(c, w, h)
        if (family.tracking) {
            val t = tracking ?: TrackingModel.load(ctx, null)
            if (!t.hasFolders) { trackingOff(c, w, h); return bmp }
            when (family) {
                AzkarWidgetFamily.C_STREAK_SMALL -> streakSmall(c, w, h, t)
                AzkarWidgetFamily.C_STREAK_MEDIUM -> streakMedium(c, w, h, t)
                AzkarWidgetFamily.C_STREAK_LARGE -> streakLarge(c, w, h, t)
                AzkarWidgetFamily.G_COUNTDOWN_SMALL -> countdownSmall(c, w, h, t)
                AzkarWidgetFamily.F_BENTO_SMALL -> bentoSmall(c, w, h, t)
                else -> todayMedium(c, w, h, t)
            }
            return bmp
        }
        if (GoldWidgetRenderer(ctx).draw(c, family, w, h, m, widgetId)) return bmp
        if (m.n == 0) { empty(c, w, h); return bmp }
        when (family) {
            AzkarWidgetFamily.B_SMALL -> discSmall(c, w, h, m)
            AzkarWidgetFamily.B_MEDIUM -> discMedium(c, w, h, m)
            AzkarWidgetFamily.D_SMALL -> sessionsSmall(c, w, h, m)
            AzkarWidgetFamily.D_MEDIUM -> sessionsMedium(c, w, h, m)
            AzkarWidgetFamily.D_LARGE -> sessionsLarge(c, w, h, m)
            AzkarWidgetFamily.H_SMALL -> beadsSmall(c, w, h, m)
            AzkarWidgetFamily.H_MEDIUM -> beadsMedium(c, w, h, m)
            AzkarWidgetFamily.H_LARGE -> beadsLarge(c, w, h, m)
            AzkarWidgetFamily.J_STRIP_DHIKR -> stripDhikr(c, w, h, m)
            AzkarWidgetFamily.J_STRIP_PROGRESS -> stripProgress(c, w, h, m)
            AzkarWidgetFamily.J_TALL_RING -> tallRing(c, w, h, m)
            AzkarWidgetFamily.J_TALL_TIMELINE -> tallTimeline(c, w, h, m)
            else -> empty(c, w, h)   // tracking families are drawn above
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

    private fun disc(c: Canvas, cx: Float, cy: Float, r: Float, sw: Float, innerR: Float, f: Float) {
        ring(c, cx, cy, r, sw, f)
        c.drawCircle(cx, cy, innerR, fill(inner))
        c.drawCircle(cx, cy, innerR, stroke(innerBorder, 1f))
    }

    // ───────────────────────── D · morning & evening ─────────────────────────

    /**
     * The morning / evening folder by id (names can be renamed or translated) with today's progress from the
     * reader and every widget, so both halves agree with what was actually read; the widget's own folder if it's gone.
     */
    private fun sessionModel(m: WidgetModel, session: PrayerSessions.Session): WidgetModel {
        val f = Store.folder(session.folderId) ?: return m
        return WidgetModel.today(f.id, m.takeIf { f.name == m.folderName })
    }

    private fun morningActive() = AzkarSessionClock.morningActive()

    private fun sessionsSmall(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val k = min(w, h) / 158f
        eyebrow(c, str(R.string.widget_today), w / 2, 14f + 5f, Paint.Align.CENTER)
        val total = 56f * k * 2 + 18f * k
        val left = (w - total) / 2
        val ringCy = h / 2 - 3f * k
        val morning = sessionModel(m, PrayerSessions.Session.MORNING)
        val evening = sessionModel(m, PrayerSessions.Session.EVENING)
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
        val morning = sessionModel(m, PrayerSessions.Session.MORNING)
        val evening = sessionModel(m, PrayerSessions.Session.EVENING)
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
        bigCard(c, RectF(pad, pad, w - pad, pad + ch), str(R.string.widget_morning_azkar), sessionModel(m, PrayerSessions.Session.MORNING), am)
        bigCard(c, RectF(pad, pad + ch + 10f, w - pad, h - pad), str(R.string.widget_evening_azkar), sessionModel(m, PrayerSessions.Session.EVENING), !am)
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

    /** Returns index of the first bar shown. */
    // ───────────────────────── J · strips ─────────────────────────

    private fun stripDhikr(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val cy = h / 2
        val d = min(46f, h - 20f)
        val countR = w - 20f
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

    // ───────────────────────── J · tall ─────────────────────────

    private fun tallRing(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val s = min(h / 354f, w / 158f).coerceIn(0.7f, 1.3f)
        val l = 14f; val r = w - 14f
        val hs = listOf(12f, 112f * s, 38f * s, 13f)
        val gap = ((h - 30f) - hs.sum()) / 3f
        var y = 16f
        val ys = hs.map { val top = y; y += it + gap; top }
        eyebrow(c, m.folderName, w / 2, ys[0] + 6f, Paint.Align.CENTER, maxW = r - l)
        val cx = w / 2; val cy = ys[1] + 56f * s
        ring(c, cx, cy, 48f * s, 7f * s, frac(m))
        text(c, "${m.shown}", cx, cy - 2f * s, 34f * s, cream, dmBold, Paint.Align.CENTER)
        text(c, str(R.string.widget_of, m.target), cx, cy + 20f * s, 10f * s, muted, dmReg, Paint.Align.CENTER)
        arabicBox(c, m.cur!!.arabic, l, ys[2], r, ys[2] + hs[2], 27f * s, goldSoft)
        caption(c, str(R.string.widget_next_label, nextLabel(m)), w / 2, ys[3] + 6f, Paint.Align.CENTER, maxW = r - l)
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

    // ───────────────────────── tracking (C streak · G countdown · F bento · today) ─────────────────────────

    private fun trackingOff(c: Canvas, w: Float, h: Float) {
        text(c, str(R.string.widget_tracking_off), w / 2, h / 2 - 8, 13f, cream, dmBold, Paint.Align.CENTER, 0f, w - 30)
        text(c, str(R.string.widget_tracking_off_hint), w / 2, h / 2 + 10, 11f, muted, dmReg, Paint.Align.CENTER, 0f, w - 30)
    }

    private fun streakSmall(c: Canvas, w: Float, h: Float, t: TrackingModel) {
        val k = min(w, h) / 158f
        val pad = 14f
        eyebrow(c, str(R.string.widget_streak), pad, pad + 5f, Paint.Align.LEFT, maxW = w - pad * 2)
        val big = "${t.streak}"
        val size = 56f * k
        val cy = h / 2 + 2f * k
        text(c, big, pad, cy, size, cream, dmBold, Paint.Align.LEFT)
        caption(c, str(R.string.widget_days), pad + measure(big, size, dmBold) + 6f * k, cy + size * 0.24f, Paint.Align.LEFT, 12f * k)
        // Last seven days; today is a ring until it is finished.
        val d = 12f * k
        val y = h - pad - d / 2
        val step = (w - pad * 2 - d) / (t.week.size - 1)
        t.week.forEachIndexed { i, day ->
            val cx = pad + d / 2 + i * step
            when {
                day.complete -> c.drawCircle(cx, y, d / 2, fill(gold))
                day.isToday -> c.drawCircle(cx, y, d / 2 - 1f, stroke(gold, 2f))
                else -> c.drawCircle(cx, y, d / 2, fill(beadOff))
            }
        }
    }

    private fun streakMedium(c: Canvas, w: Float, h: Float, t: TrackingModel) {
        val pad = 16f
        eyebrow(c, str(R.string.widget_streak_days, t.streak), pad, pad + 5f, Paint.Align.LEFT, maxW = w - pad * 2 - 64f)
        caption(c, str(R.string.widget_best, t.best), w - pad, pad + 5f, Paint.Align.RIGHT)
        val k = (h / 158f).coerceIn(0.7f, 1.2f)
        val d = min(30f * k, (w - pad * 2) / 7f - 6f)
        val step = (w - pad * 2 - d) / (t.week.size - 1)
        val cy = h - pad - d / 2
        t.week.forEachIndexed { i, day ->
            val cx = pad + d / 2 + i * step
            text(c, day.initial, cx, cy - d / 2 - 12f, 10f, if (day.isToday) gold else muted, if (day.isToday) dmBold else dmReg, Paint.Align.CENTER)
            when {
                day.complete -> { c.drawCircle(cx, cy, d / 2, fill(gold)); check(c, cx, cy, d * 0.47f, ink) }
                day.isToday -> { c.drawCircle(cx, cy, d / 2 - 1f, stroke(gold, 2f)); c.drawCircle(cx, cy, 4f * k, fill(gold)) }
                else -> c.drawCircle(cx, cy, d / 2 - .75f, stroke(outline, 1.5f))
            }
        }
    }

    private fun levelColor(l: Tracking.Level) = when (l) {
        Tracking.Level.FULL -> gold
        Tracking.Level.MID -> goldMid
        Tracking.Level.LOW -> haloC
        else -> missedC
    }

    private fun streakLarge(c: Canvas, w: Float, h: Float, t: TrackingModel) {
        val pad = 18f
        val l = pad; val r = w - pad
        header(c, str(R.string.widget_streak), t.rangeLabel, str(R.string.widget_n_days, t.streak), l, r, pad)

        // Legend: best streak on the left, less → more swatches on the right.
        val legendY = h - pad - 6f
        caption(c, str(R.string.widget_best_streak, t.best), l, legendY, Paint.Align.LEFT, maxW = (r - l) / 2f)
        var x = r
        text(c, str(R.string.widget_more), x, legendY, 10f, muted, dmReg, Paint.Align.RIGHT)
        x -= measure(str(R.string.widget_more), 10f, dmReg) + 6f
        listOf(gold, goldMid, haloC, missedC).forEach { col ->
            c.drawRoundRect(RectF(x - 12f, legendY - 6f, x, legendY + 6f), 3f, 3f, fill(col))
            x -= 16f
        }
        text(c, str(R.string.widget_less), x + 2f, legendY, 10f, muted, dmReg, Paint.Align.RIGHT)

        // Calendar: weekday header and five weeks.
        val gap = 6f
        val cellW = (r - l - gap * 6) / 7
        val headCy = pad + 48f
        t.gridHeader.forEachIndexed { i, s -> text(c, s, l + i * (cellW + gap) + cellW / 2, headCy, 10f, muted, dmReg, Paint.Align.CENTER) }
        val top = headCy + 6f + gap
        val rows = t.grid.size / 7
        val cellH = min(34f * (h / 354f).coerceAtLeast(0.6f), ((legendY - 14f) - top - gap * (rows - 1)) / rows)
        val rad = min(8f, cellH / 4f)
        t.grid.forEachIndexed { i, day ->
            val cx = l + (i % 7) * (cellW + gap)
            val cy = top + (i / 7) * (cellH + gap)
            val rect = RectF(cx, cy, cx + cellW, cy + cellH)
            when {
                day.future -> c.drawRoundRect(RectF(rect.left + .5f, rect.top + .5f, rect.right - .5f, rect.bottom - .5f), rad, rad, stroke(innerBorder, 1f))
                day.isToday -> {
                    c.drawRoundRect(rect, rad, rad, fill(if (day.level == Tracking.Level.MISSED) haloC else levelColor(day.level)))
                    c.drawRoundRect(RectF(rect.left + 1f, rect.top + 1f, rect.right - 1f, rect.bottom - 1f), rad, rad, stroke(gold, 2f))
                }
                else -> c.drawRoundRect(rect, rad, rad, fill(levelColor(day.level)))
            }
        }
    }

    /** "2h 20m", "45m" */
    private fun duration(ms: Long): String {
        val mins = ((ms + 59_999L) / 60_000L).toInt()
        val hh = mins / 60; val mm = mins % 60
        return when {
            hh == 0 -> str(R.string.widget_duration_m, mm)
            mm == 0 -> str(R.string.widget_duration_h, hh)
            else -> str(R.string.widget_duration_hm, hh, mm)
        }
    }

    private fun countdownSmall(c: Canvas, w: Float, h: Float, t: TrackingModel) {
        val k = min(w, h) / 158f
        val pad = 14f
        val f = t.focus
        if (f == null) { trackingOff(c, w, h); return }
        val running = t.focusRunning
        eyebrow(c, if (running) "${f.label} · ${str(R.string.widget_now)}" else str(R.string.widget_next_azkar), pad, pad + 5f, Paint.Align.LEFT, maxW = w - pad * 2)
        val mins = ((t.focusDelta + 59_999L) / 60_000L).toInt()
        val clock = String.format(java.util.Locale.getDefault(), "%d:%02d", mins / 60, mins % 60)
        text(c, clock, pad, h / 2 - 4f * k, 48f * k, cream, dmBold, Paint.Align.LEFT, 0f, w - pad * 2)
        caption(c, str(if (running) R.string.widget_hours_minutes_left else R.string.widget_hours_minutes), pad, h / 2 + 26f * k, Paint.Align.LEFT, 11f, maxW = w - pad * 2)
        val footer = if (running) str(R.string.widget_ends_at, f.label, TrackingModel.timeLabel(ctx, f.end!!))
        else "${f.label} · ${TrackingModel.timeLabel(ctx, f.nextStart)}"
        caption(c, footer, pad, h - pad - 5f, Paint.Align.LEFT, maxW = w - pad * 2)
    }

    private fun bentoSmall(c: Canvas, w: Float, h: Float, t: TrackingModel) {
        val k = min(w, h) / 158f
        val pad = 10f; val gap = 6f
        val tw = (w - pad * 2 - gap) / 2; val th = (h - pad * 2 - gap) / 2
        val primary = t.primaryFolder
        val primaryProgress = primary?.let { p -> t.sessions.firstOrNull { it.folderId == p }?.progress }
            ?: primary?.let { Tracking.progress(listOf(it), java.util.Calendar.getInstance()) }
        val primaryLabel = primary?.let { p -> t.sessions.firstOrNull { it.folderId == p }?.label ?: Store.folderName(p) } ?: ""
        val f = t.focus
        val tiles = listOf(
            Triple("${(t.today.fraction * 100).roundToInt()}%", str(R.string.widget_today_lower), true),
            Triple(primaryProgress?.let { "${it.done}/${it.total}" } ?: "–", primaryLabel.lowercase(), false),
            Triple("${t.streak}", str(R.string.widget_day_streak), false),
            if (f != null) Triple(shortDuration(t.focusDelta), if (t.focusRunning) str(R.string.widget_left_in, f.label.lowercase()) else str(R.string.widget_to_session, f.label.lowercase()), false)
            else Triple("${t.best}", str(R.string.widget_best_lower), false),
        )
        tiles.forEachIndexed { i, (value, cap, hi) ->
            val x = pad + (i % 2) * (tw + gap); val y = pad + (i / 2) * (th + gap)
            val rect = RectF(x, y, x + tw, y + th)
            if (hi) c.drawRoundRect(rect, 14f, 14f, fill(gold))
            else {
                c.drawRoundRect(rect, 14f, 14f, fill(inner))
                c.drawRoundRect(RectF(x + .5f, y + .5f, x + tw - .5f, y + th - .5f), 14f, 14f, stroke(innerBorder, 1f))
            }
            text(c, value, x + 10f, rect.centerY() - 6f * k, 24f * k, if (hi) ink else cream, dmBold, Paint.Align.LEFT, 0f, tw - 16f)
            text(c, cap, x + 10f, rect.centerY() + 14f * k, 9f * k, if (hi) ink else muted, dmReg, Paint.Align.LEFT, 0f, tw - 16f)
        }
    }

    /** "2h", "35m": the biggest unit only, for the small bento tile. */
    private fun shortDuration(ms: Long): String {
        val mins = ((ms + 59_999L) / 60_000L).toInt()
        return if (mins >= 60) str(R.string.widget_duration_h, mins / 60) else str(R.string.widget_duration_m, mins)
    }

    private fun todayMedium(c: Canvas, w: Float, h: Float, t: TrackingModel) {
        val l = 20f; val r = w - 20f; val top = 18f
        // Header: "Today" and, on the right, how long the current session has left (or until the next one starts).
        val f = t.focus
        val status = when {
            f == null -> ""
            t.focusRunning -> str(R.string.widget_time_left, duration(t.focusDelta))
            else -> str(R.string.widget_starts_in, f.label, duration(t.focusDelta))
        }
        text(c, status, r, top + 6f, 13f, gold, dmBold, Paint.Align.RIGHT, 0f, (r - l) * 0.6f)
        val sw = if (status.isEmpty()) 0f else min(measure(status, 13f, dmBold), (r - l) * 0.6f) + 12f
        eyebrow(c, str(R.string.widget_today), l, top + 6f, Paint.Align.LEFT, maxW = r - l - sw)

        // The day from midnight to midnight: dim windows for each session, gold up to now.
        val span = (t.dayEnd - t.dayStart).toFloat().coerceAtLeast(1f)
        fun xAt(ms: Long) = l + (r - l) * ((ms - t.dayStart) / span).coerceIn(0f, 1f)
        val barH = 8f
        val barCy = top + 6f + (h - top - 6f) * 0.42f
        val bt = barCy - barH / 2
        c.drawRoundRect(RectF(l, bt, r, bt + barH), barH / 2, barH / 2, fill(track))
        t.sessions.forEach { s -> s.end?.let { e -> c.drawRoundRect(RectF(xAt(s.start), bt, xAt(e), bt + barH), barH / 2, barH / 2, fill(haloC)) } }
        val nowX = xAt(t.now)
        if (nowX > l) c.drawRoundRect(RectF(l, bt, nowX, bt + barH), barH / 2, barH / 2, fill(gold))
        c.drawCircle(nowX, barCy, 7f, fill(gold))

        // Session start labels under the bar, kept inside the card and apart from each other.
        var minX = l
        t.sessions.sortedBy { it.start }.forEach { s ->
            val label = "${s.label} · ${TrackingModel.timeLabel(ctx, s.start)}"
            val lw = measure(label, 12f, dmReg)
            val x = xAt(s.start).coerceAtLeast(minX).coerceAtMost(r - lw)
            caption(c, label, x, barCy + 7f + 16f, Paint.Align.LEFT, 12f)
            minX = x + lw + 12f
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
