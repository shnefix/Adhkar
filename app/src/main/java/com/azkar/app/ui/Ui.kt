package com.azkar.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.res.ResourcesCompat
import com.azkar.app.MainActivity
import com.azkar.app.R
import androidx.fragment.app.Fragment

fun Fragment.nav(): MainActivity = requireActivity() as MainActivity

object Ui {
    fun px(ctx: Context, dp: Float): Float = dp * ctx.resources.displayMetrics.density
    fun pxi(ctx: Context, dp: Float): Int = px(ctx, dp).toInt()
    fun color(ctx: Context, @ColorRes id: Int) = ctx.getColor(id)

    fun shape(ctx: Context, fill: Int?, stroke: Int?, radiusDp: Float, dashed: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = px(ctx, radiusDp)
            if (fill != null) setColor(fill) else setColor(0)
            if (stroke != null) {
                if (dashed) setStroke(pxi(ctx, 1f), stroke, px(ctx, 3f), px(ctx, 3f))
                else setStroke(pxi(ctx, 1f), stroke)
            }
        }

    fun font(ctx: Context, id: Int) = ResourcesCompat.getFont(ctx, id)

    /** Small helper for the "Caps" section labels. */
    fun caps(ctx: Context, text: String): TextView =
        TextView(ctx, null, 0, R.style.Caps).apply { this.text = text }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0, ctx: Context? = null): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h).also { if (ctx != null && top != 0) it.topMargin = pxi(ctx, top.toFloat()) }

    /** Rows of [cols] equally wide cells with [gap]dp between them. */
    fun grid(ctx: Context, cells: List<View>, cols: Int, gap: Int): LinearLayout {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        cells.chunked(cols).forEachIndexed { r, rowCells ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until cols) {
                val cell = rowCells.getOrNull(c) ?: View(ctx)
                val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (c > 0) p.marginStart = pxi(ctx, gap.toFloat())
                row.addView(cell, p)
            }
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                if (r > 0) it.topMargin = pxi(ctx, gap.toFloat())
            })
        }
        return root
    }

    /** Folder/choice chip used on Add and Edit zikr. */
    fun chip(ctx: Context, label: String, on: Boolean, isNew: Boolean, onClick: () -> Unit): TextView {
        val gold = ctx.getColor(R.color.gold)
        return TextView(ctx, null, 0, R.style.TB).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            minHeight = pxi(ctx, 44f)
            setPadding(pxi(ctx, 16f), 0, pxi(ctx, 16f), 0)
            setTextColor(if (on) ctx.getColor(R.color.bg) else if (isNew) gold else ctx.getColor(R.color.text))
            background = if (on) shape(ctx, gold, gold, 22f)
            else if (isNew) shape(ctx, null, gold, 22f, dashed = true)
            else shape(ctx, null, ctx.getColor(R.color.border2), 22f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }
}
