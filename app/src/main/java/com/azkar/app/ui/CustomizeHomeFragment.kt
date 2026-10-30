package com.azkar.app.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.content.res.ColorStateList
import com.azkar.app.R
import com.azkar.app.data.Builtins
import com.azkar.app.data.Placement
import com.azkar.app.data.Store

class CustomizeHomeFragment : Fragment(R.layout.fragment_customize_home) {

    private data class Block(val h: Int, val fill: Int?, val stroke: Int, val dashed: Boolean, val fg: Int, val label: String)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        fun c(id: Int) = ctx.getColor(id)

        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }
        view.findViewById<View>(R.id.btn_done).setOnClickListener { nav().pop() }
        view.findViewById<View>(R.id.btn_reset).setOnClickListener { Store.resetHome(); renderAll(view) }
        renderAll(view)
    }

    private fun renderAll(view: View) {
        val ctx = requireContext()
        fun c(id: Int) = ctx.getColor(id)
        val gold = c(R.color.gold)

        // ----- Preview -----
        val grid = Block(62, c(R.color.card), c(R.color.border), false, c(R.color.muted), "Collections")
        val mixed = Block(78, c(R.color.card), gold, true, gold, "Collections + my folders")
        val folders = Block(30, null, gold, true, gold, "My folders")
        val pos = Store.placement()
        val blocks = when (pos) {
            Placement.TOP -> listOf(folders, grid)
            Placement.MIXED -> listOf(mixed)
            Placement.BOTTOM -> listOf(grid, folders)
        }
        val preview = view.findViewById<LinearLayout>(R.id.preview)
        preview.removeAllViews()
        blocks.forEachIndexed { i, b ->
            val tv = TextView(ctx, null, 0, R.style.TB).apply {
                text = b.label
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(b.fg)
                background = Ui.shape(ctx, b.fill, b.stroke, 8f, b.dashed)
            }
            preview.addView(tv, Ui.lp(h = Ui.pxi(ctx, b.h.toFloat()), top = if (i > 0) 6 else 0, ctx = ctx))
        }

        // ----- Placement radios -----
        val options = view.findViewById<LinearLayout>(R.id.options)
        options.removeAllViews()
        Placement.values().forEach { p ->
            val on = p == pos
            val v = layoutInflater.inflate(R.layout.item_radio, options, false)
            v.findViewById<TextView>(R.id.label).apply {
                text = p.label
                typeface = Ui.font(ctx, if (on) R.font.dm_sans_bold else R.font.dm_sans_medium)
            }
            v.findViewById<View>(R.id.ring).background = GradientRing(ctx, if (on) gold else c(R.color.border2)).build()
            v.findViewById<View>(R.id.dot).background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(if (on) gold else 0)
            }
            v.setOnClickListener { Store.setPlacement(p); renderAll(view) }
            options.addView(v)
        }

        // ----- Order & visibility -----
        val rows = view.findViewById<LinearLayout>(R.id.rows)
        rows.removeAllViews()
        val ids = Store.homeOrder()
        val hidden = Store.hidden()
        val last = ids.size - 1
        ids.forEachIndexed { idx, id ->
            val builtin = Builtins.homeCollections.firstOrNull { it.key == id }
            val isHidden = id in hidden
            val v = layoutInflater.inflate(R.layout.item_customize_row, rows, false)
            v.alpha = if (isHidden) 0.55f else 1f
            v.findViewById<ImageView>(R.id.ic).apply {
                setImageResource(if (builtin != null) R.drawable.ic_book_1p75 else R.drawable.ic_folder_1p75)
                imageTintList = ColorStateList.valueOf(c(if (builtin != null) R.color.teal else R.color.gold))
            }
            v.findViewById<TextView>(R.id.name).text = builtin?.title ?: Store.folder(id)?.name ?: ""
            v.findViewById<View>(R.id.up).alpha = if (idx == 0) 0.35f else 1f
            v.findViewById<View>(R.id.down).alpha = if (idx == last) 0.35f else 1f
            fun move(d: Int) {
                val a = ids.toMutableList(); val t = a[idx]; a[idx] = a[idx + d]; a[idx + d] = t
                Store.setHomeOrder(a); renderAll(view)
            }
            v.findViewById<View>(R.id.up).setOnClickListener { if (idx > 0) move(-1) }
            v.findViewById<View>(R.id.down).setOnClickListener { if (idx < last) move(1) }
            v.findViewById<ImageView>(R.id.eye).apply {
                setImageResource(if (isHidden) R.drawable.ic_eyeoff_1p75 else R.drawable.ic_eye_1p75)
                imageTintList = ColorStateList.valueOf(if (isHidden) c(R.color.muted) else gold)
            }
            v.findViewById<View>(R.id.toggle).setOnClickListener {
                Store.setHidden(if (isHidden) hidden - id else hidden + id); renderAll(view)
            }
            rows.addView(v, Ui.lp(top = if (idx > 0) 6 else 0, ctx = ctx))
        }
    }

    /** 22dp circle with a 2dp ring. */
    private class GradientRing(val ctx: android.content.Context, val color: Int) {
        fun build() = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0)
            setStroke(Ui.pxi(ctx, 2f), color)
        }
    }
}
