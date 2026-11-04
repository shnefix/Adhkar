package com.azkar.app.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.azkar.app.R
import kotlin.math.roundToInt

/** Editor colors, taken from the app theme (teal or OLED) by [load] each time the editor opens. */
internal object EditorColors {
    var Bg = 0; private set          // also the text color on gold
    var Text = 0; private set
    var Muted = 0; private set
    var Gold = 0; private set
    var Card = 0; private set
    var Tabs = 0; private set
    var Border = 0; private set
    var Chip = 0; private set
    var ChipBorder = 0; private set
    var ChipText = 0; private set
    var Custom = 0; private set
    var Dot = 0; private set

    fun load(ctx: Context) {
        Bg = ctx.getColor(R.color.bg)
        Text = ctx.getColor(R.color.text)
        Muted = ctx.getColor(R.color.muted)
        Gold = ctx.getColor(R.color.gold)
        Card = ctx.getColor(R.color.card)
        Tabs = ctx.getColor(R.color.card)
        Border = ctx.getColor(R.color.border)
        Chip = ctx.getColor(R.color.card2)
        ChipBorder = ctx.getColor(R.color.border2)
        ChipText = ctx.getColor(R.color.light)
        Custom = ctx.getColor(R.color.card2)
        Dot = ctx.getColor(R.color.border2)
    }
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

/** A settings card like the app's own screens: bordered card, bold title, optional muted hint. */
internal fun sectionCard(ctx: Context, title: String, subtitle: String? = null): LinearLayout =
    LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = editorShape(EditorColors.Card, 24f, ctx, EditorColors.Border)
        val pad = Ui.pxi(ctx, 18f)
        setPadding(pad, pad, pad, pad)
        addView(TextView(ctx, null, 0, R.style.TB).apply {
            text = title
            textSize = 16f
            setTextColor(EditorColors.Text)
        })
        if (subtitle != null) addSpaced(editorText(ctx, subtitle, 13f, EditorColors.Muted), 4f)
    }

/** Muted caps label above a group of cards (the app's "Caps" style). */
internal fun groupLabel(ctx: Context, text: String): View =
    TextView(ctx, null, 0, R.style.Caps).apply { this.text = text }

/** 1dp hairline between rows inside a card. */
internal fun divider(ctx: Context): View = View(ctx).apply { setBackgroundColor(EditorColors.Border) }

internal fun LinearLayout.addDivider() {
    addView(divider(context), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(context, 1f))
        .also { it.topMargin = Ui.pxi(context, 14f) })
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
            minHeight = Ui.pxi(ctx, 44f)
            minimumHeight = Ui.pxi(ctx, 44f)
            setPadding(Ui.pxi(ctx, 16f), Ui.pxi(ctx, 10f), Ui.pxi(ctx, 16f), Ui.pxi(ctx, 10f))
            background = editorShape(
                if (on) EditorColors.Gold else EditorColors.Chip, 22f, ctx,
                if (on) EditorColors.Gold else EditorColors.ChipBorder
            )
            setOnClickListener { onSelect(value) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.marginEnd = Ui.pxi(ctx, 8f)
            it.bottomMargin = Ui.pxi(ctx, 8f)
        })
    }
    return group
}

internal fun segmented(ctx: Context, options: List<String>, selected: Int, onSelect: (Int) -> Unit): View =
    LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        background = editorShape(EditorColors.Chip, 14f, ctx, EditorColors.Border)
        val pad = Ui.pxi(ctx, 4f)
        setPadding(pad, pad, pad, pad)
        options.forEachIndexed { i, label ->
            val on = i == selected
            addView(TextView(ctx, null, 0, R.style.WidgetEditorBody).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(if (on) EditorColors.Bg else EditorColors.Muted)
                setTypeface(typeface, if (on) Typeface.BOLD else Typeface.NORMAL)
                if (on) background = editorShape(EditorColors.Gold, 10f, ctx)
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
    val seek = SeekBar(ctx).apply {
        styleGoldSlider(ctx)
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

/** Thin gold track over a dark one with a round gold knob (the design's Slider), drawn in code so no theme tint can override it. */
private fun SeekBar.styleGoldSlider(ctx: Context) {
    val trackHeight = Ui.pxi(ctx, 4f)
    val track = GradientDrawable().apply { setColor(EditorColors.Border); cornerRadius = trackHeight / 2f }
    val fill = GradientDrawable().apply { setColor(EditorColors.Gold); cornerRadius = trackHeight / 2f }
    val layers = LayerDrawable(arrayOf(track, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL))).apply {
        setId(0, android.R.id.background)
        setId(1, android.R.id.progress)
        for (i in 0..1) {
            setLayerGravity(i, Gravity.CENTER_VERTICAL)
            setLayerHeight(i, trackHeight)
        }
    }
    val knob = Ui.pxi(ctx, 22f)
    progressDrawable = layers
    thumb = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(EditorColors.Gold)
        setSize(knob, knob)
    }
    progressTintList = null
    progressBackgroundTintList = null
    thumbTintList = null
    splitTrack = false
    setPadding(knob / 2, 0, knob / 2, 0)
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
        addView(ToggleSwitch(ctx).apply {
            on = checked
            onToggle = onChange
            contentDescription = title
        }, LinearLayout.LayoutParams(Ui.pxi(ctx, 56f), Ui.pxi(ctx, 44f)))
    }

private fun stepperButton(ctx: Context, symbol: String, description: String, enabled: Boolean = true, onClick: () -> Unit) =
    editorText(ctx, symbol, 26f, EditorColors.Gold).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(EditorColors.Chip)
            setStroke(Ui.pxi(ctx, 1f), EditorColors.Dot)
        }
        contentDescription = description
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.35f
        setOnClickListener { onClick() }
    }

private const val MINUTE_STEP = 5
private const val LAST_MINUTE_STEP = 60 - MINUTE_STEP

/**
 * One stepper press. Order: 15, 20 … 55 min, then 1, 2 … hours, so the unit rolls over instead
 * of the value getting stuck at "0 hours" or climbing through hundreds of minutes.
 * Manual (minutes <= 0) starts from the smallest value of the current unit. Returns (minutes, hours unit).
 */
internal fun stepInterval(minutes: Int, hours: Boolean, up: Boolean): Pair<Int, Boolean> {
    if (minutes <= 0) return (if (hours) 60 else MIN_INTERVAL_MINUTES) to hours
    return if (hours) {
        val h = minutes / 60
        when {
            up -> ((h + 1) * 60).coerceAtMost(MAX_INTERVAL_MINUTES) to true
            h <= 1 -> LAST_MINUTE_STEP to false
            else -> (h - 1) * 60 to true
        }
    } else when {
        up && minutes >= LAST_MINUTE_STEP -> ((minutes / 60 + 1) * 60).coerceAtMost(MAX_INTERVAL_MINUTES) to true
        up -> (minutes / MINUTE_STEP + 1) * MINUTE_STEP to false
        else -> ((minutes - 1) / MINUTE_STEP * MINUTE_STEP).coerceAtLeast(MIN_INTERVAL_MINUTES) to false
    }
}

/** Switching units converts the interval (45 min → 1 hour) rather than reusing the number (45 → 45 hours). */
private fun convertUnit(minutes: Int, toHours: Boolean): Int = when {
    minutes <= 0 -> if (toHours) 60 else MIN_INTERVAL_MINUTES
    toHours -> ((minutes / 60f).roundToInt() * 60).coerceIn(60, MAX_INTERVAL_MINUTES)
    else -> minutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
}

private fun formatInterval(ctx: Context, minutes: Int): String {
    if (minutes < 60) return ctx.getString(R.string.interval_min, minutes)
    val h = minutes / 60
    val r = minutes % 60
    val hours = ctx.resources.getQuantityString(R.plurals.interval_hours, h, h)
    return if (r == 0) hours else ctx.getString(R.string.interval_hours_min, hours, ctx.getString(R.string.interval_min, r))
}

/**
 * Custom refresh interval. [minutes] is the saved interval (0 = manual), shown in [hours] or minutes.
 * [onChange] receives the new interval in minutes (already clamped) and the unit to show it in.
 * Typed numbers are committed on Done / focus loss.
 */
internal fun customIntervalBox(
    ctx: Context, minutes: Int, hours: Boolean, active: Boolean,
    onChange: (minutes: Int, hours: Boolean) -> Unit
): View {
    val manual = minutes <= 0
    val factor = if (hours) 60 else 1
    val value = minutes / factor
    val minV = if (hours) 1 else MIN_INTERVAL_MINUTES
    val maxV = MAX_INTERVAL_MINUTES / factor
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        // Only outlined in gold while it is the interval in use, so it never looks selected next to a preset.
        background = editorShape(EditorColors.Custom, 18f, ctx, if (active) EditorColors.Gold else EditorColors.Border)
        val pad = Ui.pxi(ctx, 16f)
        setPadding(pad, pad, pad, pad)
    }
    val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    header.addView(editorText(ctx, ctx.getString(R.string.custom_interval), 15f, EditorColors.Text, bold = true),
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    if (active) {
        header.addView(editorText(ctx, ctx.getString(R.string.active), 12f, EditorColors.Bg, bold = true).apply {
            background = editorShape(EditorColors.Gold, 10f, ctx)
            setPadding(Ui.pxi(ctx, 10f), Ui.pxi(ctx, 4f), Ui.pxi(ctx, 10f), Ui.pxi(ctx, 4f))
        })
    }
    box.addView(header)

    val input = EditText(ctx).apply {
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        if (manual) setText("") else setText(value.toString())
        hint = "—"
        setHintTextColor(EditorColors.Muted)
        inputType = InputType.TYPE_CLASS_NUMBER
        imeOptions = EditorInfo.IME_ACTION_DONE
        setSingleLine(true)
        gravity = Gravity.CENTER
        textSize = 30f
        setTextColor(EditorColors.Text)
        setTypeface(typeface, Typeface.BOLD)
        background = editorShape(EditorColors.Tabs, 14f, ctx, EditorColors.ChipBorder)
        setPadding(0, 0, 0, 0)
        contentDescription = ctx.getString(R.string.custom_interval)
        setSelectAllOnFocus(true)
        highlightColor = (EditorColors.Gold and 0x00FFFFFF) or 0x55000000
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable = GradientDrawable().apply {
                setColor(EditorColors.Gold)
                setSize(Ui.pxi(ctx, 2f), Ui.pxi(ctx, 30f))
            }
        }
    }
    var committed = false
    fun commit() {
        if (committed) return
        val v = input.text.toString().filter(Char::isDigit).toIntOrNull()?.coerceIn(minV, maxV)
        if (v != null && v * factor != minutes) { committed = true; onChange(v * factor, hours) }
        else input.setText(if (manual) "" else value.toString())
    }
    input.setOnEditorActionListener { v, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) { commit(); v.clearFocus(); true } else false
    }
    input.setOnFocusChangeListener { _, has -> if (!has) commit() }

    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val gap = Ui.pxi(ctx, 12f)
    fun step(up: Boolean) {
        val (m, h) = stepInterval(minutes, hours, up)
        onChange(m, h)
    }
    row.addView(stepperButton(ctx, "−", ctx.getString(R.string.decrease_interval), enabled = manual || minutes > MIN_INTERVAL_MINUTES) { step(false) },
        LinearLayout.LayoutParams(Ui.pxi(ctx, 48f), Ui.pxi(ctx, 48f)))
    row.addView(input, LinearLayout.LayoutParams(0, Ui.pxi(ctx, 56f), 1f).also { it.marginStart = gap; it.marginEnd = gap })
    row.addView(stepperButton(ctx, "+", ctx.getString(R.string.increase_interval), enabled = manual || minutes < MAX_INTERVAL_MINUTES) { step(true) },
        LinearLayout.LayoutParams(Ui.pxi(ctx, 48f), Ui.pxi(ctx, 48f)))
    box.addSpaced(row)

    box.addSpaced(segmented(ctx, listOf(ctx.getString(R.string.minutes), ctx.getString(R.string.hours)), if (hours) 1 else 0) { i ->
        val toHours = i == 1
        if (toHours != hours || manual) onChange(convertUnit(minutes, toHours), toHours)
    })

    val caption = if (manual) {
        SpannableStringBuilder(ctx.getString(R.string.manual_refresh_caption))
    } else {
        val every = formatInterval(ctx, minutes)
        val text = ctx.getString(R.string.refreshes_every, every, ctx.getString(R.string.interval_min, MIN_INTERVAL_MINUTES))
        SpannableStringBuilder(text).apply {
            // The interval itself stands out in gold, wherever the language puts it.
            val start = text.indexOf(every)
            if (start >= 0) {
                setSpan(ForegroundColorSpan(EditorColors.Gold), start, start + every.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), start, start + every.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
    box.addSpaced(editorText(ctx, caption, 13f, EditorColors.Muted).apply { gravity = Gravity.CENTER })
    return box
}