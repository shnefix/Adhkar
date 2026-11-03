package com.azkar.app.ui

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.app.PendingIntent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.Store
import com.azkar.app.widgets.AzkarWidgetSizes
import com.azkar.app.widgets.LargeWidgetProvider
import com.azkar.app.widgets.NewWidgetRenderer
import com.azkar.app.widgets.MediumWidgetProvider
import com.azkar.app.widgets.SmallWidgetProvider
import com.azkar.app.widgets.WidgetKind
import com.azkar.app.widgets.WidgetModel
import com.azkar.app.widgets.WidgetStore
import com.azkar.app.widgets.WidgetStyle
import com.azkar.app.widgets.WidgetUpdater
import com.azkar.app.widgets.WidgetPinReceiver
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WidgetEditorFragment : Fragment(R.layout.fragment_widget_editor) {
    private val viewModel: WidgetEditorViewModel by viewModels()
    private var selected: PlacedWidget? = null
    private var activeTab = EditorTab.CONTENT
    private var tabs: List<TextView> = emptyList()
    private var activePickerAdapter: PickerAdapter? = null
    private var tabsInFamilyMode: Boolean? = null

    private enum class EditorTab(val title: String) {
        CONTENT("Content"), STYLE("Style"), TEXT("Text"), COLORS("Colors"), BEHAVIOR("Behavior")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Store.init(requireContext())
        val placed = viewModel.load(requireContext())
        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }
        view.findViewById<View>(R.id.btn_choose_widget).setOnClickListener { showPicker() }
        view.findViewById<View>(R.id.btn_apply).setOnClickListener {
            selected?.let {
                viewModel.apply(requireContext(), it.id)
                Toast.makeText(requireContext(), R.string.widget_applied, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<View>(R.id.btn_reset).setOnClickListener {
            selected?.let {
                viewModel.reset(it.id, requireContext())
                selected = viewModel.load(requireContext()).firstOrNull { p -> p.id == it.id }
                render(view)
            }
        }
        selected = placed.firstOrNull { it.id == viewModel.selectedId() } ?: placed.firstOrNull()
        selected?.let { viewModel.setSelectedId(it.id) }
        activeTab = EditorTab.values().getOrElse(viewModel.activeTab()) { EditorTab.CONTENT }
        renderChips(view, placed)
        renderTabs(view)
        render(view)
    }

    private fun render(view: View) {
        val item = selected ?: run {
            view.findViewById<TextView>(R.id.preview_arabic).setText(R.string.no_widgets_on_home)
            view.findViewById<TextView>(R.id.preview_size).text = ""
            return
        }
        val ctx = requireContext()
        val familyMode = item.family != null
        if (familyMode != tabsInFamilyMode) {
            tabsInFamilyMode = familyMode
            if (activeTab !in visibleTabs()) {
                activeTab = EditorTab.CONTENT
                viewModel.setActiveTab(activeTab.ordinal)
            }
            renderTabs(view)
        }
        val model = WidgetModel.load(ctx, item.id, item.config.folder)
        val appearance = item.config.appearance
        view.findViewById<TextView>(R.id.preview_size).text = sizeLabel(item.kind)
        view.findViewById<TextView>(R.id.preview_arabic).apply {
            text = model.cur?.arabic ?: getString(R.string.widget_no_azkar)
            textSize = appearance.fontSizeSp
            setTextColor(appearance.textColor.takeIf { it != 0 } ?: ctx.getColor(R.color.widget_editor_gold))
            textAlignment = when (appearance.alignment) {
                0 -> View.TEXT_ALIGNMENT_TEXT_START
                2 -> View.TEXT_ALIGNMENT_TEXT_END
                else -> View.TEXT_ALIGNMENT_CENTER
            }
        }
        view.findViewById<TextView>(R.id.preview_meta).text =
            "${model.folderName} · ${model.shown} / ${model.target}"
        applyPreview(view, item, model)
        renderTabContent(view)
    }

    private fun renderChips(view: View, placed: List<PlacedWidget>) {
        val root = view.findViewById<LinearLayout>(R.id.placed_widgets)
        root.removeAllViews()
        placed.forEach { item ->
            val chip = TextView(requireContext(), null, 0, R.style.WidgetEditorButton).apply {
                text = item.family?.let { f -> FAMILY_LABELS[f] }?.let { getString(it) }
                    ?: "${item.config.style.label} · ${item.kind.name.lowercase().replaceFirstChar { it.uppercase() }.first()}"
                setTextColor(requireContext().getColor(R.color.widget_editor_muted))
                textSize = 13f
                gravity = Gravity.CENTER
                minWidth = Ui.pxi(requireContext(), 40f)
                setPadding(Ui.pxi(requireContext(), 16f), 0, Ui.pxi(requireContext(), 16f), 0)
                setOnClickListener {
                    selected = viewModel.load(requireContext()).firstOrNull { p -> p.id == item.id }
                    renderChips(view, placed)
                    render(view)
                }
                background = chipBackground(item.id == selected?.id)
            }
            root.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.pxi(requireContext(), 40f)).also {
                it.marginEnd = Ui.pxi(requireContext(), 8f)
            })
        }
    }

    private fun renderTabs(view: View) {
        val root = view.findViewById<LinearLayout>(R.id.editor_tabs)
        root.removeAllViews()
        val shownTabs = visibleTabs()
        tabs = shownTabs.map { tab ->
            TextView(requireContext(), null, 0, R.style.WidgetEditorMedium).apply {
                text = tab.title
                gravity = Gravity.CENTER
                minWidth = Ui.pxi(requireContext(), 48f)
                setOnClickListener {
                    activeTab = tab
                    viewModel.setActiveTab(tab.ordinal)
                    renderTabs(view)
                    renderTabContent(view)
                }
                root.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.pxi(requireContext(), 44f)).also {
                    it.marginEnd = Ui.pxi(requireContext(), 20f)
                })
            }
        }
        tabs.forEachIndexed { index, text ->
            text.setTextColor(requireContext().getColor(
                if (shownTabs[index] == activeTab) R.color.widget_editor_gold else R.color.widget_editor_muted
            ))
            text.background = if (shownTabs[index] == activeTab) requireContext().getDrawable(R.drawable.widget_editor_tab_selected) else null
        }
    }

    private fun renderTabContent(view: View) {
        val root = view.findViewById<FrameLayout>(R.id.tab_content)
        root.removeAllViews()
        val item = selected ?: return
        val appearance = item.config.appearance
        when (activeTab) {
            EditorTab.CONTENT -> {
                val column = vertical()
                column.addView(folderPanel(view, item.config.folder), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = Ui.pxi(requireContext(), 8f) })
                // The OLED designs do not draw a counter toggle (NewWidgetRenderer ignores Appearance).
                if (item.family == null) {
                    addSwitchRow(column, R.string.show_counter, null, appearance.showCounter) {
                        update(view) { it.copy(showCounter = !it.showCounter) }
                    }
                }
                root.addView(column)
            }
            EditorTab.STYLE, EditorTab.TEXT -> root.addView(typographyPanel(view, appearance))
            EditorTab.COLORS -> root.addView(colorsPanel(view, appearance))
            EditorTab.BEHAVIOR -> {
                val column = vertical()
                if (item.family == null) column.addView(optionPanel(
                    R.string.tap_action,
                    R.string.tap_action_description,
                    WidgetStore.TapAction.values().map { it to tapLabel(it) },
                    appearance.tapAction
                ) { action -> update(view) { it.copy(tapAction = action) } })
                column.addView(optionPanel(
                    R.string.refresh,
                    R.string.refresh_description,
                    listOf(0, 15, 30, 60, 360, 1440).map { it to refreshLabel(it) },
                    appearance.refreshMinutes
                ) { minutes -> update(view) { it.copy(refreshMinutes = minutes) } })
                root.addView(column)
            }
        }
    }

    private fun <T> optionPanel(
        titleId: Int,
        descriptionId: Int,
        options: List<Pair<T, String>>,
        selectedValue: T,
        onSelected: (T) -> Unit
    ): View = optionPanelText(getString(titleId), getString(descriptionId), options, selectedValue, onSelected)

    private fun <T> optionPanelText(
        title: String,
        description: String,
        options: List<Pair<T, String>>,
        selectedValue: T,
        onSelected: (T) -> Unit
    ): View {
        val panel = panel()
        panel.addView(bodyText(title).apply { textSize = 14f })
        panel.addView(bodyText(description).apply {
            textSize = 12f
            setTextColor(requireContext().getColor(R.color.widget_editor_muted))
        })
        val group = FlowLayout(requireContext())
        options.forEach { (value, label) ->
            group.addView(TextView(requireContext(), null, 0, R.style.WidgetEditorMedium).apply {
                text = if (value == selectedValue) "✓  $label" else label
                textSize = 13f
                gravity = Gravity.CENTER
                minHeight = Ui.pxi(requireContext(), 44f)
                setPadding(Ui.pxi(requireContext(), 16f), 0, Ui.pxi(requireContext(), 16f), 0)
                setTextColor(requireContext().getColor(
                    if (value == selectedValue) R.color.widget_editor_black else R.color.widget_editor_muted
                ))
                background = roundedBackground(
                    if (value == selectedValue) R.color.widget_editor_gold else R.color.widget_editor_surface2,
                    if (value == selectedValue) R.color.widget_editor_gold else R.color.widget_editor_border,
                    22f
                )
                setOnClickListener { onSelected(value) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.pxi(requireContext(), 44f)).also {
                it.marginEnd = Ui.pxi(requireContext(), 8f)
                it.bottomMargin = Ui.pxi(requireContext(), 8f)
            })
        }
        panel.addView(group)
        return panel
    }

    private fun typographyPanel(view: View, a: WidgetStore.Appearance): View {
        val panel = panel()
        panel.addView(label(R.string.typography))
        addValueRow(panel, R.string.arabic_font, "Amiri")
        addSliderRow(panel, R.string.widget_font_size, a.fontSizeSp, 16, 44, view) { value ->
            update(view) { it.copy(fontSizeSp = value) }
        }
        addSliderRow(panel, R.string.widget_line_height, a.lineHeight, 12, 20, view) { value ->
            update(view) { it.copy(lineHeight = value) }
        }
        val alignment = horizontal()
        alignment.addView(body(R.string.alignment), LinearLayout.LayoutParams(0, Ui.pxi(requireContext(), 40f), 1f))
        val segmented = horizontal().apply {
            background = roundedBackground(Color.BLACK, R.color.widget_editor_border, 12f)
            setPadding(Ui.pxi(requireContext(), 3f), Ui.pxi(requireContext(), 3f), Ui.pxi(requireContext(), 3f), Ui.pxi(requireContext(), 3f))
        }
        listOf(R.string.start to 0, R.string.center to 1, R.string.end to 2).forEach { (textId, value) ->
            val text = body(textId).apply {
                gravity = Gravity.CENTER
                setTextColor(requireContext().getColor(if (a.alignment == value) R.color.widget_editor_gold else R.color.widget_editor_muted))
                if (a.alignment == value) background = roundedBackground(R.color.widget_editor_surface2, 0, 9f)
                setOnClickListener { update(view) { it.copy(alignment = value) } }
            }
            segmented.addView(text, LinearLayout.LayoutParams(Ui.pxi(requireContext(), 62f), Ui.pxi(requireContext(), 34f)))
        }
        alignment.addView(segmented)
        panel.addView(alignment)
        return panel
    }

    private fun colorsPanel(view: View, a: WidgetStore.Appearance): View {
        val panel = panel()
        panel.addView(body(R.string.text_color))
        panel.addView(swatches(view, a.textColor, listOf(R.color.widget_editor_text, R.color.widget_editor_gold, R.color.widget_editor_gold_deep, R.color.widget_editor_muted), false))
        panel.addView(body(R.string.background_color))
        panel.addView(swatches(view, a.backgroundColor, listOf(R.color.widget_editor_black, R.color.widget_editor_surface1, R.color.widget_editor_surface2, android.R.color.transparent), true))
        addSliderRow(panel, R.string.corner_radius, a.cornerRadiusDp, 8, 32, view) { value ->
            update(view) { it.copy(cornerRadiusDp = value) }
        }
        return panel
    }

    private fun swatches(view: View, selectedColor: Int, colors: List<Int>, transparent: Boolean): View {
        val row = horizontal()
        colors.forEach { colorId ->
            val color = if (colorId == android.R.color.transparent) Color.TRANSPARENT else requireContext().getColor(colorId)
            val swatch = View(requireContext()).apply {
                background = swatchBackground(color, selectedColor == color)
                setOnClickListener {
                    update(view) { if (transparent) it.copy(backgroundColor = color) else it.copy(textColor = color) }
                }
            }
            row.addView(swatch, LinearLayout.LayoutParams(Ui.pxi(requireContext(), 36f), Ui.pxi(requireContext(), 36f)).also {
                it.marginEnd = Ui.pxi(requireContext(), 12f)
            })
        }
        return row
    }

    private fun addSliderRow(parent: LinearLayout, labelId: Int, value: Float, min: Int, maxValue: Int, view: View, onChange: (Float) -> Unit) {
        val row = horizontal()
        val valueText = TextView(requireContext(), null, 0, R.style.WidgetEditorMedium).apply {
            text = if (labelId == R.string.widget_font_size) getString(R.string.widget_font_size, value) else getString(R.string.widget_line_height, value)
            setTextColor(requireContext().getColor(R.color.widget_editor_gold))
        }
        row.addView(valueText, LinearLayout.LayoutParams(0, Ui.pxi(requireContext(), 24f), 1f))
        parent.addView(row)
        val seek = SeekBar(requireContext(), null, 0, R.style.WidgetEditorSeekBar).apply {
            max = maxValue - min
            progress = (value * if (labelId == R.string.widget_line_height) 10 else 1).toInt() - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) onChange(if (labelId == R.string.widget_line_height) (progress + min) / 10f else (progress + min).toFloat())
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        parent.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(requireContext(), 32f)))
    }

    private fun addSwitchRow(parent: LinearLayout, titleId: Int, value: String?, checked: Boolean, onChange: () -> Unit) {
        val row = horizontal().apply {
            background = roundedBackground(R.color.widget_editor_surface1, R.color.widget_editor_border, 16f)
            setPadding(Ui.pxi(requireContext(), 16f), 0, Ui.pxi(requireContext(), 12f), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(body(titleId).apply { if (value != null) text = "$text  $value" }, LinearLayout.LayoutParams(0, -1, 1f))
        if (value == null) {
            val toggle = SwitchCompat(ContextThemeWrapper(requireContext(), R.style.WidgetEditorSwitch)).apply {
                isChecked = checked
                setOnClickListener { onChange() }
            }
            row.addView(toggle, LinearLayout.LayoutParams(Ui.pxi(requireContext(), 52f), Ui.pxi(requireContext(), 48f)))
        }
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(requireContext(), 56f)).also {
            it.bottomMargin = Ui.pxi(requireContext(), 8f)
        })
    }

    private fun showPicker() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = vertical().apply {
            background = roundedBackground(R.color.widget_editor_surface1, R.color.widget_editor_border, 28f)
            setPadding(Ui.pxi(requireContext(), 20f), Ui.pxi(requireContext(), 18f), Ui.pxi(requireContext(), 20f), Ui.pxi(requireContext(), 12f))
        }
        val container = FrameLayout(requireContext())
        container.setBackgroundColor(Color.TRANSPARENT)
        container.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).also {
            val margin = Ui.pxi(requireContext(), 12f)
            it.setMargins(margin, margin, margin, margin)
        })
        val catalog = WidgetKind.values().flatMap { kind ->
            WidgetStyle.values().filter { kind in it.kinds }.map { style -> PickerItem(kind, style) }
        }
        var selectedItem = catalog.first()
        val title = horizontal()
        title.addView(TextView(requireContext(), null, 0, R.style.TB).apply {
            text = getString(R.string.available_widgets)
            textSize = 18f
            setTextColor(requireContext().getColor(R.color.widget_editor_text))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(TextView(requireContext(), null, 0, R.style.WidgetEditorButton).apply {
            text = "×"
            textSize = 26f
            gravity = Gravity.CENTER
            background = roundedBackground(R.color.widget_editor_surface2, R.color.widget_editor_border, 22f)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(Ui.pxi(requireContext(), 44f), Ui.pxi(requireContext(), 44f)))
        root.addView(title)
        root.addView(TextView(requireContext(), null, 0, R.style.WidgetEditorBody).apply {
            text = getString(R.string.pick_widget_subtitle)
            textSize = 11f
            setTextColor(requireContext().getColor(R.color.widget_editor_muted))
        })
        root.addView(TextView(requireContext(), null, 0, R.style.WidgetEditorButton).apply {
            text = getString(R.string.widget_new_designs)
            gravity = Gravity.CENTER
            setTextColor(requireContext().getColor(R.color.widget_editor_gold))
            background = roundedBackground(R.color.widget_editor_surface2, R.color.widget_editor_gold, 18f)
            setOnClickListener { dialog.dismiss(); showNewWidgetPicker(requireContext()) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(requireContext(), 44f)).also {
            it.topMargin = Ui.pxi(requireContext(), 12f)
        })
        lateinit var pickerAdapter: PickerAdapter
        lateinit var addButton: TextView
        val filters = horizontal()
        val filterButtons = mutableListOf<TextView>()
        listOf(
            getString(R.string.all),
            getString(R.string.small),
            getString(R.string.medium),
            getString(R.string.large)
        ).forEachIndexed { index, text ->
            filters.addView(TextView(requireContext(), null, 0, R.style.WidgetEditorButton).apply {
                this.text = text
                gravity = Gravity.CENTER
                filterButtons += this
                updateFilterAppearance(this, index == 0)
                setOnClickListener {
                    val filter = WidgetKind.values().getOrNull(index - 1)
                    viewModel.setPickerFilter(filter)
                    catalog.firstOrNull { filter == null || it.kind == filter }?.let { selectedItem = it }
                    pickerAdapter.setFilter(filter)
                    addButton.text = getString(R.string.add_style_to_home, selectedItem.style.label, sizeName(selectedItem.kind))
                    filterButtons.forEachIndexed { buttonIndex, button ->
                        updateFilterAppearance(button, buttonIndex == index)
                    }
                }
            }, LinearLayout.LayoutParams(Ui.pxi(requireContext(), 72f), Ui.pxi(requireContext(), 36f)).also {
                it.marginEnd = Ui.pxi(requireContext(), 8f)
            })
        }
        root.addView(filters, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(requireContext(), 36f)).also {
            it.topMargin = Ui.pxi(requireContext(), 16f)
        })
        val recycler = RecyclerView(requireContext()).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, 0, 0, Ui.pxi(requireContext(), 64f))
            layoutManager = GridLayoutManager(requireContext(), 2).also { manager ->
                manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) =
                        if (adapter?.getItemViewType(position) == PickerAdapter.HEADER ||
                            adapter?.getItemViewType(position) == PickerAdapter.FULL) 2 else 1
                }
            }
        }
        pickerAdapter = PickerAdapter(requireContext(), catalog, selectedItem, viewModel.pickerFilter) { item ->
            selectedItem = item
            addButton.text = getString(R.string.add_style_to_home, selectedItem.style.label, sizeName(selectedItem.kind))
        }
        activePickerAdapter = pickerAdapter
        recycler.adapter = pickerAdapter
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addButton = TextView(requireContext(), null, 0, R.style.WidgetEditorButton).apply {
            text = getString(R.string.add_style_to_home, selectedItem.style.label, sizeName(selectedItem.kind))
            gravity = Gravity.CENTER
            background = roundedBackground(R.color.widget_editor_gold, 0, 16f)
            setTextColor(Color.BLACK)
            setOnClickListener { dialog.dismiss(); requestPin(selectedItem) }
        }
        root.addView(addButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.pxi(requireContext(), 52f)).also {
            it.topMargin = Ui.pxi(requireContext(), 12f)
        })
        dialog.setContentView(container)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.72f }
        }
        dialog.setOnDismissListener {
            pickerAdapter.clearCache()
            if (activePickerAdapter === pickerAdapter) activePickerAdapter = null
        }
    }

    private fun updateFilterAppearance(button: TextView, selected: Boolean) {
        button.setTextColor(requireContext().getColor(
            if (selected) R.color.widget_editor_black else R.color.widget_editor_muted
        ))
        button.background = roundedBackground(
            if (selected) R.color.widget_editor_gold else R.color.widget_editor_surface2,
            if (selected) R.color.widget_editor_gold else R.color.widget_editor_border,
            18f
        )
    }

    private fun sizeName(kind: WidgetKind) = when (kind) {
        WidgetKind.SMALL -> getString(R.string.small)
        WidgetKind.MEDIUM -> getString(R.string.medium)
        WidgetKind.LARGE -> getString(R.string.large)
    }

    private fun showChoiceDialog(title: String, values: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(values.toTypedArray(), selectedIndex) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun tapLabel(action: WidgetStore.TapAction) = when (action) {
        WidgetStore.TapAction.INCREMENT -> getString(R.string.increment_counter)
        WidgetStore.TapAction.OPEN_APP -> getString(R.string.open_app)
        WidgetStore.TapAction.OPEN_READER -> getString(R.string.open_reader)
        WidgetStore.TapAction.NEXT_ZIKR -> getString(R.string.next_zikr)
    }

    private fun refreshLabel(minutes: Int) = when (minutes) {
        0 -> getString(R.string.manual_only)
        15 -> getString(R.string.every_15_minutes)
        30 -> getString(R.string.every_30_minutes)
        60 -> getString(R.string.every_hour)
        360 -> getString(R.string.every_6_hours)
        else -> getString(R.string.daily)
    }

    private fun requestPin(item: PickerItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(requireContext(), R.string.widget_pin_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        val provider = when (item.kind) {
            WidgetKind.SMALL -> SmallWidgetProvider::class.java
            WidgetKind.MEDIUM -> MediumWidgetProvider::class.java
            WidgetKind.LARGE -> LargeWidgetProvider::class.java
        }
        val manager = AppWidgetManager.getInstance(requireContext())
        if (manager.isRequestPinAppWidgetSupported) {
            WidgetStore.savePendingPin(requireContext(), item.kind, item.style)
            val callback = PendingIntent.getBroadcast(
                requireContext(),
                item.kind.ordinal,
                Intent(requireContext(), WidgetPinReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                manager.requestPinAppWidget(ComponentName(requireContext(), provider), null, callback)
            } catch (error: RuntimeException) {
                WidgetStore.clearPendingPin(requireContext())
                Toast.makeText(requireContext(), R.string.widget_pin_failed, Toast.LENGTH_LONG).show()
                return
            }
            Toast.makeText(requireContext(), R.string.widget_pin_requested, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), R.string.widget_pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    private fun update(view: View, change: (WidgetStore.Appearance) -> WidgetStore.Appearance) {
        val item = selected ?: return
        viewModel.update(item.id, item.config.copy(appearance = change(item.config.appearance)))
        selected = viewModel.load(requireContext()).firstOrNull { it.id == item.id }
        selected?.let { viewModel.setSelectedId(it.id) }
        render(view)
    }

    private fun visibleTabs(): List<EditorTab> =
        if (selected?.family != null) listOf(EditorTab.CONTENT, EditorTab.BEHAVIOR) else EditorTab.values().toList()

    private fun folderPanel(view: View, current: String): View =
        optionPanelText(
            "Folder",
            "Which azkar folder this widget shows.",
            Store.folders().map { it.id to Store.folderName(it.id) },
            current
        ) { id -> updateConfig(view) { it.copy(folder = id) } }

    private fun updateConfig(view: View, change: (WidgetStore.Config) -> WidgetStore.Config) {
        val item = selected ?: return
        viewModel.update(item.id, change(item.config))
        selected = viewModel.load(requireContext()).firstOrNull { it.id == item.id }
        selected?.let { viewModel.setSelectedId(it.id) }
        render(view)
    }

    /** Live preview: the real OLED bitmap for the new designs, the Arabic text mock-up for the old ones. */
    private fun applyPreview(view: View, item: PlacedWidget, model: WidgetModel) {
        val ctx = requireContext()
        val family = item.family
        if (family == null) {
            showTextPreview(view, item)
            return
        }
        val (dw, dh) = AzkarWidgetSizes.design(family)
        val bitmap = try {
            NewWidgetRenderer(ctx).render(family, model, dw, dh)
        } catch (e: Exception) {
            Log.e("WidgetEditor", "preview failed for $family", e)
            null
        }
        if (bitmap == null) {
            showTextPreview(view, item)
            return
        }
        val face = view.findViewById<LinearLayout>(R.id.preview_face)
        val innerWidth = ctx.resources.displayMetrics.widthPixels - Ui.pxi(ctx, 104f)
        val wanted = (innerWidth * dh / dw).toInt() + Ui.pxi(ctx, 32f)
        face.layoutParams = face.layoutParams.apply {
            height = wanted.coerceIn(Ui.pxi(ctx, 128f), Ui.pxi(ctx, 380f))
        }
        val existing: ImageView? = face.findViewWithTag(FAMILY_PREVIEW_TAG)
        val image = existing ?: ImageView(ctx).apply {
            tag = FAMILY_PREVIEW_TAG
            scaleType = ImageView.ScaleType.FIT_CENTER
            face.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        image.setImageBitmap(bitmap)
        view.findViewById<View>(R.id.preview_arabic).visibility = View.GONE
        view.findViewById<View>(R.id.preview_meta).visibility = View.GONE
    }

    private fun showTextPreview(view: View, item: PlacedWidget) {
        val ctx = requireContext()
        val face = view.findViewById<LinearLayout>(R.id.preview_face)
        val existing: ImageView? = face.findViewWithTag(FAMILY_PREVIEW_TAG)
        existing?.let { face.removeView(it) }
        view.findViewById<View>(R.id.preview_arabic).visibility = View.VISIBLE
        view.findViewById<View>(R.id.preview_meta).visibility = View.VISIBLE
        face.layoutParams = face.layoutParams.apply {
            height = Ui.pxi(ctx, if (item.kind == WidgetKind.LARGE) 170f else 128f)
        }
    }

    private fun bodyText(value: String) =
        TextView(requireContext(), null, 0, R.style.WidgetEditorBody).apply { text = value }

    override fun onResume() {
        super.onResume()
        val root = view ?: return
        val before = selected?.id
        val knownIds = viewModel.load(requireContext()).map { it.id }.toSet()
        val placed = viewModel.reload(requireContext())
        selected = placed.firstOrNull { it.id !in knownIds }
            ?: placed.firstOrNull { it.id == before }
            ?: placed.lastOrNull()
        selected?.let { viewModel.setSelectedId(it.id) }
        renderChips(root, placed)
        render(root)
    }

    override fun onDestroyView() {
        activePickerAdapter?.clearCache()
        activePickerAdapter = null
        super.onDestroyView()
    }

    private fun vertical() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
    private fun horizontal() = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
    private fun panel() = vertical().apply {
        background = roundedBackground(R.color.widget_editor_surface1, R.color.widget_editor_border, 20f)
        setPadding(Ui.pxi(requireContext(), 16f), Ui.pxi(requireContext(), 16f), Ui.pxi(requireContext(), 16f), Ui.pxi(requireContext(), 16f))
    }
    private fun body(id: Int) = TextView(requireContext(), null, 0, R.style.WidgetEditorBody).apply { setText(id) }
    private fun label(id: Int) = TextView(requireContext(), null, 0, R.style.WidgetEditorLabel).apply { setText(id) }
    private fun labelText(value: String) = TextView(requireContext(), null, 0, R.style.WidgetEditorLabel).apply { text = value }
    private fun addValueRow(parent: LinearLayout, titleId: Int, value: String, onClick: (() -> Unit)? = null) {
        val row = horizontal()
        row.setOnClickListener { onClick?.invoke() }
        row.addView(body(titleId), LinearLayout.LayoutParams(0, Ui.pxi(requireContext(), 40f), 1f))
        row.addView(TextView(requireContext(), null, 0, R.style.WidgetEditorMedium).apply {
            text = value
            setTextColor(requireContext().getColor(R.color.widget_editor_muted))
        })
        parent.addView(row)
    }
    private fun sizeLabel(kind: WidgetKind) = when (kind) {
        WidgetKind.SMALL -> getString(R.string.small_grid)
        WidgetKind.MEDIUM -> getString(R.string.medium_grid)
        WidgetKind.LARGE -> getString(R.string.large_grid)
    }
    private fun roundedBackground(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        setColor(if (fill == Color.BLACK) Color.BLACK else requireContext().getColor(fill))
        if (stroke != 0) setStroke(Ui.pxi(requireContext(), 1f), requireContext().getColor(stroke))
        cornerRadius = Ui.px(requireContext(), radius)
    }
    private fun roundedBackground(fill: Int, strokeColor: Int, radius: Float, direct: Boolean = false) =
        roundedBackground(fill, if (direct) 0 else strokeColor, radius)
    private fun chipBackground(on: Boolean) = roundedBackground(
        if (on) R.color.widget_editor_surface2 else R.color.widget_editor_surface1,
        if (on) R.color.widget_editor_gold else R.color.widget_editor_border, 20f
    )
    private fun swatchBackground(color: Int, selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (selected) setStroke(Ui.pxi(requireContext(), 2f), requireContext().getColor(R.color.widget_editor_gold))
        else setStroke(Ui.pxi(requireContext(), 1f), requireContext().getColor(R.color.widget_editor_border))
    }
}

private const val FAMILY_PREVIEW_TAG = "family_preview"

private data class PickerItem(val kind: WidgetKind, val style: WidgetStyle)


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
                    WidgetUpdater.previewBitmap(
                        context, item.kind, item.style,
                        Store.folders().firstOrNull()?.id ?: "anytime"
                    ).also { previewCache[item] = it }
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