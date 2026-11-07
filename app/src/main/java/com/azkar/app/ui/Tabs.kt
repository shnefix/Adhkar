package com.azkar.app.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.azkar.app.R

enum class Tab(val root: Int, val ic: Int, val tx: Int) {
    HOME(R.id.nav_home, R.id.nav_home_ic, R.id.nav_home_tx),
    LIBRARY(R.id.nav_library, R.id.nav_library_ic, R.id.nav_library_tx),
    WIDGETS(R.id.nav_widgets, R.id.nav_widgets_ic, R.id.nav_widgets_tx)
}

/** Colours the bottom bar for [active] (gold + bold) and wires tab switching. */
fun Fragment.bindNav(view: View, active: Tab) {
    val ctx = requireContext()
    val gold = ctx.getColor(R.color.gold)
    val muted = ctx.getColor(R.color.muted)
    val bold = Ui.font(ctx, R.font.dm_sans_bold)
    val medium = Ui.font(ctx, R.font.dm_sans_medium)
    for (t in Tab.values()) {
        val on = t == active
        view.findViewById<ImageView>(t.ic).imageTintList = ColorStateList.valueOf(if (on) gold else muted)
        view.findViewById<TextView>(t.tx).apply {
            setTextColor(if (on) gold else muted)
            typeface = if (on) bold else medium
        }
        view.findViewById<View>(t.root).setOnClickListener { nav().openTab(t) }
    }
    bindNavInsets(view, view.findViewById(R.id.list) ?: view.findViewById(R.id.content))
}
