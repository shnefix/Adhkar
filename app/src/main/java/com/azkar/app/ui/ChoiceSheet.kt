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
 * the nudge lead time (chips + a timeline).
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
