package com.azkar.app.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.azkar.app.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import java.util.Calendar

object WidgetRenderMath {
    fun ringSweep(fraction: Float): Float = 360f * fraction.coerceIn(0f, 1f)
    fun beadDiameter(index: Int, focusedIndex: Int): Float = if (index == focusedIndex) 30f else 16f
    fun barFillHeight(top: Float, bottom: Float, fraction: Float): Float =
        (bottom - top) * fraction.coerceIn(0f, 1f)
}

object AzkarSessionClock {
    fun activeSession(context: Context, now: Calendar = Calendar.getInstance()): String {
        val schedules = com.azkar.app.data.Store.schedules()
        val morning = schedules.firstOrNull { it.open == "morning" }?.hour ?: 6
        val evening = schedules.firstOrNull { it.open == "evening" }?.hour ?: 17
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return if (hour >= morning && hour < evening) "MORNING" else "EVENING"
    }
}

class NewWidgetRenderer(private val context: Context) {
    private val gold = context.getColor(R.color.azkar_widget_gold)
    private val goldSoft = context.getColor(R.color.azkar_widget_gold_soft)
    private val halo = context.getColor(R.color.azkar_widget_gold_halo)
    private val cream = context.getColor(R.color.azkar_widget_cream)
    private val muted = context.getColor(R.color.azkar_widget_muted)
    private val track = context.getColor(R.color.azkar_widget_track)
    private val card = context.getColor(R.color.azkar_widget_ink)
    private val border = context.getColor(R.color.azkar_widget_card_border)
    private val inner = context.getColor(R.color.azkar_widget_inner)
    private val outline = context.getColor(R.color.azkar_widget_outline)
    private val arabic = ResourcesCompat.getFont(context, R.font.amiri_bold) ?: Typeface.SERIF
    private val latin = ResourcesCompat.getFont(context, R.font.dm_sans_regular) ?: Typeface.DEFAULT
    private val latinMedium = ResourcesCompat.getFont(context, R.font.dm_sans_medium) ?: Typeface.DEFAULT_BOLD

    fun render(family: AzkarWidgetFamily, model: WidgetModel, widthDp: Float, heightDp: Float): Bitmap {
        var density = context.resources.displayMetrics.density
        val px = widthDp * heightDp * density * density
        if (px > 1_100_000f) density *= kotlin.math.sqrt(1_100_000f / px)
        val bitmap = Bitmap.createBitmap(
            (widthDp * density).toInt().coerceAtLeast(1),
            (heightDp * density).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val w = widthDp
        val h = heightDp
        card(canvas, w, h)
        when (family) {
            AzkarWidgetFamily.B_SMALL, AzkarWidgetFamily.B_MEDIUM, AzkarWidgetFamily.B_LARGE -> disc(canvas, w, h, model)
            AzkarWidgetFamily.D_SMALL, AzkarWidgetFamily.D_MEDIUM, AzkarWidgetFamily.D_LARGE -> sessions(canvas, w, h, model)
            AzkarWidgetFamily.H_SMALL, AzkarWidgetFamily.H_MEDIUM, AzkarWidgetFamily.H_LARGE -> beads(canvas, w, h, model)
            AzkarWidgetFamily.I_SMALL, AzkarWidgetFamily.I_MEDIUM, AzkarWidgetFamily.I_LARGE -> bars(canvas, w, h, model)
            AzkarWidgetFamily.J_STRIP_DHIKR -> stripDhikr(canvas, w, h, model)
            AzkarWidgetFamily.J_STRIP_PROGRESS -> stripProgress(canvas, w, h, model)
            AzkarWidgetFamily.J_STRIP_DOTS -> stripDots(canvas, w, h, model)
            AzkarWidgetFamily.J_STRIP_NEXT -> stripNext(canvas, w, h, model)
            AzkarWidgetFamily.J_TALL_RING -> tallRing(canvas, w, h, model)
            AzkarWidgetFamily.J_TALL_TIMELINE -> timeline(canvas, w, h, model)
        }
        return bitmap
    }

    private fun card(c: Canvas, w: Float, h: Float) {
        val p = paint(Paint.Style.FILL, card)
        c.drawRoundRect(RectF(0f, 0f, w, h), 26f, 26f, p)
        c.drawRoundRect(RectF(.5f, .5f, w - .5f, h - .5f), 26f, 26f, paint(Paint.Style.STROKE, border, 1f))
    }

    private fun disc(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * .30f
        ring(c, cx, cy, r, 8f, fraction(m))
        text(c, "${m.shown}", cx, cy + 10f, if (h > 250) 32f else 24f, cream, latinMedium, Paint.Align.CENTER)
        text(c, "of ${m.target}", cx, cy + 29f, 11f, muted, latin, Paint.Align.CENTER)
        if (h > 250) {
            text(c, "TAP TO COUNT", cx, h - 24f, 10f, gold, latinMedium, Paint.Align.CENTER)
        }
    }

    private fun sessions(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        text(c, "TODAY", 16f, 22f, 10f, gold, latinMedium, Paint.Align.LEFT)
        val gap = if (w < 200) 18f else 24f
        val r = if (w < 200) 28f else 38f
        val active = AzkarSessionClock.activeSession(context)
        session(c, w / 2f - r - gap / 2f, h / 2f, r, m, "MORNING", active == "MORNING")
        session(c, w / 2f + r + gap / 2f, h / 2f, r, m, "EVENING", active == "EVENING")
    }

    private fun session(c: Canvas, cx: Float, cy: Float, r: Float, m: WidgetModel, label: String, active: Boolean) {
        ring(c, cx, cy, r, 5f, if (active) fraction(m) else 0f)
        text(c, if (m.finished && active) "✓" else "${m.shown}", cx, cy + 6f, if (r > 30) 18f else 15f, cream, latinMedium, Paint.Align.CENTER)
        text(c, label, cx, cy + r + 18f, 10f, if (active) gold else muted, latinMedium, Paint.Align.CENTER)
    }

    private fun beads(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        text(c, "TASBIH", 16f, 22f, 10f, gold, latinMedium, Paint.Align.LEFT)
        text(c, "${m.shown} / ${m.target}", w - 16f, 22f, 11f, muted, latin, Paint.Align.RIGHT)
        val count = min(m.beadTotal, 11)
        val spacing = (w - 36f) / count
        val y = h / 2f
        repeat(count) { i ->
            val x = 18f + spacing * i + spacing / 2f
            val filled = i < m.beadFilled * count / m.beadTotal
            val focused = m.beadFilled.coerceAtMost(count - 1)
            c.drawCircle(x, y, WidgetRenderMath.beadDiameter(i, focused) / 2f, paint(Paint.Style.FILL, if (filled) gold else track))
            if (i == m.beadFilled.coerceAtMost(count - 1)) c.drawCircle(x, y, 19f, paint(Paint.Style.STROKE, halo, 4f))
        }
        arabic(c, m.cur?.arabic ?: "", 16f, h - 42f, w - 16f, h - 8f, if (h > 250) 28f else 20f, goldSoft)
    }

    private fun bars(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        text(c, m.folderName.uppercase(), 16f, 22f, 10f, gold, latinMedium, Paint.Align.LEFT)
        text(c, "${m.done} / ${m.n}", w - 16f, 22f, 11f, muted, latin, Paint.Align.RIGHT)
        val count = m.items.size.coerceAtMost(16).coerceAtLeast(1)
        val gap = 4f
        val barW = ((w - 32f) - gap * (count - 1)) / count
        val bottom = h - 28f
        val top = if (h > 250) 70f else 56f
        m.items.take(count).forEachIndexed { i, item ->
            val x = 16f + i * (barW + gap)
            val fill = if (i < m.idx) 1f else if (i == m.curIdx) m.shown / item.target.toFloat() else 0f
            c.drawRoundRect(RectF(x, top, x + barW, bottom), 5f, 5f, paint(Paint.Style.FILL, track))
            val fh = WidgetRenderMath.barFillHeight(top, bottom, fill)
            c.drawRoundRect(RectF(x, bottom - fh, x + barW, bottom), 5f, 5f, paint(Paint.Style.FILL, if (i == m.curIdx) gold else goldSoft))
        }
        text(c, "${m.shown} / ${m.target}", 16f, h - 10f, 12f, cream, latinMedium, Paint.Align.LEFT)
    }

    private fun stripDhikr(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        arabic(c, m.cur?.arabic ?: "", 18f, 10f, w - 100f, h - 10f, 18f, cream)
        text(c, "${m.shown} / ${m.target}", w - 84f, h / 2f + 4f, 12f, muted, latin, Paint.Align.CENTER)
        button(c, w - 50f, h / 2f, "+1")
    }

    private fun stripProgress(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        text(c, "${m.folderName.uppercase()} AZKAR", 20f, 28f, 10f, gold, latinMedium, Paint.Align.LEFT)
        text(c, "${m.position} of ${m.n}", w - 20f, 28f, 11f, muted, latin, Paint.Align.RIGHT)
        progress(c, 20f, h - 22f, w - 20f, 4f, overallFraction(m))
    }

    private fun stripDots(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        val count = m.n.coerceAtMost(18).coerceAtLeast(1)
        repeat(count) { i ->
            c.drawCircle(20f + i * ((w - 100f) / count), h / 2f, 4f, paint(Paint.Style.FILL, if (i < m.idx) gold else track))
        }
        text(c, "${m.position} / ${m.n}", w - 20f, h / 2f + 4f, 12f, gold, latinMedium, Paint.Align.RIGHT)
    }

    private fun stripNext(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        pill(c, 14f, h / 2f, "NEXT")
        val next = m.items.getOrNull(m.curIdx + 1)
        arabic(c, next?.arabic ?: m.cur?.arabic ?: "", 70f, 14f, w - 18f, h - 14f, 18f, cream)
    }

    private fun tallRing(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        text(c, m.folderName.uppercase(), 16f, 24f, 10f, gold, latinMedium, Paint.Align.LEFT)
        ring(c, w / 2f, 132f, 56f, 7f, fraction(m))
        text(c, "${m.shown}", w / 2f, 140f, 30f, cream, latinMedium, Paint.Align.CENTER)
        text(c, "of ${m.target}", w / 2f, 158f, 10f, muted, latin, Paint.Align.CENTER)
        arabic(c, m.cur?.arabic ?: "", 16f, 205f, w - 16f, 250f, 23f, goldSoft)
        button(c, w / 2f, h - 34f, "+1", w - 28f)
    }

    private fun timeline(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        text(c, m.folderName.uppercase(), 16f, 24f, 10f, gold, latinMedium, Paint.Align.LEFT)
        text(c, "${m.position}/${m.n}", w - 16f, 24f, 11f, muted, latin, Paint.Align.RIGHT)
        val x = 22f
        c.drawRect(x, 48f, x + 2f, h - 22f, paint(Paint.Style.FILL, track))
        c.drawRect(x, 48f, x + 2f, 48f + (h - 70f) * overallFraction(m), paint(Paint.Style.FILL, gold))
        m.items.take(6).forEachIndexed { i, item ->
            val y = 60f + i * 44f
            c.drawCircle(x, y, 6f, paint(Paint.Style.FILL, if (i < m.idx) gold else if (i == m.curIdx) inner else card))
            c.drawCircle(x, y, 6f, paint(Paint.Style.STROKE, if (i <= m.curIdx) gold else outline, 2f))
            arabic(c, item.arabic, 38f, y - 13f, w - 16f, y + 13f, if (i == m.curIdx) 18f else 15f, if (i == m.curIdx) goldSoft else muted)
        }
    }

    private fun ring(c: Canvas, cx: Float, cy: Float, radius: Float, stroke: Float, fraction: Float) {
        c.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), -90f, 360f, false, paint(Paint.Style.STROKE, track, stroke))
        c.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), -90f, WidgetRenderMath.ringSweep(fraction), false, paint(Paint.Style.STROKE, gold, stroke))
    }

    private fun progress(c: Canvas, left: Float, top: Float, right: Float, height: Float, fraction: Float) {
        c.drawRoundRect(RectF(left, top, right, top + height), height / 2f, height / 2f, paint(Paint.Style.FILL, track))
        c.drawRoundRect(RectF(left, top, left + (right - left) * fraction.coerceIn(0f, 1f), top + height), height / 2f, height / 2f, paint(Paint.Style.FILL, gold))
    }

    private fun button(c: Canvas, cx: Float, cy: Float, value: String, width: Float = 52f) {
        c.drawRoundRect(RectF(cx - width / 2f, cy - 22f, cx + width / 2f, cy + 22f), 22f, 22f, paint(Paint.Style.FILL, gold))
        text(c, value, cx, cy + 5f, 14f, card, latinMedium, Paint.Align.CENTER)
    }

    private fun pill(c: Canvas, cx: Float, cy: Float, value: String) {
        c.drawRoundRect(RectF(cx, cy - 12f, cx + 48f, cy + 12f), 12f, 12f, paint(Paint.Style.FILL, inner))
        text(c, value, cx + 24f, cy + 4f, 9f, gold, latinMedium, Paint.Align.CENTER)
    }

    private fun arabic(c: Canvas, value: String, left: Float, top: Float, right: Float, bottom: Float, size: Float, color: Int) {
        val p = paint(Paint.Style.FILL, color).apply { typeface = arabic; textSize = size; textAlign = Paint.Align.CENTER }
        c.drawText(value, (left + right) / 2f, (top + bottom) / 2f + size / 3f, p)
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface, align: Paint.Align) {
        c.drawText(value, x, y, paint(Paint.Style.FILL, color).apply { this.typeface = typeface; textSize = size; textAlign = align })
    }

    private fun paint(style: Paint.Style, color: Int, stroke: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = style; this.color = color; this.strokeWidth = stroke }

    private fun fraction(m: WidgetModel) = if (m.target == 0) 0f else m.shown / m.target.toFloat()
    private fun overallFraction(m: WidgetModel) = if (m.n == 0) 0f else (m.done + fraction(m)) / m.n
}
