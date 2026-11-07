package com.azkar.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.azkar.app.R
import com.azkar.app.data.Store
import com.azkar.app.widgets.AzkarWidgetSizes
import com.azkar.app.widgets.NewWidgetRenderer
import com.azkar.app.widgets.TrackingModel
import com.azkar.app.data.Tracking
import com.azkar.app.widgets.WidgetKind
import com.azkar.app.widgets.WidgetModel
import com.azkar.app.widgets.WidgetStore
import com.azkar.app.widgets.WidgetStyle
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlin.math.roundToInt

@AndroidEntryPoint
class WidgetEditorFragment : Fragment(R.layout.fragment_widget_editor), HandlesOwnInsets {
    private val viewModel: WidgetEditorViewModel by viewModels()
    private var selected: PlacedWidget? = null
    private var activeTab = EditorTab.CONTENT
    private var tabs: List<View> = emptyList()
    /** Edits made since the last Apply or Reset, shown beside the Apply button. */
    private var dirty = false
    private var picker: WidgetPickerDialog? = null
    private var tabsInFamilyMode: Boolean? = null
    private val customHours = HashMap<Int, Boolean>()

    /** Style holds both the design choice and its look (typography, colors, corners). */
    private enum class EditorTab(val titleRes: Int, val icon: Int) {
        CONTENT(R.string.editor_tab_content, R.drawable.ic_doc_1p75),
        STYLE(R.string.editor_tab_style, R.drawable.ic_palette_1p75),
        BEHAVIOR(R.string.editor_tab_behavior, R.drawable.ic_sliders_1p75)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EditorColors.load(requireContext())
        Store.init(requireContext())
        val placed = viewModel.load(requireContext())
        // A bottom-bar tab now: no back arrow.
        view.findViewById<View>(R.id.btn_back).visibility = View.GONE
        bindNav(view, Tab.WIDGETS)
        bindNavInsets(view, view.findViewById(R.id.editor_content))
        view.findViewById<View>(R.id.btn_choose_widget).setOnClickListener {
            picker?.release()
            picker = WidgetPickerDialog(requireContext(), viewModel).also { it.show() }
        }
        view.findViewById<View>(R.id.btn_apply).setOnClickListener {
            selected?.let {
                viewModel.apply(requireContext(), it.id)
                setDirty(view, false)
                Toast.makeText(requireContext(), R.string.widget_applied, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<View>(R.id.btn_reset).setOnClickListener {
            selected?.let {
                viewModel.reset(it.id, requireContext())
                setDirty(view, false)
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
        setDirty(view, false)
    }

    private fun setDirty(view: View, on: Boolean) {
        dirty = on
        view.findViewById<View>(R.id.unsaved_dot).visibility = if (on) View.VISIBLE else View.INVISIBLE
        view.findViewById<TextView>(R.id.unsaved_text).setText(if (on) R.string.unsaved_changes else R.string.changes_applied)
    }

    private fun render(view: View, rebuildTabContent: Boolean = true) {
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
        if (rebuildTabContent) renderTabContent(view)
    }

    private fun renderChips(view: View, placed: List<PlacedWidget>) {
        val root = view.findViewById<LinearLayout>(R.id.placed_widgets)
        root.removeAllViews()
        placed.forEach { item ->
            val chip = TextView(requireContext(), null, 0, R.style.WidgetEditorButton).apply {
                text = item.family?.let { f -> FAMILY_LABELS[f] }?.let { getString(it) }
                    ?: "${getString(item.config.style.label)} · ${widgetSizeName(requireContext(), item.kind)}"
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
        val ctx = requireContext()
        val root = view.findViewById<LinearLayout>(R.id.editor_tabs)
        root.removeAllViews()
        val gap = Ui.pxi(ctx, 8f)
        val shownTabs = visibleTabs()
        tabs = shownTabs.mapIndexed { index, tab ->
            val on = tab == activeTab
            val color = if (on) EditorColors.Gold else EditorColors.Muted
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = getString(tab.titleRes)
                background = if (on) editorShape(ctx.getColor(R.color.widget_editor_surface2), 16f, ctx, EditorColors.Gold)
                    else editorShape(Color.TRANSPARENT, 16f, ctx, EditorColors.Border)
                addView(ImageView(ctx).apply {
                    setImageResource(tab.icon)
                    imageTintList = android.content.res.ColorStateList.valueOf(color)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(Ui.pxi(ctx, 20f), Ui.pxi(ctx, 20f)))
                addView(TextView(ctx, null, 0, R.style.WidgetEditorMedium).apply {
                    text = getString(tab.titleRes)
                    textSize = 12f
                    setTextColor(color)
                    setTypeface(typeface, if (on) Typeface.BOLD else Typeface.NORMAL)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                    it.topMargin = Ui.pxi(ctx, 4f)
                })
                setOnClickListener {
                    activeTab = tab
                    viewModel.setActiveTab(tab.ordinal)
                    renderTabs(view)
                    renderTabContent(view)
                }
                root.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).also {
                    if (index < shownTabs.lastIndex) it.marginEnd = gap
                })
            }
        }
    }

    /** Design A's folder control: one row showing the current folder; a tap opens the list. */
    private fun <T> folderPicker(ctx: android.content.Context, title: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit): View {
        val current = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second ?: ""
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = "$title: $current"
            background = editorShape(EditorColors.Chip, 18f, ctx, EditorColors.Border)
            val pad = Ui.pxi(ctx, 16f)
            setPadding(pad, 0, pad, 0)
            minimumHeight = Ui.pxi(ctx, 52f)
            addView(editorText(ctx, current, 16f, EditorColors.Text).apply {
                textDirection = View.TEXT_DIRECTION_LOCALE
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_chev_dn_2p25)
                imageTintList = android.content.res.ColorStateList.valueOf(EditorColors.Muted)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(Ui.pxi(ctx, 18f), Ui.pxi(ctx, 18f)))
            setOnClickListener {
                val checked = options.indexOfFirst { it.first == selected }
                AlertDialog.Builder(ctx)
                    .setTitle(title)
                    .setSingleChoiceItems(options.map { it.second }.toTypedArray(), checked) { d, which ->
                        d.dismiss()
                        onSelect(options[which].first)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun LinearLayout.addCard(card: View) {
        addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = Ui.pxi(context, 16f) })
    }

    private fun renderTabContent(view: View) {
        val root = view.findViewById<FrameLayout>(R.id.tab_content)
        root.removeAllViews()
        val item = selected ?: return
        val column = vertical()
        when (activeTab) {
            EditorTab.CONTENT -> contentCards(view, item, column)
            EditorTab.STYLE -> { styleCards(view, item, column); lookCards(view, item, column) }
            EditorTab.BEHAVIOR -> behaviorCards(view, item, column)
        }
        root.addView(column)
    }

    private fun contentCards(view: View, item: PlacedWidget, column: LinearLayout) {
        val ctx = requireContext()
        if (item.family?.tracking == true) {
            // Tracking widgets follow every tracked folder together, or one of them.
            val tracked = Tracking.tracked()
            val selectedFolder = item.config.folder.takeIf { it in tracked } ?: Tracking.ALL
            val card = sectionCard(ctx, getString(R.string.tracking), getString(R.string.tracking_widget_folders))
            card.addSpaced(folderPicker(ctx, getString(R.string.tracking), listOf(Tracking.ALL to getString(R.string.widget_all_tracked)) + tracked.map { it to Store.folderName(it) }, selectedFolder) { id ->
                updateConfig(view) { cfg -> cfg.copy(folder = id) }
            })
            column.addCard(card)
            return
        }
        val folder = sectionCard(ctx, getString(R.string.folder), getString(R.string.widget_folder_summary))
        folder.addSpaced(folderPicker(ctx, getString(R.string.folder), Store.folders().map { it.id to Store.folderName(it.id) }, item.config.folder) { id ->
            updateConfig(view) { cfg -> cfg.copy(folder = id) }
        })
        column.addCard(folder)
        // The OLED designs do not draw a counter toggle (NewWidgetRenderer ignores Appearance).
        if (item.family == null) {
            val counter = sectionCard(ctx, getString(R.string.widget_counter))
            counter.addSpaced(toggleRow(ctx, getString(R.string.show_counter), getString(R.string.widget_counter_summary),
                item.config.appearance.showCounter) { on -> update(view) { it.copy(showCounter = on) } })
            column.addCard(counter)
        }
    }

    private fun styleCards(view: View, item: PlacedWidget, column: LinearLayout) {
        val ctx = requireContext()
        val styles = WidgetStyle.values().filter { item.kind in it.offered }
        val card = sectionCard(ctx, getString(R.string.widget_design), getString(R.string.widget_design_summary, widgetSizeName(ctx, item.kind).lowercase()))
        card.addSpaced(chipGroup(ctx, styles.map { it to getString(it.label) }, item.config.style) { s ->
            updateConfig(view) { cfg -> cfg.copy(style = s) }
        })
        column.addCard(card)
    }

    private fun lookCards(view: View, item: PlacedWidget, column: LinearLayout) {
        val ctx = requireContext()
        val a = item.config.appearance
        val typography = sectionCard(ctx, getString(R.string.typography))
        typography.addSpaced(valueRow(ctx, getString(R.string.arabic_font), "Amiri"))
        typography.addSpaced(sliderRow(ctx, getString(R.string.font_size_label), 14, 40, a.fontSizeSp.roundToInt().coerceIn(14, 40), { getString(R.string.text_size_sp, it) }) { v ->
            update(view, rebuild = false) { it.copy(fontSizeSp = v.toFloat()) }
        })
        typography.addSpaced(sliderRow(ctx, getString(R.string.line_height_label), 10, 20, (a.lineHeight * 10).roundToInt().coerceIn(10, 20),
            { String.format(Locale.US, "%.1f", it / 10f) }) { v ->
            update(view, rebuild = false) { it.copy(lineHeight = v / 10f) }
        })
        typography.addSpaced(valueRow(ctx, getString(R.string.alignment)))
        typography.addSpaced(segmented(ctx, listOf(getString(R.string.start), getString(R.string.center), getString(R.string.end)), a.alignment) { i ->
            update(view) { it.copy(alignment = i) }
        }, 10f)
        column.addCard(typography)

        val colors = sectionCard(ctx, getString(R.string.widget_colors))
        colors.addSpaced(colorRow(ctx, getString(R.string.text_color), EditorTextColors, a.textColor) { c ->
            update(view) { it.copy(textColor = c) }
        })
        colors.addSpaced(colorRow(ctx, getString(R.string.background_color), EditorBgColors, a.backgroundColor) { c ->
            update(view) { it.copy(backgroundColor = c) }
        })
        colors.addSpaced(sliderRow(ctx, getString(R.string.corner_radius), 8, 32, a.cornerRadiusDp.roundToInt().coerceIn(8, 32), { getString(R.string.dp_value, it) }) { v ->
            update(view, rebuild = false) { it.copy(cornerRadiusDp = v.toFloat()) }
        })
        column.addCard(colors)
    }

    private fun presetLabel(minutes: Int) = when (minutes) {
        0 -> getString(R.string.refresh_manual)
        15, 30 -> getString(R.string.interval_min, minutes)
        60 -> getString(R.string.refresh_hourly)
        360 -> resources.getQuantityString(R.plurals.interval_hours, 6, 6)
        else -> getString(R.string.daily)
    }

    private fun behaviorCards(view: View, item: PlacedWidget, column: LinearLayout) {
        val ctx = requireContext()
        val a = item.config.appearance
        // OLED designs: a tap always adds one, so the tap-action card only exists for the old widgets.
        if (item.family == null) {
            val tap = sectionCard(ctx, getString(R.string.tap_action), getString(R.string.widget_tap_summary))
            tap.addSpaced(chipGroup(ctx, WidgetStore.TapAction.values().map { it to tapLabel(it) }, a.tapAction) { action ->
                update(view) { it.copy(tapAction = action) }
            })
            column.addCard(tap)
        }

        val presets = listOf(0, 15, 30, 60, 360, 1440)
        val minutes = a.refreshMinutes
        val refresh = sectionCard(ctx, getString(R.string.refresh), getString(R.string.widget_refresh_summary))
        refresh.addSpaced(chipGroup(ctx, presets.map { it to presetLabel(it) }, minutes.takeIf { it in presets }) { m ->
            // A preset decides its own unit; a unit left over from the custom box would show 15 min as "0 hours".
            customHours.remove(item.id)
            update(view) { it.copy(refreshMinutes = m) }
        })
        // Hours only make sense from one hour up.
        val hours = minutes >= 60 && (customHours[item.id] ?: (minutes % 60 == 0))
        val active = minutes > 0 && minutes !in presets
        refresh.addSpaced(customIntervalBox(ctx, minutes, hours, active) { m, h ->
            customHours[item.id] = h
            update(view) { it.copy(refreshMinutes = m) }
        })
        column.addCard(refresh)
    }




    private fun tapLabel(action: WidgetStore.TapAction) = when (action) {
        WidgetStore.TapAction.INCREMENT -> getString(R.string.increment_counter)
        WidgetStore.TapAction.OPEN_APP -> getString(R.string.open_app)
        WidgetStore.TapAction.OPEN_READER -> getString(R.string.open_reader)
        WidgetStore.TapAction.NEXT_ZIKR -> getString(R.string.next_zikr)
    }



    /** [rebuild] = false while a slider is being dragged, so the slider is not recreated under the finger. */
    private fun update(view: View, rebuild: Boolean = true, change: (WidgetStore.Appearance) -> WidgetStore.Appearance) {
        val item = selected ?: return
        viewModel.update(item.id, item.config.copy(appearance = change(item.config.appearance)))
        setDirty(view, true)
        selected = viewModel.load(requireContext()).firstOrNull { it.id == item.id }
        selected?.let { viewModel.setSelectedId(it.id) }
        render(view, rebuild)
    }

    private fun visibleTabs(): List<EditorTab> =
        if (selected?.family != null) listOf(EditorTab.CONTENT, EditorTab.BEHAVIOR) else EditorTab.values().toList()

    private fun updateConfig(view: View, change: (WidgetStore.Config) -> WidgetStore.Config) {
        val item = selected ?: return
        viewModel.update(item.id, change(item.config))
        setDirty(view, true)
        selected = viewModel.load(requireContext()).firstOrNull { it.id == item.id }
        selected?.let { viewModel.setSelectedId(it.id) }
        renderChips(view, viewModel.load(requireContext()))
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
            NewWidgetRenderer(ctx).render(family, model, dw, dh, if (family.tracking) TrackingModel.load(ctx, item.config.folder) else null)
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
        picker?.release()
        picker = null
        super.onDestroyView()
    }

    private fun vertical() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
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
    private fun chipBackground(on: Boolean) = roundedBackground(
        if (on) R.color.widget_editor_surface2 else R.color.widget_editor_surface1,
        if (on) R.color.widget_editor_gold else R.color.widget_editor_border, 20f
    )
}

private const val FAMILY_PREVIEW_TAG = "family_preview"
