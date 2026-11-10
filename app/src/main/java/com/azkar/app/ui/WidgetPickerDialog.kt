package com.azkar.app.ui

import android.app.Dialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.Store
import com.azkar.app.widgets.AzkarWidgetFamily
import com.azkar.app.widgets.AzkarWidgetSizes
import com.azkar.app.widgets.LargeWidgetProvider
import com.azkar.app.widgets.MediumWidgetProvider
import com.azkar.app.widgets.NewWidgetRenderer
import com.azkar.app.widgets.SmallWidgetProvider
import com.azkar.app.widgets.WidgetKind
import com.azkar.app.widgets.WidgetModel
import com.azkar.app.widgets.WidgetPinReceiver
import com.azkar.app.widgets.WidgetStyle
import com.azkar.app.widgets.WidgetStore
import com.azkar.app.widgets.WidgetUpdater

/** Full-screen catalog of every widget design; picking one asks the launcher to pin it. */
internal class WidgetPickerDialog(
    private val context: Context,
    private val viewModel: WidgetEditorViewModel
) {
    private var adapter: PickerAdapter? = null

    /** Drops cached preview bitmaps; call when the owning view goes away. */
    fun release() {
        adapter?.clearCache()
        adapter = null
    }

    private fun vertical() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun horizontal() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private fun rounded(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        setColor(context.getColor(fill))
        if (stroke != 0) setStroke(Ui.pxi(context, 1f), context.getColor(stroke))
        cornerRadius = Ui.px(context, radius)
    }

    fun show() {
        EditorColors.load(context)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        // A bottom sheet: rounded top corners, a grab handle, and the app's dimmed screen above it.
        val root = vertical().apply {
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.widget_editor_surface1))
                setStroke(Ui.pxi(context, 1f), context.getColor(R.color.widget_editor_border))
                val r = Ui.px(context, 30f)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            isClickable = true
        }
        val container = FrameLayout(context)
        container.setBackgroundColor(Color.TRANSPARENT)
        container.setOnClickListener { dialog.dismiss() }
        container.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).also { it.topMargin = Ui.pxi(context, 46f) })
        val catalog = WidgetKind.values().flatMap { kind ->
            pickerStyles(kind).map { style -> PickerItem(kind, style) } +
                pickerFamilies(kind).map { PickerItem(kind, WidgetStyle.CLASSIC, it) }
        }
        // How many of each design are already on the home screen, for the "On home" tags.
        val placed = try { viewModel.load(context) } catch (e: Exception) { emptyList() }
        val onHome: (PickerItem) -> Int = { item ->
            placed.count { p -> if (item.family != null) p.family == item.family else p.family == null && p.kind == item.kind && p.config.style == item.style }
        }
        var selectedItem = catalog.first()
        val side = Ui.pxi(context, 20f)
        root.addView(View(context).apply {
            background = rounded(R.color.widget_editor_border, 0, 2f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(Ui.pxi(context, 40f), Ui.pxi(context, 4f)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.topMargin = Ui.pxi(context, 10f)
        })
        val title = horizontal().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(side, Ui.pxi(context, 10f), side, 0) }
        title.addView(TextView(context, null, 0, R.style.TB).apply {
            text = context.getString(R.string.add_widget)
            textSize = 22f
            setTextColor(context.getColor(R.color.widget_editor_text))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(TextView(context, null, 0, R.style.WidgetEditorButton).apply {
            text = "×"
            textSize = 24f
            gravity = Gravity.CENTER
            contentDescription = context.getString(R.string.close)
            setTextColor(context.getColor(R.color.widget_editor_text))
            background = rounded(R.color.widget_editor_surface2, R.color.widget_editor_border, 20f)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(Ui.pxi(context, 40f), Ui.pxi(context, 40f)))
        root.addView(title)
        lateinit var pickerAdapter: PickerAdapter
        lateinit var updateBar: () -> Unit
        val filters = horizontal().apply { setPadding(side, 0, side, 0) }
        val filterButtons = mutableListOf<TextView>()
        listOf<WidgetKind?>(null, WidgetKind.SMALL, WidgetKind.MEDIUM, WidgetKind.LARGE).forEachIndexed { index, kind ->
            val count = catalog.count { kind == null || it.kind == kind }
            if (count == 0) return@forEachIndexed
            val label = kind?.let { widgetSizeName(context, it) } ?: context.getString(R.string.all)
            filters.addView(TextView(context, null, 0, R.style.WidgetEditorButton).apply {
                // The count sits after the name in a lighter tone.
                text = android.text.SpannableStringBuilder(label).apply {
                    append("  ")
                    val start = length
                    append(count.toString())
                    setSpan(android.text.style.RelativeSizeSpan(0.8f), start, length, 0)
                    setSpan(android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), start, length, 0)
                }
                gravity = Gravity.CENTER
                setPadding(Ui.pxi(context, 16f), 0, Ui.pxi(context, 16f), 0)
                contentDescription = "$label, $count"
                filterButtons += this
                updateFilterAppearance(this, index == 0)
                setOnClickListener {
                    viewModel.setPickerFilter(kind)
                    if (kind != null && selectedItem.kind != kind)
                        catalog.firstOrNull { it.kind == kind }?.let { selectedItem = it; pickerAdapter.select(it) }
                    pickerAdapter.setFilter(kind)
                    updateBar()
                    filterButtons.forEach { button -> updateFilterAppearance(button, button === this) }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.pxi(context, 38f)).also {
                it.marginEnd = Ui.pxi(context, 8f)
            })
        }
        root.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(filters)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(context, 38f)).also {
            it.topMargin = Ui.pxi(context, 14f)
        })
        val recycler = RecyclerView(context).apply {
            setHasFixedSize(true)
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(side, 0, side, Ui.pxi(context, 16f))
            layoutManager = GridLayoutManager(context, 2).also { manager ->
                manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) =
                        if (adapter?.getItemViewType(position) == PickerAdapter.HEADER ||
                            adapter?.getItemViewType(position) == PickerAdapter.FULL) 2 else 1
                }
            }
        }
        // Every open starts on "All" (Small → Medium → Large), matching the highlighted filter button.
        viewModel.setPickerFilter(null)
        pickerAdapter = PickerAdapter(context, catalog, selectedItem, null, onHome) { item ->
            selectedItem = item
            updateBar()
        }
        adapter = pickerAdapter
        recycler.adapter = pickerAdapter
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Confirm bar: a larger preview of the chosen design, its name and size, and the Add button.
        val bar = vertical().apply {
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.widget_editor_black))
                setStroke(Ui.pxi(context, 1f), context.getColor(R.color.widget_editor_border))
                val r = Ui.px(context, 24f)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            elevation = Ui.px(context, 12f)
            setPadding(side, Ui.pxi(context, 16f), side, Ui.pxi(context, 16f))
        }
        val barTop = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
        val barThumb = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        barTop.addView(barThumb, LinearLayout.LayoutParams(Ui.pxi(context, 118f), Ui.pxi(context, 56f)))
        val barText = vertical()
        val barName = editorText(context, "", 16f, EditorColors.Text, bold = true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val barTags = horizontal()
        barText.addView(barName)
        barText.addView(barTags, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.pxi(context, 8f) })
        barTop.addView(barText, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.pxi(context, 14f) })
        bar.addView(barTop)
        val addButton = TextView(context, null, 0, R.style.WidgetEditorButton).apply {
            gravity = Gravity.CENTER
            background = rounded(R.color.widget_editor_gold, 0, 16f)
            setTextColor(Color.BLACK)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(Ui.pxi(context, 12f), 0, Ui.pxi(context, 12f), 0)
            setOnClickListener { dialog.dismiss(); requestPin(selectedItem) }
        }
        bar.addView(addButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(context, 52f)).also {
            it.topMargin = Ui.pxi(context, 14f)
        })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2))
        updateBar = {
            val item = selectedItem
            barThumb.setImageBitmap(pickerAdapter.preview(item))
            barName.text = itemName(item)
            barTags.removeAllViews()
            barTags.addView(pickerTag(context, pickerSizeLabel(context, item.kind), gold = true))
            onHome(item).takeIf { it > 0 }?.let {
                barTags.addView(pickerTag(context, context.getString(R.string.picker_on_home_count, it), gold = false),
                    LinearLayout.LayoutParams(-2, -2).also { lp -> lp.marginStart = Ui.pxi(context, 6f) })
            }
            addButton.text = addLabel(item)
        }
        updateBar()
        dialog.setContentView(container)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.72f }
        }
        dialog.setOnDismissListener {
            pickerAdapter.clearCache()
            if (adapter === pickerAdapter) adapter = null
        }
    }

    private fun itemName(item: PickerItem): String =
        item.family?.let { FAMILY_LABELS[it] }?.let { context.getString(it) } ?: context.getString(item.style.label)

    private fun addLabel(item: PickerItem): String =
        context.getString(R.string.add_style_to_home, itemName(item), widgetSizeName(context, item.kind))

    private fun updateFilterAppearance(button: TextView, selected: Boolean) {
        button.setTextColor(if (selected) Color.BLACK else EditorColors.ChipText)
        button.setTypeface(button.typeface, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        button.background = rounded(
            if (selected) R.color.widget_editor_gold else R.color.widget_editor_surface2,
            if (selected) R.color.widget_editor_gold else R.color.widget_editor_border,
            19f
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) button.stateDescription = if (selected) context.getString(R.string.active) else null
    }

    private fun requestPin(item: PickerItem) {
        item.family?.let { pinNewWidget(context, it); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(context, R.string.widget_pin_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val provider = when (item.kind) {
            WidgetKind.SMALL -> SmallWidgetProvider::class.java
            WidgetKind.MEDIUM -> MediumWidgetProvider::class.java
            WidgetKind.LARGE -> LargeWidgetProvider::class.java
        }
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            WidgetStore.savePendingPin(context, item.kind, item.style)
            val callback = PendingIntent.getBroadcast(
                context,
                item.kind.ordinal,
                Intent(context, WidgetPinReceiver::class.java),
                // Mutable so the launcher can add EXTRA_APPWIDGET_ID; safe because the intent is explicit.
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            )
            try {
                manager.requestPinAppWidget(ComponentName(context, provider), null, callback)
            } catch (error: RuntimeException) {
                WidgetStore.clearPendingPin(context)
                Toast.makeText(context, R.string.widget_pin_failed, Toast.LENGTH_LONG).show()
                return
            }
            Toast.makeText(context, R.string.widget_pin_requested, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.widget_pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }
}

internal fun widgetSizeName(context: Context, kind: WidgetKind) = when (kind) {
    WidgetKind.SMALL -> context.getString(R.string.small)
    WidgetKind.MEDIUM -> context.getString(R.string.medium)
    WidgetKind.LARGE -> context.getString(R.string.large)
}

private data class PickerItem(val kind: WidgetKind, val style: WidgetStyle, val family: AzkarWidgetFamily? = null)


private class PickerAdapter(
        private val context: android.content.Context,
        private val catalog: List<PickerItem>,
        private var selected: PickerItem,
        initialFilter: WidgetKind?,
        private val onHome: (PickerItem) -> Int,
        private val onSelected: (PickerItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        companion object {
            const val HEADER = 1
            const val CARD = 2
            const val FULL = 3
        }

        private var filter = initialFilter
        private var rows = buildRows(filter)
        private val previewCache = HashMap<PickerItem, android.graphics.Bitmap>()

        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is PickerRow.Header -> HEADER
            is PickerRow.Card -> if ((rows[position] as PickerRow.Card).item.kind == WidgetKind.SMALL) CARD else FULL
        }
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
            if (type == HEADER) HeaderHolder(TextView(context, null, 0, R.style.WidgetEditorLabel))
            else CardHolder(FrameLayout(context))

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is PickerRow.Header -> (holder as HeaderHolder).bind(row)
                is PickerRow.Card -> (holder as CardHolder).bind(row.item)
            }
        }

        fun clearCache() {
            previewCache.clear()
        }

        /** Marks [item] as chosen (when a filter moves the choice) and redraws the two tiles that changed. */
        fun select(item: PickerItem) {
            val previous = selected
            selected = item
            listOf(previous, item).forEach { s ->
                rows.indexOfFirst { it is PickerRow.Card && it.item == s }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
            }
        }

        /** The design's real picture with sample content, drawn once per open. */
        fun preview(item: PickerItem): android.graphics.Bitmap? = previewCache[item] ?: try {
            val folder = Store.readableFolders().firstOrNull()?.id ?: Store.ANYTIME
            val family = item.family
            (if (family != null) {
                val (dw, dh) = AzkarWidgetSizes.design(family)
                val model = WidgetModel.load(context, AppWidgetManager.INVALID_APPWIDGET_ID, folder)
                NewWidgetRenderer(context).render(family, model, dw, dh)
            } else {
                WidgetUpdater.previewBitmap(context, item.kind, item.style, folder)
            }).also { previewCache[item] = it }
        } catch (error: Exception) {
            Log.e("WidgetPicker", "Unable to render ${item.kind}/${item.style.label} preview", error)
            null
        }

        fun setFilter(value: WidgetKind?) {
            val old = rows
            filter = value
            rows = buildRows(value)
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = rows.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    old[oldItemPosition].toString() == rows[newItemPosition].toString()
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    areItemsTheSame(oldItemPosition, newItemPosition)
            }).dispatchUpdatesTo(this)
        }

        private fun buildRows(value: WidgetKind?) = catalog.filter { value == null || it.kind == value }
            .groupBy { it.kind }.flatMap { (kind, items) ->
                listOf(PickerRow.Header(kind, items.size)) + items.map { PickerRow.Card(it) }
            }

        private sealed interface PickerRow {
            data class Header(val kind: WidgetKind, val count: Int) : PickerRow
            data class Card(val item: PickerItem) : PickerRow
        }

        private class HeaderHolder(private val text: TextView) : RecyclerView.ViewHolder(text) {
            fun bind(row: PickerRow.Header) {
                val ctx = text.context
                text.text = "${pickerSizeLabel(ctx, row.kind)}  ·  ${ctx.getString(R.string.picker_designs, row.count)}"
                text.setTextColor(ctx.getColor(R.color.widget_editor_muted))
                text.textSize = 11f
                text.isAllCaps = true
                text.letterSpacing = if (ctx.resources.configuration.locales[0].language == "ar") 0f else 0.12f
                text.setPadding(0, Ui.pxi(ctx, 18f), 0, Ui.pxi(ctx, 10f))
            }
        }

        private inner class CardHolder(private val frame: FrameLayout) : RecyclerView.ViewHolder(frame) {
            fun bind(item: PickerItem) {
                val height = if (item.kind == WidgetKind.SMALL) 132 else if (item.kind == WidgetKind.MEDIUM) 104 else 190
                val on = item == selected
                frame.removeAllViews()
                frame.setPadding(Ui.pxi(context, 8f), Ui.pxi(context, 8f), Ui.pxi(context, 8f), Ui.pxi(context, 8f))
                frame.background = rounded(on)
                (frame.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    val m = Ui.pxi(context, 5f); lp.setMargins(m, m, m, m); frame.layoutParams = lp
                } ?: run {
                    val m = Ui.pxi(context, 5f)
                    frame.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(m, m, m, m) }
                }
                val bitmap = preview(item)
                val preview: View = if (bitmap != null) {
                    android.widget.ImageView(context).apply {
                        setImageBitmap(bitmap)
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                    }
                } else {
                    TextView(context).apply {
                        text = context.getString(R.string.widget_preview_unavailable)
                        gravity = Gravity.CENTER
                        setTextColor(context.getColor(R.color.widget_editor_muted))
                    }
                }
                // Medium previews share one width (a 4x2 widget's), so the 4x1 strips aren't drawn larger than the rest.
                val box = height.toFloat() - 12f
                val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                column.addView(preview, LinearLayout.LayoutParams(
                    if (item.kind == WidgetKind.MEDIUM) Ui.pxi(context, box * 338f / 158f) else ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.pxi(context, box)
                ).also { it.gravity = Gravity.CENTER_HORIZONTAL })
                // Name, then "On home" / "Selected" tags.
                val name = item.family?.let { FAMILY_LABELS[it] }?.let { context.getString(it) } ?: context.getString(item.style.label)
                column.addView(editorText(context, name, 13f, if (on) EditorColors.Gold else EditorColors.Text, bold = true).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2).also {
                    it.topMargin = Ui.pxi(context, 8f)
                    it.marginStart = Ui.pxi(context, 4f); it.marginEnd = Ui.pxi(context, 4f)
                })
                // Tags get their own line so they never squeeze the name.
                val tags = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                if (on) tags.addView(pickerTag(context, context.getString(R.string.picker_selected), gold = true))
                if (onHome(item) > 0) tags.addView(pickerTag(context, context.getString(R.string.picker_on_home), gold = false),
                    LinearLayout.LayoutParams(-2, -2).also { if (on) it.marginStart = Ui.pxi(context, 6f) })
                if (tags.childCount > 0) column.addView(tags, LinearLayout.LayoutParams(-2, -2).also {
                    it.topMargin = Ui.pxi(context, 6f)
                    it.marginStart = Ui.pxi(context, 4f)
                })
                frame.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2))
                frame.contentDescription = name
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    frame.stateDescription = if (on) context.getString(R.string.picker_selected) else null
                frame.setOnClickListener {
                    val previous = selected
                    selected = item
                    onSelected(item)
                    rows.indexOfFirst { it is PickerRow.Card && it.item == previous }
                        .takeIf { it >= 0 }
                        ?.let { notifyItemChanged(it) }
                    rows.indexOfFirst { it is PickerRow.Card && it.item == item }
                        .takeIf { it >= 0 }
                        ?.let { notifyItemChanged(it) }
                }
            }

            private fun rounded(selected: Boolean) = GradientDrawable().apply {
                setColor(context.getColor(R.color.widget_editor_black))
                setStroke(
                    Ui.pxi(context, if (selected) 2f else 1f),
                    context.getColor(if (selected) R.color.widget_editor_gold else R.color.widget_editor_border)
                )
                cornerRadius = Ui.px(context, 20f)
            }
        }
    }

/** A small rounded label: gold for the size or "Selected", quiet for "On home". */
private fun pickerTag(context: Context, text: String, gold: Boolean): TextView =
    editorText(context, text, 10f, if (gold) EditorColors.Gold else EditorColors.Muted, bold = true).apply {
        maxLines = 1
        setPadding(Ui.pxi(context, 7f), Ui.pxi(context, 2f), Ui.pxi(context, 7f), Ui.pxi(context, 2f))
        background = editorShape(EditorColors.Chip, 8f, context, EditorColors.Border)
    }

private fun pickerSizeLabel(context: android.content.Context, kind: WidgetKind) = when (kind) {
    WidgetKind.SMALL -> context.getString(R.string.small_grid)
    WidgetKind.MEDIUM -> context.getString(R.string.medium_grid)
    WidgetKind.LARGE -> context.getString(R.string.large_grid)
}

private fun <T> List<T>.swapped(a: T, b: T): List<T> {
    val i = indexOf(a); val j = indexOf(b)
    if (i < 0 || j < 0) return this
    return toMutableList().also { it[i] = b; it[j] = a }
}

/** Styles of [kind] in picker order: among the small ones the calligraphy style comes before the tasbih. */
internal fun pickerStyles(kind: WidgetKind): List<WidgetStyle> =
    WidgetStyle.values().filter { kind in it.offered }.let {
        if (kind == WidgetKind.SMALL) it.swapped(WidgetStyle.TASBIH, WidgetStyle.CALLIGRAPHY) else it
    }

/** Design and tracking families of [kind] in picker order: among the small ones the tap disc comes before the morning/evening rings. */
internal fun pickerFamilies(kind: WidgetKind, offered: List<AzkarWidgetFamily> = offeredFamilies()): List<AzkarWidgetFamily> =
    offered.filter { it.kind == kind }.let {
        if (kind == WidgetKind.SMALL) it.swapped(AzkarWidgetFamily.D_SMALL, AzkarWidgetFamily.B_SMALL) else it
    }
