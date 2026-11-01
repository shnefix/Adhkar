
package com.azkar.app.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.azkar.app.R
import com.azkar.app.data.Store

/**
 * Shared behaviour of the Add zikr and Edit zikr screens:
 * repeat stepper and folder chips.
 */
class ZikrForm(
    private val view: View,
    private val allowNew: Boolean,
    private val onSelChanged: (String?) -> Unit = {}
) {
    /** Last valid repeat count. */
    private var count = 1

    /** Prevents TextWatcher recursion during programmatic updates. */
    private var syncing = false

    var n: Int
        get() = box.text.toString()
            .toIntOrNull()
            ?.takeIf { it >= 1 }
            ?.coerceAtMost(999) ?: count
        set(v) {
            count = v.coerceIn(1, 999)
            syncBox()
        }

    var sel: String? = null
        set(v) {
            field = v
            renderChips()
            onSelChanged(v)
        }

    val arabic: EditText = view.findViewById(R.id.ar)
    private val box: EditText = view.findViewById(R.id.n)

    init {
        view.findViewById<View>(R.id.btn_dec).setOnClickListener {
            n = n - 1
        }

        view.findViewById<View>(R.id.btn_inc).setOnClickListener {
            n = n + 1
        }

        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {}

            override fun afterTextChanged(s: Editable?) {
                if (syncing) return

                s?.toString()
                    ?.toIntOrNull()
                    ?.takeIf { it >= 1 }
                    ?.let { count = it.coerceAtMost(999) }

                updateDec()
            }
        })

        box.setOnFocusChangeListener { _, focused ->
            if (!focused) syncBox()
        }

        box.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                syncBox()
            }
            false
        }

        syncBox()
    }

    private fun syncBox() {
        syncing = true
        try {
            val value = count.toString()
            if (box.text.toString() != value) {
                box.setText(value)
            }
        } finally {
            syncing = false
        }

        updateDec()
    }

    private fun updateDec() {
        view.findViewById<View>(R.id.btn_dec).isEnabled = n > 1
    }

    fun renderChips() {
        val ctx = view.context
        val chips = view.findViewById<FlowLayout>(R.id.chips)

        chips.removeAllViews()

        val choices = Store.folders().map {
            it.id to ((it.icon?.let { e -> "$e " } ?: "") + it.name)
        }

        choices.forEach { (k, label) ->
            chips.addView(
                Ui.chip(ctx, label, sel == k, false) {
                    sel = k
                }
            )
        }

        if (allowNew) {
            chips.addView(
                Ui.chip(ctx, "+ New folder", sel == "new", true) {
                    sel = "new"
                }
            )
        }
    }
}
