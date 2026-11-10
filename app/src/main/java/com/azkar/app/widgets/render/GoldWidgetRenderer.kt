package com.azkar.app.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.format.DateFormat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.azkar.app.R
import com.azkar.app.data.Store
import com.azkar.app.reminders.PrayerSessions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The black & yellow designs (K rings, A dial, P after-prayer, I counters, N tall stack, U Friday, Z6 morning,
 * G stars, M8 sunrise / moon, M7 tahlil). Each is drawn in its design's own pixel grid (206 px = one 2×2 widget)
 * and scaled uniformly into the real widget, centred, so nothing stretches when the widget is resized.
 */
internal class GoldWidgetRenderer(private val ctx: Context) {
    private val dmBold = ResourcesCompat.getFont(ctx, R.font.dm_sans_bold) ?: Typeface.DEFAULT_BOLD
    private val dmReg = ResourcesCompat.getFont(ctx, R.font.dm_sans_regular) ?: Typeface.DEFAULT
    private val amiri = ResourcesCompat.getFont(ctx, R.font.amiri_regular) ?: Typeface.SERIF
    private val arabicUi = ctx.resources.configuration.locales[0].language == "ar"

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val ap = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)   // Arabic layouts only, so labels never change their alignment

    private fun str(id: Int, vararg a: Any): String = ctx.getString(id, *a)

    /** Draws [family]; false when it isn't one of these designs. [widgetId] is 0 for previews. */
    fun draw(c: Canvas, family: AzkarWidgetFamily, w: Float, h: Float, m: WidgetModel, widgetId: Int): Boolean {
        val (dw, dh) = DESIGN[family] ?: return false
        val rad = min(26f, min(w, h) / 2f)
        c.save()
        val clip = Path().apply { addRoundRect(RectF(1f, 1f, w - 1f, h - 1f), rad, rad, Path.Direction.CW) }
        c.clipPath(clip)
        if (family == AzkarWidgetFamily.Z6_MORNING_SMALL) c.drawRect(0f, 0f, w, h, fill(GOLD))
        val k = min(w / dw, h / dh)
        c.translate((w - dw * k) / 2f, (h - dh * k) / 2f)
        c.scale(k, k)
        when (family) {
            AzkarWidgetFamily.K_RINGS_SMALL -> ringsSmall(c, m)
            AzkarWidgetFamily.K_RINGS_MEDIUM -> ringsMedium(c, m)
            AzkarWidgetFamily.A_DIAL_SMALL -> dialSmall(c)
            AzkarWidgetFamily.A_DIAL_MEDIUM -> dialMedium(c)
            AzkarWidgetFamily.P_AFTER_MEDIUM -> afterMedium(c, m)
            AzkarWidgetFamily.P_AFTER_LARGE -> afterLarge(c, m)
            AzkarWidgetFamily.I_COUNTERS_LARGE -> counters(c, m, widgetId)
            AzkarWidgetFamily.N_TALL_STACK -> tallStack(c, m)
            AzkarWidgetFamily.U_FRIDAY_MEDIUM -> friday(c, m)
            AzkarWidgetFamily.Z6_MORNING_SMALL -> morningSmall(c)
            AzkarWidgetFamily.G5_STAR_SMALL -> star(c)
            AzkarWidgetFamily.G_ROSETTE_SMALL -> rosette(c)
            AzkarWidgetFamily.M8_SUNRISE -> when (AzkarSessionClock.activeSession()) {
                PrayerSessions.Session.MORNING -> sunrise(c)
                PrayerSessions.Session.EVENING -> moon(c)
                else -> tahlil(c)
            }
            AzkarWidgetFamily.M7_TAHLIL_FULL -> tahlil(c)
            else -> {}
        }
        c.restore()
        return true
    }

    // ───────────────────────── session data ─────────────────────────

    private fun session(m: WidgetModel, s: PrayerSessions.Session): WidgetModel {
        val f = Store.folder(s.folderId) ?: return m
        return WidgetModel.today(f.id, m.takeIf { f.name == m.folderName })
    }

    private fun frac(m: WidgetModel) = if (m.target == 0) 0f else m.shown / m.target.toFloat()
    private fun overall(m: WidgetModel) = if (m.n == 0) 0f else ((m.done + if (m.finished) 0f else frac(m)) / m.n).coerceIn(0f, 1f)

    /** "done / total" of a folder: whole azkar, or recitations when the folder is a single zikr (a tasbih). */
    private fun progressPair(m: WidgetModel): Pair<Int, Int> = if (m.n == 1) m.shown to m.target else m.done to m.n

    /** Recitations done and in total across the folder, for the "45 / 100" chips. */
    private fun totals(m: WidgetModel): Pair<Int, Int> {
        val total = m.items.sumOf { it.target }
        val done = if (m.finished) total else m.items.take(m.idx).sumOf { it.target } + m.shown
        return done to total
    }

    private data class Window(val session: PrayerSessions.Session, val start: Long, val end: Long)

    /** Today's morning and evening windows: prayer times when a location is set, else the reminder times. */
    private fun windows(now: Long = System.currentTimeMillis()): List<Window> =
        PrayerSessions.Session.values().mapNotNull { s ->
            PrayerSessions.window(s, now)?.let { Window(s, it.first, it.second) } ?: Store.schedule(s.scheduleId)?.let { sch ->
                val start = Calendar.getInstance().apply {
                    timeInMillis = now; set(Calendar.HOUR_OF_DAY, sch.hour); set(Calendar.MINUTE, sch.minute)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Window(s, start, start + 4 * 3_600_000L)
            }
        }.filter { it.session == PrayerSessions.Session.MORNING || it.session == PrayerSessions.Session.EVENING }

    private fun sessionLabel(s: PrayerSessions.Session) =
        str(if (s == PrayerSessions.Session.MORNING) R.string.widget_morning else R.string.widget_evening)

    private fun timeLabel(at: Long) = DateFormat.getTimeFormat(ctx).format(Date(at))

    // ───────────────────────── K · rings ─────────────────────────

    private fun ringsSmall(c: Canvas, m: WidgetModel) {
        val am = AzkarSessionClock.morningActive()
        eyebrow(c, str(R.string.widget_today), 103f, 25.8f, Paint.Align.CENTER, 12.5f)
        rings(c, 103f, 116f, listOf(64.5f, 49.5f, 34.5f), 11f, m)
        icon(c, if (am) SUN else MOON, 103f, 116f, 22f, GOLD, 1.8f)
    }

    private fun ringsMedium(c: Canvas, m: WidgetModel) {
        val am = AzkarSessionClock.morningActive()
        rings(c, 99f, 103f, listOf(69f, 52f, 35f), 12f, m)
        icon(c, if (am) SUN else MOON, 99f, 103f, 24f, GOLD, 1.8f)
        label(c, str(R.string.widget_today), 206f, 32f, 14f, GOLD, true, maxW = 212f)
        val morning = session(m, PrayerSessions.Session.MORNING)
        val evening = session(m, PrayerSessions.Session.EVENING)
        val rows = listOf(
            Triple(str(R.string.widget_morning), progressPair(morning), am),
            Triple(str(R.string.widget_evening), progressPair(evening), !am),
            Triple(m.folderName, progressPair(m), false),
        )
        val dots = listOf(GOLD, GOLD_2, GOLD_3)
        rows.forEachIndexed { i, (name, pr, now) ->
            val top = 48f + i * 46f
            val cy = top + 23f
            if (i > 0) c.drawRect(206f, top, 418f, top + 1f, fill(LINE))
            c.drawCircle(211f, cy, 5f, fill(dots[i]))
            val right = numberPair(c, pr.first, pr.second, 418f, cy, 20f, 14f)
            var x = 226f
            val nameMax = right - x - 10f - if (now) 50f else 0f
            label(c, name, x, cy, 15.5f, SOFT, false, maxW = nameMax)
            x += min(measure(name, 15.5f, dmReg), nameMax) + 10f
            if (now) pill(c, str(R.string.widget_now), x, cy, 12f, 9f, 8f)
        }
    }

    private fun rings(c: Canvas, cx: Float, cy: Float, radii: List<Float>, sw: Float, m: WidgetModel) {
        val values = listOf(
            overall(session(m, PrayerSessions.Session.MORNING)),
            overall(session(m, PrayerSessions.Session.EVENING)),
            if (m.n == 1) frac(m) else overall(m),
        )
        val colors = listOf(GOLD, GOLD_2, GOLD_3)
        radii.forEachIndexed { i, r -> ring(c, cx, cy, r, sw, values[i], colors[i]) }
    }

    // ───────────────────────── A · dial ─────────────────────────

    private fun dialSmall(c: Canvas) {
        label(c, str(R.string.widget_azkar_times), 103f, 28f, 14f, GOLD, true, Paint.Align.CENTER, 180f)
        dial(c, 103f, 116f, 62f, 30f)
    }

    private fun dialMedium(c: Canvas) {
        val now = System.currentTimeMillis()
        dial(c, 99f, 103f, 66f, 32f)
        val l = 206f; val r = 418f
        label(c, str(R.string.widget_azkar_times), l, 32f, 14f, GOLD, true, maxW = r - l)
        val ws = windows(now)
        val active = ws.firstOrNull { now in it.start until it.end }
        val next = ws.filter { it.start > now }.minByOrNull { it.start }
            ?: ws.minByOrNull { it.start }?.let { it.copy(start = it.start + DAY, end = it.end + DAY) }
        val target = active?.end ?: next?.start
        if (target != null) {
            val mins = ((target - now + 59_999L) / 60_000L).toInt()
            var x = l
            val base = 75f
            fun part(n: Int, unit: String) {
                x += drawBaseline(c, "$n", x, base, 40f, CREAM, dmBold) + 4f
                x += drawBaseline(c, unit, x, base, 18f, MUTED, dmBold) + 8f
            }
            if (mins >= 60) part(mins / 60, str(R.string.widget_unit_h))
            part(mins % 60, str(R.string.widget_unit_m))
            val caption = if (active != null) str(R.string.widget_left_of_session, sessionLabel(active.session))
            else str(R.string.widget_until_session, sessionLabel(next!!.session))
            label(c, caption, l, 102.5f, 14.5f, SOFT, false, maxW = r - l)
        } else {
            drawBaseline(c, "–", l, 75f, 40f, CREAM, dmBold)
        }
        ws.sortedBy { it.session.ordinal }.take(2).forEachIndexed { i, wnd ->
            val top = 120f + i * 32f
            val cy = top + 16f
            if (i > 0) c.drawRect(l, top, r, top + 1f, fill(LINE))
            val on = wnd === active
            c.drawCircle(l + 5f, cy, 5f, fill(if (on) GOLD else GOLD_3))
            val range = "${timeLabel(wnd.start)} – ${timeLabel(wnd.end)}"
            val rw = measure(range, 14f, dmReg)
            label(c, range, r, cy, 14f, MUTED, false, Paint.Align.RIGHT)
            label(c, sessionLabel(wnd.session), l + 18f, cy, 15f, if (on) CREAM else SOFT, on, maxW = r - l - 26f - rw)
        }
    }

    /** A 24-hour dial: noon at the top, midnight at the bottom, the azkar windows as arcs and a dot for now. */
    private fun dial(c: Canvas, cx: Float, cy: Float, r: Float, timeSize: Float) {
        val now = System.currentTimeMillis()
        c.drawCircle(cx, cy, r, stroke(TRACK, 12f))
        fun angle(ms: Long): Float {
            val cal = Calendar.getInstance().apply { timeInMillis = ms }
            val hours = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
            return (hours - 12f) * 15f - 90f   // canvas degrees, 0 = 3 o'clock
        }
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        fun arc(from: Long, to: Long, color: Int) {
            if (to <= from) return
            val a = angle(from)
            var sweep = angle(to) - a
            if (sweep <= 0f) sweep += 360f
            c.drawArc(oval, a, sweep, false, stroke(color, 12f, Paint.Cap.ROUND))
        }
        windows(now).forEach { wnd ->
            when {
                now >= wnd.end -> arc(wnd.start, wnd.end, DIM)
                now < wnd.start -> arc(wnd.start, wnd.end, GOLD_3)
                else -> { arc(wnd.start, now, DIM); arc(now, wnd.end, GOLD) }
            }
        }
        for (hour in listOf(3, 6, 9, 15, 18, 21)) {
            val a = Math.toRadians(((hour - 12) * 15 - 90).toDouble())
            val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
            c.drawLine(cx + ca * (r - 14f), cy + sa * (r - 14f), cx + ca * (r - 10f), cy + sa * (r - 10f), stroke(TICK, 2f, Paint.Cap.ROUND))
        }
        icon(c, SUN, cx, cy - (r - 20f), 14f, MUTED, 2f)
        icon(c, MOON, cx, cy + (r - 20f), 14f, MUTED, 2f)
        val a = Math.toRadians(angle(now).toDouble())
        val mx = cx + cos(a).toFloat() * r; val my = cy + sin(a).toFloat() * r
        c.drawCircle(mx, my, 8.5f, fill(INK))
        c.drawCircle(mx, my, 8.5f, stroke(GOLD, 3f))
        val h24 = DateFormat.is24HourFormat(ctx)
        val time = SimpleDateFormat(if (h24) "H:mm" else "h:mm", Locale.US).format(Date(now))
        if (h24) {
            label(c, time, cx, cy, timeSize, CREAM, true, Paint.Align.CENTER)
        } else {
            label(c, time, cx, cy - 11f, timeSize, CREAM, true, Paint.Align.CENTER)
            label(c, SimpleDateFormat("a", ctx.resources.configuration.locales[0]).format(Date(now)), cx, cy + 16.4f, 13f, MUTED, true, Paint.Align.CENTER)
        }
    }

    // ───────────────────────── P · after prayer ─────────────────────────

    private fun shortName(m: WidgetModel, i: Int): String =
        (m.labels.getOrNull(i)?.takeIf { !it.startsWith("×") } ?: m.items[i].arabic).replace(Regex("\\s+"), " ").trim()

    private fun stepFrac(m: WidgetModel, i: Int) = when {
        m.finished || i < m.idx -> 1f
        i == m.idx -> frac(m)
        else -> 0f
    }

    private fun afterMedium(c: Canvas, m: WidgetModel) {
        if (m.n == 0) { emptyState(c, 442f, 206f); return }
        val (done, total) = totals(m)
        label(c, m.folderName, 22f, 30f, 14f, GOLD, true, maxW = 260f)
        chip(c, "$done / $total", 420f, 20f)
        val shown = min(3, m.n)
        val first = (m.curIdx - 1).coerceIn(0, m.n - shown)
        val gap = 8f
        val cw = (398f - gap * (shown - 1)) / shown
        for (j in 0 until shown) {
            val i = first + j
            val x = 22f + j * (cw + gap)
            bar(c, x, 60f, x + cw, 6f, stepFrac(m, i))
            val isDone = m.finished || i < m.idx
            val isCur = !m.finished && i == m.idx
            var tx = x
            if (isDone) { icon(c, CHECK, x + 6f, 83.4f, 12f, GOLD, 2.6f); tx += 17f }
            label(c, shortName(m, i), tx, 83.4f, 13f, if (isCur) CREAM else if (isDone) SOFT else MUTED, isCur, maxW = x + cw - tx)
        }
        arabicBox(c, m.cur!!.arabic, 22f, 106f, 420f, 160f, 42f, CREAM)
        label(c, if (m.finished) str(R.string.widget_completed) else str(R.string.widget_count_of, m.shown, m.target),
            221f, 175.4f, 15.5f, SOFT, false, Paint.Align.CENTER, 380f)
    }

    private fun afterLarge(c: Canvas, m: WidgetModel) {
        if (m.n == 0) { emptyState(c, 442f, 463f); return }
        val (done, total) = totals(m)
        label(c, m.folderName, 22f, 37.2f, 21f, CREAM, true, maxW = 290f)
        label(c, str(R.string.widget_step_total, m.position, m.n, total), 22f, 67f, 14.5f, MUTED, false, maxW = 290f)
        chip(c, "$done / $total", 420f, 22f)
        // The step before, the current one as a big card, then the ones after (three rows in all).
        val first = (m.curIdx - 1).coerceIn(0, max(0, m.n - 3))
        val rows = (first until min(m.n, first + 3)).toList()
        var y = 86f
        for (i in rows) {
            if (i == m.curIdx) {
                val r = RectF(22f, y, 420f, y + 148f)
                c.drawRoundRect(r, 22f, 22f, fill(PANEL))
                c.drawRoundRect(RectF(r.left + .75f, r.top + .75f, r.right - .75f, r.bottom - .75f), 22f, 22f, stroke(GOLD, 1.5f))
                label(c, str(R.string.widget_step_n, i + 1), 38f, y + 23.4f, 13f, GOLD, true, maxW = 200f)
                numberPair(c, m.shown, m.target, 404f, y + 23.4f, 20f, 14f)
                arabicBox(c, m.items[i].arabic, 38f, y + 38f, 404f, y + 120f, 54f, CREAM)
                bar(c, 38f, y + 126f, 404f, 6f, frac(m))
                y += 148f + 10f
            } else {
                val isDone = m.finished || i < m.idx
                val r = RectF(22f, y, 420f, y + 54f)
                c.drawRoundRect(r, 18f, 18f, fill(PANEL))
                val cy = y + 27f
                if (isDone) {
                    c.drawCircle(51f, cy, 13f, fill(GOLD))
                    icon(c, CHECK, 51f, cy, 15f, INK, 2.8f)
                } else c.drawCircle(51f, cy, 12.25f, stroke(TICK, 1.5f))
                val color = if (isDone) SOFT else MUTED
                val name = shortName(m, i)
                val arabic = m.items[i].arabic
                var nameMax = 328f
                if (name != arabic.replace(Regex("\\s+"), " ").trim()) {
                    val aw = arabicFit(c, arabic, 240f, 404f, cy, 26f, color, Paint.Align.RIGHT)
                    nameMax = 404f - aw - 12f - 76f
                }
                label(c, name, 76f, cy - 8f, 15.5f, color, true, maxW = nameMax)
                label(c, "${if (isDone) m.items[i].target else 0} / ${m.items[i].target}", 76f, cy + 12f, 12f, MUTED, false)
                y += 54f + 10f
            }
        }
    }

    // ───────────────────────── I · counters ─────────────────────────

    private fun counters(c: Canvas, m: WidgetModel, widgetId: Int) {
        val counts = WidgetStore.counters(ctx, widgetId)
        val last = WidgetStore.lastCounter(ctx, widgetId)
        val cells = m.items.take(4)
        label(c, str(R.string.widget_counters), 24f, 32f, 14f, GOLD, true, maxW = 220f)
        // Chip: "Today 64"
        val todayW = measure(str(R.string.widget_today), 13.5f, dmReg)
        val total = counts.take(cells.size).sum().toString()
        val totW = measure(total, 13f, dmBold)
        val cw = todayW + 6f + totW + 24f
        c.drawRoundRect(RectF(418f - cw, 18f, 418f, 46f), 14f, 14f, fill(CHIP))
        label(c, str(R.string.widget_today), 418f - cw + 12f, 32f, 13.5f, MUTED, false)
        label(c, total, 418f - 12f, 32f, 13f, GOLD, true, Paint.Align.RIGHT)
        if (cells.isEmpty()) { emptyState(c, 442f, 463f); return }
        cells.forEachIndexed { i, item ->
            val x = 22f + (i % 2) * 205f
            val y = 62f + (i / 2) * 195f
            val r = RectF(x, y, x + 193f, y + 183f)
            c.drawRoundRect(r, 26f, 26f, fill(PANEL))
            if (i == last) c.drawRoundRect(RectF(r.left + .75f, r.top + .75f, r.right - .75f, r.bottom - .75f), 26f, 26f, stroke(GOLD, 1.5f))
            arabicBox(c, item.arabic, x + 12f, y + 12f, x + 181f, y + 68f, 26f, CREAM)
            m.labels.getOrNull(i)?.takeIf { !it.startsWith("×") }?.let {
                label(c, it, x + 96.5f, y + 82f, 14f, MUTED, false, Paint.Align.CENTER, 170f)
            }
            label(c, "${counts[i]}", x + 18f, y + 183f - 36f, 36f, CREAM, true, maxW = 100f)
            val bx = x + 193f - 14f - 26f; val by = y + 183f - 14f - 26f
            c.drawCircle(bx, by, 26f, fill(GOLD))
            icon(c, PLUS, bx, by, 22f, INK, 2.6f)
        }
    }

    // ───────────────────────── N · tall stack ─────────────────────────

    private fun tallStack(c: Canvas, m: WidgetModel) {
        val am = AzkarSessionClock.morningActive()
        label(c, str(R.string.widget_today), 18f, 30f, 14f, GOLD, true, maxW = 90f)
        label(c, m.date, 188f, 30f, 12f, MUTED, false, Paint.Align.RIGHT, 80f)
        val ws = windows()
        fun startOf(s: PrayerSessions.Session) = ws.firstOrNull { it.session == s }?.let { timeLabel(it.start) } ?: ""
        val morning = session(m, PrayerSessions.Session.MORNING)
        val evening = session(m, PrayerSessions.Session.EVENING)
        data class Card(val icon: Path, val title: String, val model: WidgetModel, val active: Boolean, val status: String)
        fun status(s: WidgetModel, active: Boolean, session: PrayerSessions.Session) = when {
            s.finished -> str(R.string.widget_done)
            active -> str(R.string.widget_now)
            else -> startOf(session)
        }
        val cards = listOf(
            Card(SUN, str(R.string.widget_morning), morning, am, status(morning, am, PrayerSessions.Session.MORNING)),
            Card(MOON, str(R.string.widget_evening), evening, !am, status(evening, !am, PrayerSessions.Session.EVENING)),
            Card(BEADS, m.folderName, m, false,
                if (m.finished) str(R.string.widget_done)
                else if (m.n == 1) str(R.string.widget_round_short, m.round, m.rounds)
                else str(R.string.widget_count_of, m.position, m.n)),
        )
        cards.forEachIndexed { i, card ->
            val top = 52f + i * 134f
            val r = RectF(14f, top, 192f, top + 124f)
            c.drawRoundRect(r, 24f, 24f, fill(PANEL))
            if (card.active) c.drawRoundRect(RectF(r.left + .75f, r.top + .75f, r.right - .75f, r.bottom - .75f), 24f, 24f, stroke(GOLD, 1.5f))
            val cy = top + 62f
            ring(c, 56f, cy, 25f, 6f, if (card.model.n == 1) frac(card.model) else overall(card.model), GOLD)
            icon(c, card.icon, 56f, cy, 20f, GOLD, 1.9f)
            label(c, card.title, 96f, cy - 23.5f, 14.5f, SOFT, false, maxW = 88f)
            val (d, t) = progressPair(card.model)
            val bw = measure("$d", 22f, dmBold)
            label(c, "$d", 96f, cy - 2f, 22f, CREAM, true)
            label(c, "/ $t", 96f + bw + 4f, cy + 1f, 13f, MUTED, false, maxW = 84f - bw)
            label(c, card.status, 96f, cy + 24.6f, 13f, if (card.active && !card.model.finished) GOLD else MUTED, card.active, maxW = 88f)
        }
    }

    // ───────────────────────── U · Friday ─────────────────────────

    private fun friday(c: Canvas, m: WidgetModel) {
        label(c, str(R.string.widget_friday), 24f, 30f, 14f, GOLD, true, maxW = 300f)
        val read = WidgetStore.kahfRead(ctx)
        // Al-Kahf: tap the left card to mark it read for this Friday.
        val l = RectF(16f, 50f, 215f, 188f)
        c.drawRoundRect(l, 24f, 24f, fill(PANEL))
        if (read) {
            c.drawCircle(43f, 75f, 11f, fill(GOLD))
            icon(c, CHECK, 43f, 75f, 14f, INK, 2.8f)
        } else c.drawCircle(43f, 75f, 10.25f, stroke(TICK, 1.5f))
        arabicFit(c, KAHF, l.left + 16f, l.right - 16f, 116f, 30f, CREAM)
        label(c, str(if (read) R.string.widget_kahf_read else R.string.widget_kahf_unread), l.centerX(), 164f, 13.5f,
            if (read) GOLD else MUTED, false, Paint.Align.CENTER, 170f)
        // The widget's own folder (the salawat by default): tap the right card to count.
        val r = RectF(227f, 50f, 426f, 188f)
        c.drawRoundRect(r, 24f, 24f, fill(PANEL))
        if (m.n == 0) { label(c, str(R.string.widget_no_azkar), r.centerX(), r.centerY(), 13f, MUTED, false, Paint.Align.CENTER, 170f); return }
        val (done, total) = totals(m)
        val f = if (total == 0) 0f else done / total.toFloat()
        ring(c, 254f, 75f, 8.5f, 3f, f, GOLD)
        label(c, "$done / $total", 410f, 75f, 13f, GOLD, true, Paint.Align.RIGHT, 120f)
        arabicFit(c, m.folderName, r.left + 16f, r.right - 16f, 116f, 30f, CREAM)
        bar(c, 243f, 168f, 410f, 6f, f)
    }

    // ───────────────────────── decorative ─────────────────────────

    private fun morningSmall(c: Canvas) {
        listOf(Z6_A to 1.5f, Z6_B to 1.2f, Z6_C to 1.2f).forEach { (pts, sw) -> poly(c, pts, null, SHADE, sw) }
        c.drawCircle(103f, 46f, 28f, fill(INK))
        icon(c, SUNRISE, 103f, 46f, 30f, GOLD, 1.8f)
        arabicFit(c, "أَصْبَحْنَا", 10f, 196f, 115f, 44f, INK)
        label(c, str(R.string.widget_morning_azkar), 103f, 168.3f, 17f, INK, true, Paint.Align.CENTER, 190f)
    }

    private fun star(c: Canvas) {
        poly(c, G5_A, DECO_PANEL, DECO, 1.5f)
        poly(c, G5_B, null, DIM, 1.2f)
        poly(c, G5_C, null, GOLD, 1.5f)
        poly(c, G5_D, GOLD, GOLD, 1f)
        poly(c, G5_E, INK, INK, 1f)
        c.drawCircle(103f, 103f, 8f, fill(GOLD))
    }

    private fun rosette(c: Canvas) {
        poly(c, GR_A, null, DECO, 1.6f)
        poly(c, GR_B, null, DIM, 1.3f)
        poly(c, G5_C, null, GOLD, 1.4f)
        poly(c, GR_D, null, DECO, 1.1f)
        c.drawCircle(103f, 103f, 43f, fill(DECO_PANEL))
        c.drawCircle(103f, 103f, 43f, stroke(DECO, 1.5f))
        arabicFit(c, "الله", 64f, 142f, 103f, 42f, GOLD)
    }

    private fun sunrise(c: Canvas) {
        RAYS.forEach { (x1, y1, x2, y2) -> c.drawLine(x1, y1, x2, y2, stroke(DECO, 3f, Paint.Cap.ROUND)) }
        c.drawCircle(221f, 236f, 64f, fill(GOLD))
        label(c, str(R.string.widget_morning_azkar), 24f, 24f, 14f, GOLD, true, maxW = 300f)
        arabicFit(c, "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ", 16f, 426f, 66.8f, 36f, CREAM)
    }

    private fun moon(c: Canvas) {
        c.drawCircle(352f, 103f, 62f, fill(GOLD))
        c.drawCircle(378f, 88f, 56f, fill(INK))
        listOf(Triple(290f, 40f, 3f), Triple(318f, 168f, 2f), Triple(404f, 160f, 3f), Triple(258f, 120f, 2f), Triple(410f, 44f, 3f))
            .forEach { (x, y, r) -> c.drawCircle(x, y, r, fill(DECO)) }
        label(c, str(R.string.widget_evening_azkar), 24f, 24f, 14f, GOLD, true, maxW = 240f)
        arabicFit(c, "أَمْسَيْنَا وَأَمْسَى", 10f, 280f, 80.9f, 34f, CREAM)
        arabicFit(c, "الْمُلْكُ لِلَّهِ", 10f, 280f, 138.7f, 34f, GOLD)
    }

    private fun tahlil(c: Canvas) {
        poly(c, M7_A, null, DIM, 1.5f)
        poly(c, M7_B, null, DIM, 1.2f)
        poly(c, M7_C, null, DIM, 1.2f)
        label(c, str(R.string.widget_tahlil), 418f, 26f, 14f, GOLD, true, Paint.Align.RIGHT, 200f)
        arabicFit(c, "لَا إِلَهَ إِلَّا اللَّهُ", 130f, 418f, 85f, 40f, GOLD)
        arabicFit(c, "وَحْدَهُ لَا شَرِيكَ لَهُ", 130f, 418f, 146.25f, 30f, CREAM)
        c.drawCircle(400f, 172f, 22f, fill(CHIP))
        icon(c, SHARE, 400f, 172f, 20f, GOLD, 1.9f)
    }

    private fun emptyState(c: Canvas, dw: Float, dh: Float) {
        label(c, str(R.string.widget_no_azkar), dw / 2, dh / 2 - 10f, 16f, CREAM, true, Paint.Align.CENTER, dw - 40f)
        label(c, str(R.string.widget_tap_to_open), dw / 2, dh / 2 + 14f, 13f, MUTED, false, Paint.Align.CENTER, dw - 40f)
    }

    // ───────────────────────── pieces ─────────────────────────

    private fun fill(color: Int): Paint { p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = color; return p }
    private fun stroke(color: Int, w: Float, cap: Paint.Cap = Paint.Cap.BUTT): Paint {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = w; p.strokeCap = cap
        p.strokeJoin = Paint.Join.ROUND; return p
    }

    private fun ring(c: Canvas, cx: Float, cy: Float, r: Float, sw: Float, f: Float, color: Int) {
        c.drawCircle(cx, cy, r, stroke(TRACK, sw))
        val pr = f.coerceIn(0f, 1f)
        if (pr > 0f) c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * pr, false, stroke(color, sw, Paint.Cap.ROUND))
    }

    private fun bar(c: Canvas, l: Float, t: Float, r: Float, h: Float, f: Float) {
        c.drawRoundRect(RectF(l, t, r, t + h), h / 2, h / 2, fill(TRACK))
        val pr = f.coerceIn(0f, 1f)
        if (pr > 0f) c.drawRoundRect(RectF(l, t, l + max(h, (r - l) * pr), t + h), h / 2, h / 2, fill(GOLD))
    }

    private fun chip(c: Canvas, s: String, right: Float, top: Float) {
        val wd = measure(s, 13f, dmBold) + 24f
        c.drawRoundRect(RectF(right - wd, top, right, top + 28f), 14f, 14f, fill(CHIP))
        label(c, s, right - wd / 2, top + 14f, 13f, GOLD, true, Paint.Align.CENTER)
    }

    private fun pill(c: Canvas, s: String, left: Float, cy: Float, size: Float, rad: Float, pad: Float) {
        val wd = measure(s, size, dmBold) + pad * 2
        c.drawRoundRect(RectF(left, cy - 11f, left + wd, cy + 11f), rad, rad, fill(CHIP))
        label(c, s, left + pad, cy, size, GOLD, true)
    }

    /** Right-aligned "12 / 33": returns the left edge. */
    private fun numberPair(c: Canvas, big: Int, total: Int, right: Float, cy: Float, bigSize: Float, sufSize: Float): Float {
        val suf = "/ $total"
        val sw = measure(suf, sufSize, dmReg)
        label(c, suf, right, cy + bigSize * 0.12f, sufSize, MUTED, false, Paint.Align.RIGHT)
        val bw = measure("$big", bigSize, dmBold)
        label(c, "$big", right - sw - 5f, cy, bigSize, CREAM, true, Paint.Align.RIGHT)
        return right - sw - 5f - bw
    }

    private fun eyebrow(c: Canvas, s: String, x: Float, cy: Float, align: Paint.Align, size: Float) {
        tp.reset(); tp.isAntiAlias = true; tp.textSize = size; tp.typeface = dmBold; tp.color = GOLD; tp.textAlign = align
        if (!arabicUi) tp.letterSpacing = 0.12f
        val fm = tp.fontMetrics
        c.drawText(s.uppercase(), x, cy - (fm.ascent + fm.descent) / 2f, tp)
    }

    private fun measure(s: String, size: Float, tf: Typeface): Float {
        tp.reset(); tp.isAntiAlias = true; tp.textSize = size; tp.typeface = tf
        return tp.measureText(s)
    }

    /** UI text vertically centred on [cy]; bold = the design's 600/700 weights. */
    private fun label(
        c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int, bold: Boolean,
        align: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f,
    ) {
        tp.reset(); tp.isAntiAlias = true
        tp.textSize = size; tp.color = color; tp.typeface = if (bold) dmBold else dmReg; tp.textAlign = align
        var out = s
        if (maxW > 0f && tp.measureText(out) > maxW) out = TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString()
        val fm = tp.fontMetrics
        c.drawText(out, x, cy - (fm.ascent + fm.descent) / 2f, tp)
    }

    /** Text on a baseline; returns its width. */
    private fun drawBaseline(c: Canvas, s: String, x: Float, base: Float, size: Float, color: Int, tf: Typeface): Float {
        tp.reset(); tp.isAntiAlias = true; tp.textSize = size; tp.color = color; tp.typeface = tf
        c.drawText(s, x, base, tp)
        return tp.measureText(s)
    }

    /**
     * One line of Amiri centred on [cy] between [l] and [r] (or right-aligned), shrunk until it fits and
     * ellipsized below half size. Returns the drawn width.
     */
    private fun arabicFit(c: Canvas, s: String, l: Float, r: Float, cy: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER): Float {
        val avail = r - l
        if (avail < 4f) return 0f
        val line = s.replace(Regex("\\s+"), " ").trim()
        ap.reset(); ap.isAntiAlias = true; ap.typeface = amiri; ap.color = color; ap.textSize = size; ap.textAlign = Paint.Align.LEFT
        var sz = size
        while (android.text.Layout.getDesiredWidth(line, ap) > avail && sz > size * 0.5f) { sz *= 0.94f; ap.textSize = sz }
        val wd = min(avail, android.text.Layout.getDesiredWidth(line, ap))
        val lay = layout(line, avail, 1, when (align) {
            Paint.Align.RIGHT -> android.text.Layout.Alignment.ALIGN_NORMAL   // RTL: normal = right
            Paint.Align.LEFT -> android.text.Layout.Alignment.ALIGN_OPPOSITE
            else -> android.text.Layout.Alignment.ALIGN_CENTER
        })
        c.save(); c.translate(l, cy - lay.height / 2f); lay.draw(c); c.restore()
        return wd
    }

    /** An Arabic paragraph centred in a box, on as many lines as fit, shrinking (then ellipsized) until it does. */
    private fun arabicBox(c: Canvas, s: String, l: Float, t: Float, r: Float, b: Float, size: Float, color: Int) {
        val avail = r - l
        if (avail < 4f || b - t < 4f) return
        val text = s.trim()
        ap.reset(); ap.isAntiAlias = true; ap.typeface = amiri; ap.color = color; ap.textSize = size; ap.textAlign = Paint.Align.LEFT
        var sz = size
        var lay = layout(text, avail, 0, android.text.Layout.Alignment.ALIGN_CENTER)
        while (lay.height > b - t && sz > size * 0.55f) {
            sz *= 0.93f; ap.textSize = sz
            lay = layout(text, avail, 0, android.text.Layout.Alignment.ALIGN_CENTER)
        }
        if (lay.height > b - t) {
            val lines = max(1, ((b - t) / (lay.height / lay.lineCount.toFloat())).toInt())
            lay = layout(text, avail, lines, android.text.Layout.Alignment.ALIGN_CENTER)
        }
        c.save(); c.translate(l, t + ((b - t) - lay.height) / 2f); lay.draw(c); c.restore()
    }

    /** RTL StaticLayout with [ap]'s current font and size; [maxLines] 0 = no limit. */
    private fun layout(s: String, width: Float, maxLines: Int, align: android.text.Layout.Alignment): android.text.StaticLayout {
        val w = max(1, width.toInt())
        val b = android.text.StaticLayout.Builder.obtain(s, 0, s.length, ap, w)
            .setAlignment(align)
            .setTextDirection(android.text.TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setLineSpacing(ap.textSize * 0.25f, 1f)
        if (maxLines > 0) b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setEllipsizedWidth(w)
        return b.build()
    }

    private fun icon(c: Canvas, path: Path, cx: Float, cy: Float, size: Float, color: Int, sw: Float) {
        c.save(); c.translate(cx - size / 2, cy - size / 2); c.scale(size / 24f, size / 24f)
        c.drawPath(path, stroke(color, sw, Paint.Cap.ROUND))
        if (path === BEADS) {
            c.drawCircle(5f, 12f, 2.6f, fill(color)); c.drawCircle(12f, 12f, 3.8f, fill(color)); c.drawCircle(19f, 12f, 2.6f, fill(color))
        }
        c.restore()
    }

    private fun poly(c: Canvas, pts: FloatArray, fillColor: Int?, strokeColor: Int, sw: Float) {
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        path.close()
        if (fillColor != null) c.drawPath(path, fill(fillColor))
        c.drawPath(path, stroke(strokeColor, sw))
    }

    companion object {
        const val DAY = 86_400_000L
        private const val KAHF = "سورة الكهف"

        // Design palette.
        private const val GOLD = 0xFFE3C06E.toInt()
        private const val GOLD_2 = 0xFFB99A4E.toInt()
        private const val GOLD_3 = 0xFF8F7637.toInt()
        private const val DIM = 0xFF4A3F23.toInt()
        private const val DECO = 0xFF8A743A.toInt()
        private const val DECO_PANEL = 0xFF0C0C0C.toInt()
        private const val INK = 0xFF000000.toInt()
        private const val CREAM = 0xFFFFFEF8.toInt()
        private const val SOFT = 0xFFC4C3BE.toInt()
        private const val MUTED = 0xFF8C8B87.toInt()
        private const val TRACK = 0xFF262626.toInt()
        private const val TICK = 0xFF4A4A47.toInt()
        private const val PANEL = 0xFF111111.toInt()
        private const val CHIP = 0xFF1A1A1A.toInt()
        private const val LINE = 0xFF1F1F1F.toInt()
        private const val SHADE = 0x1F000000

        /** Design canvas of each family, in the mock-up's pixels. */
        val DESIGN: Map<AzkarWidgetFamily, Pair<Float, Float>> = mapOf(
            AzkarWidgetFamily.K_RINGS_SMALL to (206f to 206f),
            AzkarWidgetFamily.K_RINGS_MEDIUM to (442f to 206f),
            AzkarWidgetFamily.A_DIAL_SMALL to (206f to 206f),
            AzkarWidgetFamily.A_DIAL_MEDIUM to (442f to 206f),
            AzkarWidgetFamily.P_AFTER_MEDIUM to (442f to 206f),
            AzkarWidgetFamily.P_AFTER_LARGE to (442f to 463f),
            AzkarWidgetFamily.I_COUNTERS_LARGE to (442f to 463f),
            AzkarWidgetFamily.N_TALL_STACK to (206f to 463f),
            AzkarWidgetFamily.U_FRIDAY_MEDIUM to (442f to 206f),
            AzkarWidgetFamily.Z6_MORNING_SMALL to (206f to 206f),
            AzkarWidgetFamily.G5_STAR_SMALL to (206f to 206f),
            AzkarWidgetFamily.G_ROSETTE_SMALL to (206f to 206f),
            AzkarWidgetFamily.M8_SUNRISE to (442f to 206f),
            AzkarWidgetFamily.M7_TAHLIL_FULL to (442f to 206f),
        )

        private fun path(d: String): Path = PathParser.createPathFromPathData(d)
        private val SUN = path("M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.3 5.3l1.6 1.6M17.1 17.1l1.6 1.6M18.7 5.3l-1.6 1.6M6.9 17.1l-1.6 1.6")
        private val MOON = path("M20.5 14.2A8.5 8.5 0 1 1 9.8 3.5a6.6 6.6 0 0 0 10.7 10.7z")
        private val BEADS = path("M2 12h20")
        private val CHECK = path("M5 12.5l4.2 4.2L19 7.5")
        private val PLUS = path("M12 5v14M5 12h14")
        private val SHARE = path("M12 15.5V4M7.8 8.2l4.2-4.2 4.2 4.2M5.5 12.5V19a1.5 1.5 0 0 0 1.5 1.5h10a1.5 1.5 0 0 0 1.5-1.5v-6.5")
        private val SUNRISE = path("M17 17.5a5 5 0 0 0-10 0M12 3v6M8.7 6l3.3-3 3.3 3M4.6 10.6l1.5 1.5M19.4 10.6l-1.5 1.5M1.8 17.5h2.4M19.8 17.5h2.4M2.5 21.5h19")

        private fun pts(s: String) = s.trim().split(Regex("[ ,]+")).map { it.toFloat() }.toFloatArray()

        private val Z6_A = pts("103.00,-8.00 138.15,27.14 187.85,27.15 187.86,76.85 223.00,112.00 187.86,147.15 187.85,196.85 138.15,196.86 103.00,232.00 67.85,196.86 18.15,196.85 18.14,147.15 -17.00,112.00 18.14,76.85 18.15,27.15 67.85,27.14")
        private val Z6_B = pts("148.92,1.13 167.95,47.05 213.87,66.08 194.85,112.00 213.87,157.92 167.95,176.95 148.92,222.87 103.00,203.85 57.08,222.87 38.05,176.95 -7.87,157.92 11.15,112.00 -7.87,66.08 38.05,47.05 57.08,1.13 103.00,20.15")
        private val Z6_C = pts("103.00,28.00 127.60,52.60 162.40,52.60 162.40,87.40 187.00,112.00 162.40,136.60 162.40,171.40 127.60,171.40 103.00,196.00 78.40,171.40 43.60,171.40 43.60,136.60 19.00,112.00 43.60,87.40 43.60,52.60 78.40,52.60")
        private val G5_A = pts("103.00,5.00 131.70,33.70 172.30,33.70 172.30,74.30 201.00,103.00 172.30,131.70 172.30,172.30 131.70,172.30 103.00,201.00 74.30,172.30 33.70,172.30 33.70,131.70 5.00,103.00 33.70,74.30 33.70,33.70 74.30,33.70")
        private val G5_B = pts("140.50,12.46 156.04,49.96 193.54,65.50 178.01,103.00 193.54,140.50 156.04,156.04 140.50,193.54 103.00,178.01 65.50,193.54 49.96,156.04 12.46,140.50 27.99,103.00 12.46,65.50 49.96,49.96 65.50,12.46 103.00,27.99")
        private val G5_C = pts("103.00,31.00 124.09,52.09 153.91,52.09 153.91,81.91 175.00,103.00 153.91,124.09 153.91,153.91 124.09,153.91 103.00,175.00 81.91,153.91 52.09,153.91 52.09,124.09 31.00,103.00 52.09,81.91 52.09,52.09 81.91,52.09")
        private val G5_D = pts("103.00,55.00 117.06,69.06 136.94,69.06 136.94,88.94 151.00,103.00 136.94,117.06 136.94,136.94 117.06,136.94 103.00,151.00 88.94,136.94 69.06,136.94 69.06,117.06 55.00,103.00 69.06,88.94 69.06,69.06 88.94,69.06")
        private val G5_E = pts("113.72,77.13 118.15,87.85 128.87,92.28 124.43,103.00 128.87,113.72 118.15,118.15 113.72,128.87 103.00,124.43 92.28,128.87 87.85,118.15 77.13,113.72 81.57,103.00 77.13,92.28 87.85,87.85 92.28,77.13 103.00,81.57")
        private val GR_A = pts("103.00,6.00 131.41,34.41 171.59,34.41 171.59,74.59 200.00,103.00 171.59,131.41 171.59,171.59 131.41,171.59 103.00,200.00 74.59,171.59 34.41,171.59 34.41,131.41 6.00,103.00 34.41,74.59 34.41,34.41 74.59,34.41")
        private val GR_B = pts("140.12,13.38 155.50,50.50 192.62,65.88 177.24,103.00 192.62,140.12 155.50,155.50 140.12,192.62 103.00,177.24 65.88,192.62 50.50,155.50 13.38,140.12 28.76,103.00 13.38,65.88 50.50,50.50 65.88,13.38 103.00,28.76")
        private val GR_D = pts("130.55,36.48 141.97,64.03 169.52,75.45 158.11,103.00 169.52,130.55 141.97,141.97 130.55,169.52 103.00,158.11 75.45,169.52 64.03,141.97 36.48,130.55 47.89,103.00 36.48,75.45 64.03,64.03 75.45,36.48 103.00,47.89")
        private val M7_A = pts("60.00,13.00 86.36,39.36 123.64,39.36 123.64,76.64 150.00,103.00 123.64,129.36 123.64,166.64 86.36,166.64 60.00,193.00 33.64,166.64 -3.64,166.64 -3.64,129.36 -30.00,103.00 -3.64,76.64 -3.64,39.36 33.64,39.36")
        private val M7_B = pts("94.44,19.85 108.71,54.29 143.15,68.56 128.89,103.00 143.15,137.44 108.71,151.71 94.44,186.15 60.00,171.89 25.56,186.15 11.29,151.71 -23.15,137.44 -8.89,103.00 -23.15,68.56 11.29,54.29 25.56,19.85 60.00,34.11")
        private val M7_C = pts("60.00,40.00 78.45,58.45 104.55,58.45 104.55,84.55 123.00,103.00 104.55,121.45 104.55,147.55 78.45,147.55 60.00,166.00 41.55,147.55 15.45,147.55 15.45,121.45 -3.00,103.00 15.45,84.55 15.45,58.45 41.55,58.45")
        private val RAYS = listOf(
            floatArrayOf(140.25f, 221.76f, 118.58f, 217.94f), floatArrayOf(149.99f, 195.00f, 130.93f, 184.00f),
            floatArrayOf(168.29f, 173.18f, 154.15f, 156.33f), floatArrayOf(192.95f, 158.95f, 185.43f, 138.27f),
            floatArrayOf(221.00f, 154.00f, 221.00f, 132.00f), floatArrayOf(249.05f, 158.95f, 256.57f, 138.27f),
            floatArrayOf(273.71f, 173.18f, 287.85f, 156.33f), floatArrayOf(292.01f, 195.00f, 311.07f, 184.00f),
            floatArrayOf(301.75f, 221.76f, 323.42f, 217.94f),
        )
    }
}
