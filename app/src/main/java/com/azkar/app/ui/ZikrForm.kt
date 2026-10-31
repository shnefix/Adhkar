package com.azkar.app.ui

import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.azkar.app.R
import com.azkar.app.data.Builtins
import com.azkar.app.data.Store

/**
 * Shared behaviour of the Add zikr and Edit zikr screens: repeat stepper and folder chips.
 * [sel] is a folder key, "new" (Add only) or null.
 */
class ZikrForm(private val view: View, private val allowNew: Boolean, private val onSelChanged: (String?) -> Unit = {}) {
    var n = 3
        set(v) { field = v; renderStepper() }
    var sel: String? = null
        set(v) { field = v; renderChips(); onSelChanged(v) }

    val arabic: EditText = view.findViewById(R.id.ar)
    val note: EditText? = view.findViewById(R.id.note)

    init {
        view.findViewById<View>(R.id.btn_dec).setOnClickListener { if (n > 1) n-- }
        view.findViewById<View>(R.id.btn_inc).setOnClickListener { n = minOf(999, n + 1) }
        renderStepper()
    }

    private fun renderStepper() {
        view.findViewById<TextView>(R.id.n).text = n.toString()
        view.findViewById<View>(R.id.btn_dec).alpha = if (n > 1) 1f else 0.4f
    }

    fun renderChips() {
        val ctx = view.context
        val box = view.findViewById<FlowLayout>(R.id.chips)
        box.removeAllViews()
        val choices = Store.folders().map { it.id to ((it.icon?.let { e -> "$e " } ?: "") + it.name) }
        choices.forEach { (k, label) ->
            box.addView(Ui.chip(ctx, label, sel == k, false) { sel = k })
        }
        if (allowNew) box.addView(Ui.chip(ctx, "+ New folder", sel == "new", true) { sel = "new" })
    }
}
