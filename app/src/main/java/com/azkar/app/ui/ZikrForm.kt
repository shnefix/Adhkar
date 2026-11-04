
package com.azkar.app.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.azkar.app.R
import com.azkar.app.data.Store
import com.azkar.app.data.TitlePos

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

    // Optional title, off by default: a line shown above (or below) the zikr in the reader.
    private val titleSwitch: ToggleSwitch = view.findViewById(R.id.sw_title)
    private val titleBox: View = view.findViewById(R.id.title_box)
    private val titleInput: EditText = view.findViewById(R.id.zikr_title)
    private var pos = TitlePos.ABOVE

    /** The title to save: blank while the switch is off. */
    val title: String get() = if (titleSwitch.on) titleInput.text.toString().trim() else ""
    /** Where the title shows: above, below, both, or kept but hidden. */
    val titlePos: TitlePos get() = if (title.isNotEmpty()) pos else TitlePos.ABOVE

    /** Fills the title controls; the switch starts on only when there is a title. */
    fun setTitle(text: String, at: TitlePos) {
        titleInput.setText(text)
        pos = at
        titleSwitch.on = text.isNotBlank()
        renderTitle()
    }

    private fun renderTitle() {
        titleBox.visibility = if (titleSwitch.on) View.VISIBLE else View.GONE
        val ctx = view.context
        val box = view.findViewById<android.widget.LinearLayout>(R.id.title_pos)
        box.orientation = android.widget.LinearLayout.VERTICAL
        box.removeAllViews()
        // Two rows of two: above / below, then both / hidden.
        var row = android.widget.LinearLayout(ctx)
        listOf(
            TitlePos.ABOVE to R.string.zikr_title_above, TitlePos.BELOW to R.string.zikr_title_below,
            TitlePos.BOTH to R.string.zikr_title_both, TitlePos.HIDDEN to R.string.zikr_title_hidden
        ).forEachIndexed { k, (option, label) ->
            if (k % 2 == 0) {
                row = android.widget.LinearLayout(ctx)
                box.addView(row, android.widget.LinearLayout.LayoutParams(-1, -2).apply { if (k > 0) topMargin = Ui.pxi(ctx, 8f) })
            }
            val i = k % 2
            val on = pos == option
            val b = android.widget.TextView(ctx, null, 0, R.style.TB).apply {
                setText(label)
                textSize = 15f
                gravity = android.view.Gravity.CENTER
                setTextColor(ctx.getColor(if (on) R.color.bg else R.color.text))
                background = Ui.shape(ctx, if (on) ctx.getColor(R.color.gold) else null, ctx.getColor(if (on) R.color.gold else R.color.border2), 14f)
                isClickable = true
                isFocusable = true
                setOnClickListener { pos = option; renderTitle() }
            }
            val p = android.widget.LinearLayout.LayoutParams(0, Ui.pxi(ctx, 44f), 1f)
            if (i > 0) p.marginStart = Ui.pxi(ctx, 8f)
            row.addView(b, p)
        }
    }

    init {
        titleSwitch.onToggle = { on ->
            renderTitle()
            if (on) titleInput.requestFocus()
        }
        renderTitle()

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
                Ui.chip(ctx, ctx.getString(R.string.new_folder_chip), sel == "new", true) {
                    sel = "new"
                }
            )
        }
    }
}
