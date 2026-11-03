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
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = vertical().apply {
            background = rounded(R.color.widget_editor_surface1, R.color.widget_editor_border, 28f)
            setPadding(Ui.pxi(context, 20f), Ui.pxi(context, 18f), Ui.pxi(context, 20f), Ui.pxi(context, 12f))
        }
        val container = FrameLayout(context)
        container.setBackgroundColor(Color.TRANSPARENT)
        container.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).also {
            val margin = Ui.pxi(context, 12f)
            it.setMargins(margin, margin, margin, margin)
        })
        val catalog = WidgetKind.values().flatMap { kind ->
            WidgetStyle.values().filter { kind in it.kinds }.map { style -> PickerItem(kind, style) } +
                AzkarWidgetFamily.values().filter { it.kind == kind }.map { PickerItem(kind, WidgetStyle.CLASSIC, it) }
        }
        var selectedItem = catalog.first()
        val title = horizontal()
        title.addView(TextView(context, null, 0, R.style.TB).apply {
            text = context.getString(R.string.available_widgets)
            textSize = 18f
            setTextColor(context.getColor(R.color.widget_editor_text))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(TextView(context, null, 0, R.style.WidgetEditorButton).apply {
            text = "×"
            textSize = 26f
            gravity = Gravity.CENTER
            background = rounded(R.color.widget_editor_surface2, R.color.widget_editor_border, 22f)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(Ui.pxi(context, 44f), Ui.pxi(context, 44f)))
        root.addView(title)
        root.addView(TextView(context, null, 0, R.style.WidgetEditorBody).apply {
            text = context.getString(R.string.pick_widget_subtitle)
            textSize = 11f
            setTextColor(context.getColor(R.color.widget_editor_muted))
        })
        lateinit var pickerAdapter: PickerAdapter
        lateinit var addButton: TextView
        val filters = horizontal()
        val filterButtons = mutableListOf<TextView>()
        listOf(
            context.getString(R.string.all),
            context.getString(R.string.small),
            context.getString(R.string.medium),
            context.getString(R.string.large)
        ).forEachIndexed { index, text ->
            filters.addView(TextView(context, null, 0, R.style.WidgetEditorButton).apply {
                this.text = text
                gravity = Gravity.CENTER
                filterButtons += this
                updateFilterAppearance(this, index == 0)
                setOnClickListener {
                    val filter = WidgetKind.values().getOrNull(index - 1)
                    viewModel.setPickerFilter(filter)
                    catalog.firstOrNull { filter == null || it.kind == filter }?.let { selectedItem = it }
                    pickerAdapter.setFilter(filter)
                    addButton.text = addLabel(selectedItem)
                    filterButtons.forEachIndexed { buttonIndex, button ->
                        updateFilterAppearance(button, buttonIndex == index)
                    }
                }
            }, LinearLayout.LayoutParams(Ui.pxi(context, 72f), Ui.pxi(context, 36f)).also {
                it.marginEnd = Ui.pxi(context, 8f)
            })
        }
        root.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(filters)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(context, 36f)).also {
            it.topMargin = Ui.pxi(context, 16f)
        })
        val recycler = RecyclerView(context).apply {
            setHasFixedSize(true)
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, 0, 0, Ui.pxi(context, 64f))
            layoutManager = GridLayoutManager(context, 2).also { manager ->
                manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) =
                        if (adapter?.getItemViewType(position) == PickerAdapter.HEADER ||
                            adapter?.getItemViewType(position) == PickerAdapter.FULL) 2 else 1
                }
            }
        }
        pickerAdapter = PickerAdapter(context, catalog, selectedItem, viewModel.pickerFilter) { item ->
            selectedItem = item
            addButton.text = addLabel(selectedItem)
        }
        adapter = pickerAdapter
        recycler.adapter = pickerAdapter
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addButton = TextView(context, null, 0, R.style.WidgetEditorButton).apply {
            text = addLabel(selectedItem)
            gravity = Gravity.CENTER
            background = rounded(R.color.widget_editor_gold, 0, 16f)
            setTextColor(Color.BLACK)
            setOnClickListener { dialog.dismiss(); requestPin(selectedItem) }
        }
        root.addView(addButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(context, 52f)).also {
            it.topMargin = Ui.pxi(context, 12f)
        })
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

    private fun addLabel(item: PickerItem): String {
        val name = item.family?.let { FAMILY_LABELS[it] }?.let { context.getString(it) } ?: item.style.label
        return context.getString(R.string.add_style_to_home, name, widgetSizeName(context, item.kind))
    }

    private fun updateFilterAppearance(button: TextView, selected: Boolean) {
        button.setTextColor(context.getColor(
            if (selected) R.color.widget_editor_black else R.color.widget_editor_muted
        ))
        button.background = rounded(
            if (selected) R.color.widget_editor_gold else R.color.widget_editor_surface2,
            if (selected) R.color.widget_editor_gold else R.color.widget_editor_border,
            18f
        )
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
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
                text.text = "${pickerSizeLabel(text.context, row.kind)}    ${row.count} ${text.context.getString(R.string.widgets)}"
                text.setTextColor(text.context.getColor(R.color.widget_editor_muted))
                text.textSize = 11f
                text.letterSpacing = 0f
                text.setPadding(0, Ui.pxi(text.context, 16f), 0, Ui.pxi(text.context, 8f))
            }
        }

        private inner class CardHolder(private val frame: FrameLayout) : RecyclerView.ViewHolder(frame) {
            fun bind(item: PickerItem) {
                val height = if (item.kind == WidgetKind.SMALL) 132 else if (item.kind == WidgetKind.MEDIUM) 104 else 190
                frame.removeAllViews()
                frame.setPadding(Ui.pxi(context, 6f), Ui.pxi(context, 6f), Ui.pxi(context, 6f), Ui.pxi(context, 6f))
                frame.background = rounded(item == selected)
                val bitmap = previewCache[item] ?: try {
                    val folder = Store.folders().firstOrNull()?.id ?: "anytime"
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
                frame.addView(preview, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.pxi(context, height.toFloat() - 12f)
                ))
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
                setColor(context.getColor(R.color.widget_editor_surface2))
                setStroke(
                    Ui.pxi(context, 1f),
                    context.getColor(if (selected) R.color.widget_editor_gold else R.color.widget_editor_border)
                )
                cornerRadius = Ui.px(context, 20f)
            }
        }
    }

private fun pickerSizeLabel(context: android.content.Context, kind: WidgetKind) = when (kind) {
    WidgetKind.SMALL -> context.getString(R.string.small_grid)
    WidgetKind.MEDIUM -> context.getString(R.string.medium_grid)
    WidgetKind.LARGE -> context.getString(R.string.large_grid)
}
