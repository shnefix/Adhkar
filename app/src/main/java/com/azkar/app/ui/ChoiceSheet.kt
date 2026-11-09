package com.azkar.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.azkar.app.R
import com.azkar.app.data.AppPrefs

/**
 * Bottom sheets that replace the plain list dialogs for settings that are easier to pick by seeing them:
 * the source-page style (two drawn previews side by side) and the nudge lead time (chips + a timeline).
 */
object ChoiceSheet {

    private class Sheet(val ctx: Context) {
        val dialog = Dialog(ctx)
        fun dp(v: Float) = Ui.pxi(ctx, v)
        val gold = ctx.getColor(R.color.gold)
        val ink = ctx.getColor(R.color.text)
        val muted = ctx.getColor(R.color.muted)
        val border = ctx.getColor(R.color.border)
        val card2 = ctx.getColor(R.color.card2)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_sheet_top)
            setPadding(dp(20f), dp(12f), dp(20f), dp(24f))
            addView(View(ctx).apply { background = Ui.shape(ctx, ctx.getColor(R.color.border2), null, 2f) },
                LinearLayout.LayoutParams(dp(40f), dp(4f)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(16f) })
        }

        fun title(res: Int, sub: Int?) {
            root.addView(TextView(ctx, null, 0, R.style.TB).apply { setText(res); textSize = 19f; setTextColor(ink) })
            if (sub != null) root.addView(TextView(ctx, null, 0, R.style.T).apply {
                setText(sub); textSize = 13.5f; setTextColor(muted); setLineSpacing(0f, 1.25f)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })
        }

        fun show() {
            dialog.setContentView(root)
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setGravity(Gravity.BOTTOM)
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
                setWindowAnimations(android.R.style.Animation_InputMethod)
            }
            dialog.show()
        }
    }

    // ---------------------------------------------------------------- Source page style

    fun showSourceStyle(ctx: Context, onPicked: () -> Unit) {
        val s = Sheet(ctx)
        s.title(R.string.source_style, R.string.source_style_pick_sub)
        val row = LinearLayout(ctx)
        val options = listOf(
            Triple(AppPrefs.SOURCE_TABS, R.string.source_style_tabs, R.string.source_style_tabs_sub),
            Triple(AppPrefs.SOURCE_SHEET, R.string.source_style_sheet, R.string.source_style_sheet_sub),
        )
        options.forEachIndexed { i, (key, label, sub) ->
            val selected = AppPrefs.sourceStyle() == key || (i == 0 && options.none { it.first == AppPrefs.sourceStyle() })
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(s.dp(10f), s.dp(10f), s.dp(10f), s.dp(14f))
                background = Ui.shape(ctx, if (selected) s.card2 else null, if (selected) s.gold else s.border, 20f)
                isClickable = true; isFocusable = true
                contentDescription = ctx.getString(label)
                setOnClickListener {
                    AppPrefs.setSourceStyle(key); onPicked()
                    s.dialog.dismiss()
                }
            }
            val frame = FrameLayout(ctx)
            frame.addView(SourcePreview(ctx, sheet = key == AppPrefs.SOURCE_SHEET), FrameLayout.LayoutParams(-1, s.dp(150f)))
            if (selected) frame.addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_check_2p25); setColorFilter(ctx.getColor(R.color.bg))
                background = Ui.shape(ctx, s.gold, null, 12f); setPadding(s.dp(4f), s.dp(4f), s.dp(4f), s.dp(4f))
            }, FrameLayout.LayoutParams(s.dp(24f), s.dp(24f), Gravity.TOP or Gravity.END).apply { setMargins(0, s.dp(6f), s.dp(6f), 0) })
            col.addView(frame)
            col.addView(TextView(ctx, null, 0, R.style.TB).apply {
                setText(label); textSize = 14.5f; setTextColor(if (selected) s.gold else s.ink)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.dp(12f) })
            col.addView(TextView(ctx, null, 0, R.style.T).apply {
                setText(sub); textSize = 12.5f; setTextColor(s.muted); setLineSpacing(0f, 1.2f)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.dp(4f) })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (i == 0) marginEnd = s.dp(6f) else marginStart = s.dp(6f)
            })
        }
        s.root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.dp(18f) })
        s.show()
    }

    /** A small drawn mock of the reader: either the card turned over with tabs, or a sheet risen over it. */
    private class SourcePreview(ctx: Context, private val sheet: Boolean) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val d = ctx.resources.displayMetrics.density
        private val bg = ctx.getColor(R.color.bg)
        private val card = ctx.getColor(R.color.card)
        private val card2 = ctx.getColor(R.color.card2)
        private val line = ctx.getColor(R.color.border2)
        private val gold = ctx.getColor(R.color.gold)
        private val arabic = ctx.getColor(R.color.arabic)

        private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int, alpha: Int = 255) {
            p.color = color; p.alpha = alpha; c.drawRoundRect(RectF(l, t, r, b), rad * d, rad * d, p); p.alpha = 255
        }
        private fun bar(c: Canvas, l: Float, t: Float, w: Float, color: Int, h: Float = 4f * d) = rr(c, l, t, l + w, t + h, 2f, color)

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val m = 8 * d
            rr(c, 0f, 0f, w, h, 14f, bg)
            // The reader card
            rr(c, m, m, w - m, h - m, 12f, card)
            val inner = m + 9 * d; val iw = w - 2 * inner
            if (!sheet) {
                // Tabs row: Hadith (active) · Chain · Takhrij
                val tw = (iw - 8 * d) / 3
                for (i in 0..2) rr(c, inner + i * (tw + 4 * d), inner, inner + i * (tw + 4 * d) + tw, inner + 14 * d, 7f, if (i == 0) gold else card2)
                var y = inner + 26 * d
                listOf(1f, .85f, .95f, .7f).forEach { f -> bar(c, inner + iw * (1 - f), y, iw * f, arabic); y += 11 * d }
                y += 4 * d
                listOf(.6f, .45f).forEach { f -> bar(c, inner, y, iw * f, line, 3f * d); y += 9 * d }
            } else {
                // Dimmed zikr behind
                var y = inner + 4 * d
                listOf(1f, .8f, .9f).forEach { f -> bar(c, inner + iw * (1 - f), y, iw * f, arabic); y += 11 * d }
                rr(c, m, m, w - m, h - m, 12f, bg, 140)
                // Sheet rising from the bottom
                val top = h * 0.42f
                rr(c, m - 2 * d, top, w - m + 2 * d, h + 12 * d, 12f, card2)
                rr(c, w / 2 - 10 * d, top + 6 * d, w / 2 + 10 * d, top + 9 * d, 2f, line)
                var sy = top + 18 * d
                bar(c, inner, sy, iw * .35f, gold, 5f * d); sy += 13 * d
                listOf(.95f, .8f).forEach { f -> bar(c, inner + iw * (1 - f), sy, iw * f, arabic); sy += 10 * d }
                bar(c, inner, sy, iw * .5f, line, 3f * d)
            }
        }
    }

    // ---------------------------------------------------------------- Nudge lead time

    fun showNudgeLead(ctx: Context, choices: List<Int>, onPicked: (Int) -> Unit) {
        val s = Sheet(ctx)
        s.title(R.string.nudge_lead_title, R.string.nudge_lead_sub)
        var current = AppPrefs.nudgeLeadMinutes()

        val big = TextView(ctx, null, 0, R.style.TB).apply { textSize = 40f; setTextColor(s.gold); gravity = Gravity.CENTER }
        val unit = TextView(ctx, null, 0, R.style.T).apply { textSize = 13.5f; setTextColor(s.muted); gravity = Gravity.CENTER }
        val timeline = LeadTimeline(ctx)
        val chipRow = LinearLayout(ctx)
        val chips = mutableListOf<TextView>()

        fun render() {
            big.text = ctx.getString(R.string.nudge_lead_big, current)
            unit.setText(R.string.nudge_lead_unit)
            timeline.minutes = current
            chips.forEachIndexed { i, chip ->
                val on = choices[i] == current
                chip.setTextColor(if (on) ctx.getColor(R.color.bg) else s.ink)
                chip.background = Ui.shape(ctx, if (on) s.gold else s.card2, if (on) null else s.border, 18f)
            }
        }

        s.root.addView(big, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.dp(20f) })
        s.root.addView(unit, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.dp(4f) })
        s.root.addView(timeline, LinearLayout.LayoutParams(-1, s.dp(64f)).apply { topMargin = s.dp(14f) })
        choices.forEachIndexed { i, m ->
            val chip = TextView(ctx, null, 0, R.style.TB).apply {
                text = ctx.getString(R.string.nudge_lead_chip, m); textSize = 14f; gravity = Gravity.CENTER
                isClickable = true; isFocusable = true
                setOnClickListener { current = m; render() }
            }
            chips += chip
            chipRow.addView(chip, LinearLayout.LayoutParams(0, s.dp(40f), 1f).apply {
                if (i > 0) marginStart = s.dp(8f)
            })
        }
        s.root.addView(chipRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = s.dp(16f) })
        s.root.addView(TextView(ctx, null, 0, R.style.TB).apply {
            setText(R.string.done); textSize = 15f; gravity = Gravity.CENTER; setTextColor(ctx.getColor(R.color.bg))
            background = Ui.shape(ctx, s.gold, null, 24f)
            isClickable = true; isFocusable = true
            setOnClickListener { s.dialog.dismiss(); onPicked(current) }
        }, LinearLayout.LayoutParams(-1, s.dp(48f)).apply { topMargin = s.dp(22f) })
        render()
        s.show()
    }

    /** "🔔 nudge ——— X min ——— Dhuhr / Maghrib": a line whose bell slides further left the earlier it is. */
    private class LeadTimeline(ctx: Context) : View(ctx) {
        var minutes = 30
            set(v) { field = v; invalidate() }
        private val d = ctx.resources.displayMetrics.density
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val gold = ctx.getColor(R.color.gold)
        private val teal = ctx.getColor(R.color.teal)
        private val track = ctx.getColor(R.color.track)
        private val muted = ctx.getColor(R.color.muted)
        private val prayer = ctx.getString(R.string.nudge_lead_prayer)
        private val nudge = ctx.getString(R.string.nudge_lead_nudge)

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val y = 22 * d; val pad = 14 * d
            val end = w - pad
            val frac = (minutes.coerceIn(0, 90) / 90f)
            val start = end - (end - pad) * (0.18f + 0.82f * frac)
            p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 4 * d
            p.color = track; c.drawLine(pad, y, end, y, p)
            p.color = gold; c.drawLine(start, y, end, y, p)
            p.style = Paint.Style.FILL
            p.color = gold; c.drawCircle(start, y, 8 * d, p)
            p.color = teal; c.drawCircle(end, y, 8 * d, p)
            p.textSize = 12 * d; p.color = muted
            p.textAlign = Paint.Align.CENTER
            val ny = y + 28 * d
            c.drawText(nudge, start.coerceAtLeast(pad + p.measureText(nudge) / 2), ny, p)
            c.drawText(prayer, (end).coerceAtMost(w - p.measureText(prayer) / 2), ny, p)
        }
    }
}
