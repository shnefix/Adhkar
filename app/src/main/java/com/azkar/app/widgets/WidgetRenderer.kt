package com.azkar.app.widgets

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
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.Store
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the 14 widget designs using the app's selected theme. All coordinates are in dp
 * (the canvas is scaled), so the numbers below are the same numbers as in the design file.
 */
class WidgetRenderer(ctx: Context) {
    init {
        Store.init(ctx)
    }

    private val palette = WidgetPalette.from(ctx, AppPrefs.theme() == com.azkar.app.ThemeMode.OLED)
    private val strings = WidgetStrings(ctx)
    private val amiriBold = ResourcesCompat.getFont(ctx, R.font.amiri_bold) ?: Typeface.SERIF
    private val amiriReg = ResourcesCompat.getFont(ctx, R.font.amiri_regular) ?: Typeface.SERIF
    private val dmBold = ResourcesCompat.getFont(ctx, R.font.dm_sans_bold) ?: Typeface.DEFAULT_BOLD
    private val dmReg = ResourcesCompat.getFont(ctx, R.font.dm_sans_regular) ?: Typeface.DEFAULT

    private val BLACK get() = palette.background
    private val CARD_BORDER get() = palette.border
    private val GOLD get() = palette.accent
    private val GOLD_SOFT get() = palette.accentSoft
    private val TEXT get() = palette.text
    private val MUTED get() = palette.muted
    private val SOFT get() = palette.soft
    private val TRACK get() = palette.track
    private val BEAD_TRACK get() = palette.beadTrack
    private val CHIP get() = palette.chip
    private val PANEL get() = palette.panel
    private val PANEL_BORDER get() = palette.panelBorder
    private val OUTLINE get() = palette.outline
    private var appearance = WidgetStore.Appearance()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val arabicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private enum class V { TOP, CENTER, BOTTOM }

    // ───────────────────────── entry point ─────────────────────────

    fun render(
        style: WidgetStyle,
        kind: WidgetKind,
        m: WidgetModel,
        wDp: Float,
        hDp: Float,
        density: Float,
        appearance: WidgetStore.Appearance = WidgetStore.Appearance()
    ): Bitmap {
        this.appearance = appearance
        val widthDp = wDp.coerceAtLeast(1f)
        val heightDp = hDp.coerceAtLeast(1f)
        var s = density
        val px = widthDp * heightDp * s * s
        if (px > MAX_PIXELS) s *= sqrt(MAX_PIXELS / px)       // RemoteViews bitmaps must stay small
        val bmp = Bitmap.createBitmap(max(1, (widthDp * s).roundToInt()), max(1, (heightDp * s).roundToInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        if (m.n == 0) { empty(c, widthDp, heightDp); return bmp }
        when (style) {
            WidgetStyle.CLASSIC -> when (kind) {
                WidgetKind.SMALL -> classicSmall(c, widthDp, heightDp, m)
                WidgetKind.MEDIUM -> classicMedium(c, widthDp, heightDp, m)
                WidgetKind.LARGE -> classicLarge(c, widthDp, heightDp, m)
            }
            WidgetStyle.CHECKLIST -> when (kind) {
                WidgetKind.MEDIUM -> checklistMedium(c, widthDp, heightDp, m)
                WidgetKind.LARGE -> checklistLarge(c, widthDp, heightDp, m)
                WidgetKind.SMALL -> error("Checklist does not support small widgets")
            }
            WidgetStyle.TASBIH -> if (kind == WidgetKind.LARGE) tasbihLarge(c, widthDp, heightDp, m) else tasbihSmall(c, widthDp, heightDp, m)
            WidgetStyle.CALLIGRAPHY -> if (kind == WidgetKind.SMALL) calligraphySmall(c, widthDp, heightDp, m) else calligraphyMedium(c, widthDp, heightDp, m)
        }
        return bmp
    }

    // ───────────────────────── 1 · CLASSIC ─────────────────────────

    private fun classicSmall(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        card(c, w, h, CARD_BORDER)
        val l = 15f; val r = w - 15f; val t = 15f; val b = h - 15f
        text(c, m.folderName.uppercase(), l, t + 8, 10f, GOLD, dmBold, Paint.Align.LEFT, 0.1f, r - l - 24)
        moon(c, r - 16, t, 16f)
        val barTop = b - 23
        bar(c, l, barTop, r, 4f, m.shown / m.target.toFloat())
        counterText(c, "${m.shown} / ${m.target}", r, b - 6.5f, 11f, TEXT, dmBold, Paint.Align.RIGHT)
        arabic(c, m.cur!!.arabic, l, t + 22, r, barTop - 8, 27f, 1.5f, TEXT)
    }

    private fun classicMedium(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        card(c, w, h, CARD_BORDER)
        val l = 17f; val r = w - 17f; val t = 17f; val b = h - 17f
        val leftR = r - 84f - 14f
        text(c, "${m.folderName.uppercase()} · ${strings.countOf(m.position, m.n)}", l, t + 6, 10f, GOLD, dmBold, Paint.Align.LEFT, 0.1f, leftR - l)
        arabic(c, m.cur!!.arabic, l, t + 16, leftR, b, 24f, 1.6f, TEXT, true, Layout.Alignment.ALIGN_CENTER, V.BOTTOM)
        val cx = r - 42f
        val top = h / 2f - 48f
        val cy = top + 38f
        ring(c, cx, cy, 31f, 6f, m.shown / m.target.toFloat())
        counterText(c, "${m.shown}", cx, cy - 6f, 20f, TEXT, dmBold, Paint.Align.CENTER)
        counterText(c, if (m.finished) strings.done else strings.of(m.target), cx, cy + 11f, 10f, MUTED, dmReg, Paint.Align.CENTER)
    }

    private fun classicLarge(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        card(c, w, h, CARD_BORDER)
        val l = 19f; val r = w - 19f; val t = 19f; val b = h - 19f
        header(c, m, l, r, t, "${m.position} / ${m.n}")
        val pTop = t + 45f
        val pBot = b - 52f - 12f
        val rect = RectF(l, pTop, r, pBot)
        c.drawRoundRect(rect, 18f, 18f, fillP(PANEL))
        c.drawRoundRect(RectF(l + .5f, pTop + .5f, r - .5f, pBot - .5f), 18f, 18f, strokeP(PANEL_BORDER, 1f))
        arabic(c, m.cur!!.arabic, l + 14, pTop + 14, r - 14, pBot - 14, 24f, 1.7f, GOLD_SOFT)
        val cy = b - 26f
        ring(c, l + 26f, cy, 21f, 5f, m.shown / m.target.toFloat())
        counterText(c, "${m.shown}", l + 26f, cy, 13f, TEXT, dmBold, Paint.Align.CENTER)
        button(c, RectF(r - 56f, cy - 22f, r, cy + 22f), if (m.finished) "↺" else "", true, 13f)
    }

    // ───────────────────────── 2 · CHECKLIST ─────────────────────────

    private fun stateOf(m: WidgetModel, i: Int) = when {
        m.finished || i < m.idx -> 0     // done
        i == m.idx -> 1                  // current
        else -> 2                        // pending
    }

    private fun checklistMedium(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        card(c, w, h, CARD_BORDER)
        val l = 17f; val r = w - 17f; val t = 15f
        text(c, m.folderName.uppercase(), l, t + 9, 10f, GOLD, dmBold, Paint.Align.LEFT, 0.1f, r - l - 50)
        counterText(c, "${m.done} / ${m.n}", r, t + 9, 11f, MUTED, dmReg, Paint.Align.RIGHT)
        val shown = min(3, m.n)
        val start = (m.curIdx - 1).coerceIn(0, m.n - shown)
        var y = t + 24f
        for (k in 0 until shown) {
            val i = start + k
            val rh = if (stateOf(m, i) == 1) 34f else 30f
            checkRow(c, m, i, y, rh, l, r, false)
            y += rh + 6f
        }
    }

    private fun checklistLarge(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        card(c, w, h, CARD_BORDER)
        val l = 17f; val r = w - 17f; val t = 17f; val b = h - 17f
        text(c, m.folderName, l, t + 9, 15f, TEXT, dmBold, Paint.Align.LEFT, 0f, r - l - 60)
        text(c, strings.azkar(m.n), l, t + 26.5f, 11f, MUTED, dmReg, Paint.Align.LEFT, 0f, r - l - 60)
        counterText(c, "${m.done} / ${m.n}", r, t + 8, 11f, GOLD, dmBold, Paint.Align.RIGHT)
        bar(c, l, t + 43f, r, 4f, m.done / m.n.toFloat())
        val listTop = t + 57f
        val maxRows = max(1, floor((b - listTop - 6f) / 40f).toInt())
        val shown = min(m.n, maxRows)
        val start = if (m.n <= maxRows) 0 else (m.curIdx - maxRows / 2).coerceIn(0, m.n - maxRows)
        var y = listTop
        for (k in 0 until shown) {
            val i = start + k
            val cur = stateOf(m, i) == 1
            if (cur) y += 4f
            val rh = if (cur) 42f else 40f
            checkRow(c, m, i, y, rh, l, r, !cur && k != shown - 1)
            y += rh
        }
    }

    private fun checkRow(c: Canvas, m: WidgetModel, i: Int, y: Float, rh: Float, l: Float, r: Float, divider: Boolean) {
        val state = stateOf(m, i)
        val cy = y + rh / 2
        var x0 = l; var x1 = r
        if (state == 1) {
            c.drawRoundRect(RectF(l - 6, y, r + 6, y + rh), 12f, 12f, fillP(PANEL))
            x0 = l + 4; x1 = r - 4
        }
        when (state) {
            0 -> checkCircle(c, x0 + 9, cy)
            1 -> c.drawCircle(x0 + 9, cy, 8f, strokeP(GOLD, 2f))
            else -> c.drawCircle(x0 + 9, cy, 8.25f, strokeP(OUTLINE, 1.5f))
        }
        val tx = x0 + 28
        if (state == 1) {
            counterText(c, "${m.count}/${m.items[i].target}", x1, cy, 12f, GOLD, dmBold, Paint.Align.RIGHT)
            text(c, m.labels[i], tx, cy, 13f, TEXT, dmBold, Paint.Align.LEFT, 0f, x1 - 44f - tx)
        } else {
            val aw = min(120f, (x1 - tx) * 0.55f)
            arabicLine(c, m.items[i].arabic, x1 - aw, x1, cy, 16f, MUTED)
            text(c, m.labels[i], tx, cy, 13f, if (state == 0) MUTED else TEXT, dmReg, Paint.Align.LEFT, 0f, x1 - aw - 8f - tx)
        }
        if (divider) c.drawLine(l, y + rh - .5f, r, y + rh - .5f, strokeP(PANEL_BORDER, 1f))
    }

    // ───────────────────────── 3 · TASBIH ─────────────────────────

    private fun tasbihSmall(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        card(c, w, h, CARD_BORDER)
        val s = (min(w, h) / 158f).coerceIn(0.8f, 1.4f)
        val cx = w / 2; val cy = h / 2
        beads(c, cx, cy, 50f * s, 6f * s, m.beadTotal, m.beadFilled)
        counterText(c, "${m.beadFilled}", cx, cy - 5f, 30f, TEXT, dmBold, Paint.Align.CENTER)
        counterText(c, strings.of(m.beadTotal), cx, cy + 19f, 10f, MUTED, dmReg, Paint.Align.CENTER)
    }

    private fun tasbihLarge(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        card(c, w, h, CARD_BORDER)
        val l = 19f; val r = w - 19f; val t = 19f; val b = h - 19f
        text(c, strings.tasbih, l, t + 9, 15f, TEXT, dmBold)
        counterText(c, strings.round(m.round, m.rounds, m.target), l, t + 26.5f, 11f, MUTED, dmReg, Paint.Align.LEFT, 0f, r - l - 70)
        chip(c, "${m.shown} / ${m.target}", r, t)
        val btnTop = b - 44f
        val top = t + 43f
        val bot = btnTop - 10f
        val outer = min(190f, min(bot - top, w - 38f))
        val s = outer / 190f
        val cx = w / 2; val cy = (top + bot) / 2
        beads(c, cx, cy, 80f * s, 9f * s, m.beadTotal, m.beadFilled)
        arabic(c, m.cur!!.arabic, cx - 58f * s, cy - 28f * s, cx + 58f * s, cy + 11f * s, 28f * s, 1.4f, TEXT)
        counterText(c, strings.countOf(m.beadFilled, m.beadTotal), cx, cy + 20.5f * s, 12f, MUTED, dmReg, Paint.Align.CENTER)
        button(c, RectF(l, btnTop, l + 72f, b), strings.reset, false, 13f)
        button(c, RectF(l + 82f, btnTop, r, b), "", true, 14f)
    }

    // ───────────────────────── 4 · CALLIGRAPHY ─────────────────────────

    private fun calligraphySmall(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        card(c, w, h, CARD_BORDER)
        val l = 15f; val r = w - 15f; val t = 15f; val b = h - 15f
        text(c, strings.dhikr, w / 2, t + 6, 10f, GOLD, dmBold, Paint.Align.CENTER, 0.12f)
        counterText(c, "${m.shown}", w / 2, b - 7.5f, 12f, MUTED, dmReg, Paint.Align.CENTER, 0f, r - l)
        arabic(c, m.cur!!.arabic, l, t + 18, r, b - 24, 34f, 1.4f, TEXT)
    }

    private fun calligraphyMedium(c: Canvas, w: Float, h: Float, m: WidgetModel) {
        card(c, w, h, CARD_BORDER)
        val l = 17f; val r = w - 17f; val t = 17f; val b = h - 17f
        text(c, "${m.folderName.uppercase()} · ${strings.countOf(m.position, m.n)}", w / 2, t + 6, 10f, GOLD, dmBold, Paint.Align.CENTER, 0.12f, r - l)
        if (m.n <= 24) segments(c, l, b - 3f, r, 3f, m.n, m.done) else bar(c, l, b - 3f, r, 3f, m.done / m.n.toFloat())
        arabic(c, m.cur!!.arabic, l, t + 20, r, b - 13f, 31f, 1.4f, TEXT)
    }

    private fun empty(c: Canvas, w: Float, h: Float) {
        card(c, w, h, CARD_BORDER)
        text(c, strings.noAzkar, w / 2, h / 2 - 8, 13f, TEXT, dmBold, Paint.Align.CENTER, 0f, w - 30)
        text(c, strings.tapToOpen, w / 2, h / 2 + 10, 11f, MUTED, dmReg, Paint.Align.CENTER, 0f, w - 30)
    }

    // ───────────────────────── shared pieces ─────────────────────────

    /** Title + subtitle on the left, count chip on the right (Classic large). */
    private fun header(c: Canvas, m: WidgetModel, l: Float, r: Float, t: Float, chipText: String) {
        text(c, m.folderName, l, t + 9, 15f, TEXT, dmBold, Paint.Align.LEFT, 0f, r - l - 70)
        text(c, strings.azkar(m.n), l, t + 26.5f, 11f, MUTED, dmReg, Paint.Align.LEFT, 0f, r - l - 70)
        chip(c, chipText, r, t)
    }

    private fun card(c: Canvas, w: Float, h: Float, border: Int) {
        val rect = RectF(.5f, .5f, w - .5f, h - .5f)
        val fill = if (appearance.backgroundColor != 0) appearance.backgroundColor else BLACK
        val radius = appearance.cornerRadiusDp.coerceIn(0f, min(w, h) / 2f)
        c.drawRoundRect(rect, radius, radius, fillP(fill))
        c.drawRoundRect(rect, radius, radius, strokeP(border, 1f))
    }

    private fun chip(c: Canvas, s: String, right: Float, top: Float) {
        tp.reset(); tp.isAntiAlias = true; tp.textSize = 11f; tp.typeface = dmBold
        val wd = tp.measureText(s) + 20f
        c.drawRoundRect(RectF(right - wd, top, right, top + 21f), 10.5f, 10.5f, fillP(CHIP))
        text(c, s, right - wd / 2, top + 10.5f, 11f, GOLD, dmBold, Paint.Align.CENTER)
    }

    private fun button(c: Canvas, r: RectF, label: String, filled: Boolean, size: Float) {
        val rad = r.height() / 2
        if (filled) c.drawRoundRect(r, rad, rad, fillP(GOLD))
        else c.drawRoundRect(RectF(r.left + .5f, r.top + .5f, r.right - .5f, r.bottom - .5f), rad, rad, strokeP(OUTLINE, 1f))
        text(c, label, r.centerX(), r.centerY(), size, if (filled) BLACK else TEXT, dmBold, Paint.Align.CENTER, 0f, r.width() - 8f)
    }

    private fun bar(c: Canvas, l: Float, t: Float, r: Float, h: Float, progress: Float) {
        c.drawRoundRect(RectF(l, t, r, t + h), h / 2, h / 2, fillP(TRACK))
        val pr = progress.coerceIn(0f, 1f)
        if (pr > 0f) c.drawRoundRect(RectF(l, t, l + (r - l) * pr, t + h), h / 2, h / 2, fillP(GOLD))
    }

    private fun segments(c: Canvas, l: Float, t: Float, r: Float, h: Float, total: Int, filled: Int) {
        val gap = 3f
        val sw = (r - l - gap * (total - 1)) / total
        for (i in 0 until total) {
            val x = l + i * (sw + gap)
            c.drawRoundRect(RectF(x, t, x + sw, t + h), 1.5f, 1.5f, fillP(if (i < filled) GOLD else TRACK))
        }
    }

    private fun ring(c: Canvas, cx: Float, cy: Float, r: Float, sw: Float, progress: Float) {
        c.drawCircle(cx, cy, r, strokeP(TRACK, sw))
        val pr = progress.coerceIn(0f, 1f)
        if (pr > 0f) c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * pr, false, strokeP(GOLD, sw, Paint.Cap.ROUND))
    }

    private fun beads(c: Canvas, cx: Float, cy: Float, r: Float, d: Float, total: Int, filled: Int) {
        for (i in 0 until total) {
            val a = Math.toRadians(-90.0 + 360.0 * i / total)
            c.drawCircle(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), d / 2, fillP(if (i < filled) GOLD else BEAD_TRACK))
        }
    }

    private val moonPath: Path by lazy { PathParser.createPathFromPathData("M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z") }
    private val checkPath: Path by lazy { PathParser.createPathFromPathData("M5 12.5l4.5 4.5L19 7.5") }

    private fun moon(c: Canvas, x: Float, y: Float, size: Float) {
        c.save(); c.translate(x, y); c.scale(size / 24f, size / 24f)
        val paint = strokeP(GOLD, 1.8f); paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
        c.drawPath(moonPath, paint)
        c.restore()
    }

    private fun checkCircle(c: Canvas, cx: Float, cy: Float) {
        c.drawCircle(cx, cy, 9f, fillP(GOLD))
        c.save(); c.translate(cx - 5.5f, cy - 5.5f); c.scale(11f / 24f, 11f / 24f)
        val paint = strokeP(BLACK, 3.2f); paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
        c.drawPath(checkPath, paint)
        c.restore()
    }

    // ───────────────────────── text ─────────────────────────

    private fun fillP(color: Int): Paint { p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = color; return p }
    private fun strokeP(color: Int, w: Float, cap: Paint.Cap = Paint.Cap.BUTT): Paint {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = w; p.strokeCap = cap; return p
    }

    /** Latin UI text, vertically centred on [cy]. */
    private fun text(
        c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int, tf: Typeface,
        align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f, maxW: Float = 0f,
    ) {
        tp.reset(); tp.isAntiAlias = true
        tp.textSize = size; tp.color = color; tp.typeface = tf; tp.textAlign = align; tp.letterSpacing = spacing
        var str = s
        if (maxW > 0f && tp.measureText(str) > maxW) str = TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString()
        val fm = tp.fontMetrics
        c.drawText(str, x, cy - (fm.ascent + fm.descent) / 2f, tp)
    }

    private fun counterText(
        c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int, tf: Typeface,
        align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f, maxW: Float = 0f,
    ) {
        if (appearance.showCounter) text(c, s, x, cy, size, color, tf, align, spacing, maxW)
    }

    private fun layout(s: String, w: Int, size: Float, lh: Float, color: Int, bold: Boolean, align: Layout.Alignment, maxLines: Int): StaticLayout {
        val t = arabicPaint
        t.reset()
        t.typeface = if (bold) amiriBold else amiriReg
        t.textSize = size; t.color = color
        val fm = t.fontMetrics
        val b = StaticLayout.Builder.obtain(s, 0, s.length, t, w)
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setLineSpacing(size * lh - (fm.descent - fm.ascent), 1f)
        if (maxLines > 0) b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setEllipsizedWidth(w)
        return b.build()
    }

    /** Arabic paragraph in a box. Shrinks the font until it fits. */
    private fun arabic(
        c: Canvas, s: String, l: Float, t: Float, r: Float, b: Float, size: Float, lh: Float, color: Int,
        bold: Boolean = true, align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER, v: V = V.CENTER,
    ) {
        val w = max(1, (r - l).toInt())
        val configuredSize = appearance.fontSizeSp.takeIf { it > 0f } ?: size
        val configuredLineHeight = appearance.lineHeight.takeIf { it > 0f } ?: lh
        val configuredColor = appearance.textColor.takeIf { it != 0 } ?: color
        val configuredAlign = when (appearance.alignment) {
            0 -> Layout.Alignment.ALIGN_NORMAL
            2 -> Layout.Alignment.ALIGN_OPPOSITE
            else -> align
        }
        var sz = configuredSize
        var lay = layout(s, w, sz, configuredLineHeight, configuredColor, bold, configuredAlign, 0)
        while (lay.height > (b - t) && sz > configuredSize * 0.5f) {
            sz *= 0.93f
            lay = layout(s, w, sz, configuredLineHeight, configuredColor, bold, configuredAlign, 0)
        }
        val y = when (v) { V.TOP -> t; V.CENTER -> t + ((b - t) - lay.height) / 2f; V.BOTTOM -> b - lay.height }
        c.save(); c.translate(l, y); lay.draw(c); c.restore()
    }

    /** One Arabic line, right-aligned, ellipsized, centred on [cy]. */
    private fun arabicLine(c: Canvas, s: String, l: Float, r: Float, cy: Float, size: Float, color: Int) {
        val lay = layout(s, max(1, (r - l).toInt()), size, 1.2f, color, false, Layout.Alignment.ALIGN_NORMAL, 1)
        c.save(); c.translate(l, cy - lay.height / 2f); lay.draw(c); c.restore()
    }

    private companion object { const val MAX_PIXELS = 1_100_000f }
}
