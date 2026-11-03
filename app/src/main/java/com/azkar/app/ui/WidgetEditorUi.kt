package com.azkar.app.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.azkar.app.R

/** Design tokens of the "Customize widget" design (Main / Behavior). */
internal object EditorColors {
    val Bg = 0xFF0B0B0B.toInt()
    val Text = 0xFFF2F2F2.toInt()
    val Muted = 0xFFA8A8A8.toInt()
    val Gold = 0xFFE0BF6E.toInt()
    val Card = 0xFF141414.toInt()
    val Tabs = 0xFF161616.toInt()
    val Border = 0xFF2A2A2A.toInt()
    val Chip = 0xFF1E1E1E.toInt()
    val ChipBorder = 0xFF2F2F2F.toInt()
    val ChipText = 0xFFD0D0D0.toInt()
    val Custom = 0xFF0F0F0F.toInt()
    val Dot = 0xFF3A3A3A.toInt()
}

internal val EditorTextColors = listOf(0xFFEEEEEE.toInt(), 0xFFE0BF6E.toInt(), 0xFFC8A24C.toInt(), 0xFF9A9A9A.toInt())
internal val EditorBgColors = listOf(0xFF000000.toInt(), 0xFF0B0B0B.toInt(), 0xFF161616.toInt(), 0xFF0F0F0F.toInt())

internal const val MIN_INTERVAL_MINUTES = 15
private const val MAX_INTERVAL_MINUTES = 10080 // one week

internal fun editorShape(fill: Int, radiusDp: Float, ctx: Context, stroke: Int = 0, strokeDp: Float = 1f) =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = Ui.px(ctx, radiusDp)
        if (stroke != 0) setStroke(Ui.pxi(ctx, strokeDp), stroke)
    }

internal fun editorText(ctx: Context, text: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false) =
    TextView(ctx, null, 0, R.style.WidgetEditorBody).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

internal fun LinearLayout.addSpaced(child: View, topDp: Float = 14f) {
    addView(child, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).also { it.topMargin = Ui.pxi(context, topDp) })
}

internal fun sectionCard(ctx: Context, title: String, subtitle: String? = null): LinearLayout =
    LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = editorShape(EditorColors.Card, 20f, ctx)
        val pad = Ui.pxi(ctx, 18f)
        setPadding(pad, pad, pad, pad)
        addView(editorText(ctx, title.uppercase(), 12f, EditorColors.Muted, bold = true).apply { letterSpacing = 0.12f })
        if (subtitle != null) addSpaced(editorText(ctx, subtitle, 13f, EditorColors.Muted), 4f)
    }

internal fun <T> chipGroup(ctx: Context, options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit): View {
    val group = FlowLayout(ctx)
    options.forEach { (value, label) ->
        val on = value == selected
        group.addView(TextView(ctx, null, 0, R.style.WidgetEditorBody).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (on) EditorColors.Bg else EditorColors.ChipText)
            setTypeface(typeface, if (on) Typeface.BOLD else Typeface.NORMAL)
            setPadding(Ui.pxi(ctx, 16f), 0, Ui.pxi(ctx, 16f), 0)
            background = editorShape(
                if (on) EditorColors.Gold else EditorColors.Chip, 22f, ctx,
                if (on) EditorColors.Gold else EditorColors.ChipBorder
            )
            setOnClickListener { onSelect(value) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.pxi(ctx, 44f)).also {
            it.marginEnd = Ui.pxi(ctx, 8f)
            it.bottomMargin = Ui.pxi(ctx, 8f)
        })
    }
    return group
}

internal fun segmented(ctx: Context, options: List<String>, selected: Int, onSelect: (Int) -> Unit): View =
    LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        background = editorShape(EditorColors.Bg, 12f, ctx)
        val pad = Ui.pxi(ctx, 3f)
        setPadding(pad, pad, pad, pad)
        options.forEachIndexed { i, label ->
            val on = i == selected
            addView(TextView(ctx, null, 0, R.style.WidgetEditorBody).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(if (on) EditorColors.Gold else EditorColors.Muted)
                if (on) background = editorShape(EditorColors.Border, 10f, ctx)
                setOnClickListener { onSelect(i) }
            }, LinearLayout.LayoutParams(0, Ui.pxi(ctx, 44f), 1f))
        }
    }

internal fun valueRow(ctx: Context, label: String, value: String? = null): View =
    LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(editorText(ctx, label, 15f, EditorColors.Text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (value != null) addView(editorText(ctx, value, 15f, EditorColors.Gold, bold = true))
    }

/** Label + live value on one row and a gold slider under it. Dragging never rebuilds the screen. */
internal fun sliderRow(
    ctx: Context, label: String, minValue: Int, maxValue: Int, current: Int,
    format: (Int) -> String, onChange: (Int) -> Unit
): View {
    val valueView = editorText(ctx, format(current), 15f, EditorColors.Gold, bold = true)
    val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    column.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(editorText(ctx, label, 15f, EditorColors.Text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(valueView)
    })
    val seek = SeekBar(ctx, null, 0, R.style.WidgetEditorSeekBar).apply {
        max = maxValue - minValue
        progress = (current - minValue).coerceIn(0, maxValue - minValue)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = progress + minValue
                valueView.text = format(v)
                onChange(v)
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }
    column.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(ctx, 40f)))
    return column
}

internal fun colorRow(ctx: Context, label: String, colors: List<Int>, selected: Int, onSelect: (Int) -> Unit): View {
    val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    column.addView(valueRow(ctx, label))
    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    colors.forEach { c ->
        val on = c == selected
        row.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                setStroke(Ui.pxi(ctx, if (on) 3f else 1f), if (on) EditorColors.Gold else EditorColors.Dot)
            }
            setOnClickListener { onSelect(c) }
        }, LinearLayout.LayoutParams(Ui.pxi(ctx, 44f), Ui.pxi(ctx, 44f)).also { it.marginEnd = Ui.pxi(ctx, 12f) })
    }
    column.addSpaced(row, 10f)
    return column
}

internal fun toggleRow(ctx: Context, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit): View =
    LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Ui.pxi(ctx, 52f)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(editorText(ctx, title, 15f, EditorColors.Text))
            addSpaced(editorText(ctx, subtitle, 13f, EditorColors.Muted), 2f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(SwitchCompat(ContextThemeWrapper(ctx, R.style.WidgetEditorSwitch)).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }, LinearLayout.LayoutParams(Ui.pxi(ctx, 52f), Ui.pxi(ctx, 48f)))
    }

private fun stepperButton(ctx: Context, symbol: String, description: String, onClick: () -> Unit) =
    editorText(ctx, symbol, 26f, EditorColors.Gold).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(EditorColors.Chip)
            setStroke(Ui.pxi(ctx, 1f), EditorColors.Dot)
        }
        contentDescription = description
        setOnClickListener { onClick() }
    }

/**
 * Custom refresh interval: [value] is shown in [hours] or minutes. [onValue] receives the new value
 * (already clamped), [onUnit] the new unit. Typed numbers are committed on Done / focus loss.
 */
internal fun customIntervalBox(
    ctx: Context, value: Int, hours: Boolean, active: Boolean,
    onValue: (Int) -> Unit, onUnit: (Boolean) -> Unit
): View {
    val minV = if (hours) 1 else MIN_INTERVAL_MINUTES
    val maxV = if (hours) MAX_INTERVAL_MINUTES / 60 else MAX_INTERVAL_MINUTES
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = editorShape(EditorColors.Custom, 18f, ctx, EditorColors.Gold)
        val pad = Ui.pxi(ctx, 16f)
        setPadding(pad, pad, pad, pad)
    }
    val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    header.addView(editorText(ctx, "Custom interval", 15f, EditorColors.Text, bold = true),
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    if (active) {
        header.addView(editorText(ctx, "Active", 12f, EditorColors.Bg, bold = true).apply {
            background = editorShape(EditorColors.Gold, 10f, ctx)
            setPadding(Ui.pxi(ctx, 10f), Ui.pxi(ctx, 4f), Ui.pxi(ctx, 10f), Ui.pxi(ctx, 4f))
        })
    }
    box.addView(header)

    val input = EditText(ctx).apply {
        setText(value.toString())
        inputType = InputType.TYPE_CLASS_NUMBER
        imeOptions = EditorInfo.IME_ACTION_DONE
        setSingleLine(true)
        gravity = Gravity.CENTER
        textSize = 30f
        setTextColor(EditorColors.Text)
        setTypeface(typeface, Typeface.BOLD)
        background = editorShape(EditorColors.Tabs, 14f, ctx, EditorColors.ChipBorder)
        setPadding(0, 0, 0, 0)
        contentDescription = "Custom interval"
        setSelectAllOnFocus(true)
    }
    var committed = false
    fun commit() {
        if (committed) return
        val v = input.text.toString().filter(Char::isDigit).toIntOrNull()?.coerceIn(minV, maxV) ?: value
        if (v != value) { committed = true; onValue(v) } else input.setText(value.toString())
    }
    input.setOnEditorActionListener { v, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) { commit(); v.clearFocus(); true } else false
    }
    input.setOnFocusChangeListener { _, has -> if (!has) commit() }

    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val gap = Ui.pxi(ctx, 12f)
    row.addView(stepperButton(ctx, "−", "Decrease interval") { onValue((value - 1).coerceIn(minV, maxV)) },
        LinearLayout.LayoutParams(Ui.pxi(ctx, 48f), Ui.pxi(ctx, 48f)))
    row.addView(input, LinearLayout.LayoutParams(0, Ui.pxi(ctx, 56f), 1f).also { it.marginStart = gap; it.marginEnd = gap })
    row.addView(stepperButton(ctx, "+", "Increase interval") { onValue((value + 1).coerceIn(minV, maxV)) },
        LinearLayout.LayoutParams(Ui.pxi(ctx, 48f), Ui.pxi(ctx, 48f)))
    box.addSpaced(row)

    box.addSpaced(segmented(ctx, listOf("Minutes", "Hours"), if (hours) 1 else 0) { onUnit(it == 1) })

    val unitWord = if (hours) (if (value == 1) "hour" else "hours") else (if (value == 1) "minute" else "minutes")
    val caption = SpannableStringBuilder("Refreshes every ")
    val start = caption.length
    caption.append("$value $unitWord")
    caption.setSpan(ForegroundColorSpan(EditorColors.Gold), start, caption.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    caption.setSpan(StyleSpan(Typeface.BOLD), start, caption.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    caption.append(" · minimum $MIN_INTERVAL_MINUTES min")
    box.addSpaced(editorText(ctx, caption, 13f, EditorColors.Muted).apply { gravity = Gravity.CENTER })
    return box
}
