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
                peekCache.remove(it.id)
                Toast.makeText(requireContext(), R.string.widget_applied, Toast.LENGTH_SHORT).show()
            }
        }
        selected = placed.firstOrNull { it.id == viewModel.selectedId() } ?: placed.firstOrNull()
        selected?.let { viewModel.setSelectedId(it.id) }
        activeTab = EditorTab.values().getOrElse(viewModel.activeTab()) { EditorTab.CONTENT }
        bindSwipe(view)
        renderChips(view, placed)
        renderTabs(view)
        render(view)
        setDirty(view, false)
    }

    /** Shows "Not applied" beside Apply while the selected widget's draft differs from the home screen. */
    private fun setDirty(view: View, on: Boolean) {
        dirty = on
        refreshApplied(view)
    }

    private fun refreshApplied(view: View) {
        val pending = selected?.let { viewModel.hasChanges(requireContext(), it.id) } == true
        view.findViewById<View>(R.id.not_applied).visibility = if (pending) View.VISIBLE else View.INVISIBLE
    }

    /**
     * Before leaving a widget with unapplied edits, asks whether to apply or discard them; [go] runs after either.
     * Cancel stays on the widget. Without edits, [go] runs straight away.
     */
    private fun confirmLeave(view: View, go: () -> Unit) {
        val item = selected
        if (item == null || !viewModel.hasChanges(requireContext(), item.id)) return go()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.widget_apply_changes_title)
            .setMessage(R.string.widget_apply_changes_body)
            .setPositiveButton(R.string.apply) { _, _ ->
                viewModel.apply(requireContext(), item.id)
                peekCache.remove(item.id)
                Toast.makeText(requireContext(), R.string.widget_applied, Toast.LENGTH_SHORT).show()
                go()
            }
            .setNegativeButton(R.string.widget_discard) { _, _ ->
                viewModel.reset(item.id, requireContext())
                go()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
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
        refreshApplied(view)
        prewarmPeeks(view)
    }

    /** Page dots under the preview: the long gold one is the widget being edited. */
    private fun renderChips(view: View, placed: List<PlacedWidget>) {
        renderDots(view, view.findViewById(R.id.placed_widgets), placed)
        renderDots(view, view.findViewById(R.id.mini_dots), placed)
        val item = selected
        view.findViewById<TextView>(R.id.mini_title).text = item?.let {
            it.family?.let { f -> FAMILY_LABELS[f] }?.let { r -> getString(r) }
                ?: "${getString(it.config.style.label)} · ${widgetSizeName(requireContext(), it.kind)}"
        } ?: ""
        val i = placed.indexOfFirst { it.id == item?.id }
        view.findViewById<View>(R.id.mini_prev).alpha = if (i > 0) 1f else 0.3f
        view.findViewById<View>(R.id.mini_next).alpha = if (i in 0 until placed.lastIndex) 1f else 0.3f
    }

    private fun renderDots(view: View, root: LinearLayout, placed: List<PlacedWidget>) {
        val ctx = requireContext()
        val before = root.tag as? Int
        root.tag = selected?.id
        root.removeAllViews()
        root.visibility = if (placed.size > 1) View.VISIBLE else View.GONE
        placed.forEachIndexed { index, item ->
            val on = item.id == selected?.id
            val dot = View(ctx).apply {
                background = editorShape(if (on) EditorColors.Gold else ctx.getColor(R.color.widget_editor_border), 3f, ctx)
                contentDescription = (item.family?.let { f -> FAMILY_LABELS[f] }?.let { getString(it) }
                    ?: "${getString(item.config.style.label)} · ${widgetSizeName(ctx, item.kind)}") + " (${index + 1}/${placed.size})"
            }
            // Each dot sits in a 24dp-tall touch box so it can be tapped too.
            val box = FrameLayout(ctx).apply {
                setPadding(Ui.pxi(ctx, 4f), 0, Ui.pxi(ctx, 4f), 0)
                setOnClickListener { select(view, item.id) }
                // The dots are part of the swipe area; a tap still selects.
                swipeTouch?.let { setOnTouchListener(it) }
                val wasOn = item.id == before
                val from = Ui.pxi(ctx, if (before == null || before == selected?.id) (if (on) 18f else 6f) else if (wasOn) 18f else 6f)
                val to = Ui.pxi(ctx, if (on) 18f else 6f)
                addView(dot, FrameLayout.LayoutParams(from, Ui.pxi(ctx, 6f), Gravity.CENTER))
                if (from != to) android.animation.ValueAnimator.ofInt(from, to).apply {
                    duration = 260
                    interpolator = android.view.animation.DecelerateInterpolator()
                    addUpdateListener { a -> dot.layoutParams = dot.layoutParams.apply { width = a.animatedValue as Int } }
                    start()
                }
            }
            root.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    /** [direction] 1 = next (slides in from the end), -1 = previous, 0 = no slide. */
    private fun select(view: View, id: Int, direction: Int = 0) {
        if (id == selected?.id) return
        confirmLeave(view) { if (isAdded) switchTo(view, id, direction) }
    }

    private fun switchTo(view: View, id: Int, direction: Int) {
        val placed = viewModel.load(requireContext())
        if (id == selected?.id) return
        val target = placed.firstOrNull { it.id == id } ?: return
        val dir = if (direction != 0) direction else {
            val from = placed.indexOfFirst { it.id == selected?.id }
            if (placed.indexOf(target) > from) 1 else -1
        }
        val card = view.findViewById<View>(R.id.preview_card)
        val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val shift = card.width * 0.35f * dir * (if (rtl) -1 else 1)
        card.animate().cancel()
        // Leave from wherever a drag left the card, always moving away (never back toward the middle).
        val exit = if (Math.abs(card.translationX) > Math.abs(shift)) card.translationX + -shift * 0.5f else -shift
        card.animate().translationX(exit).alpha(0f).setDuration(120)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                if (!isAdded) return@withEndAction
                selected = target
                viewModel.setSelectedId(id)
                renderChips(view, placed)
                render(view)
                card.translationX = shift
                card.animate().translationX(0f).alpha(1f).setDuration(220)
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }.start()
    }

    private fun step(view: View, by: Int) {
        val placed = viewModel.load(requireContext())
        val i = placed.indexOfFirst { it.id == selected?.id }
        placed.getOrNull(i + by)?.let { select(view, it.id, by) }
    }

    /** Swipe the preview sideways to move to the next or previous widget. */
    private fun bindSwipe(view: View) {
        view.findViewById<View>(R.id.mini_prev).setOnClickListener { step(view, -1) }
        view.findViewById<View>(R.id.mini_next).setOnClickListener { step(view, 1) }
        // The mini bar slides in once the preview has scrolled out of sight.
        val mini = view.findViewById<View>(R.id.mini_bar)
        val panelBottom = { view.findViewById<View>(R.id.placed_widgets).bottom }
        view.findViewById<android.widget.ScrollView>(R.id.editor_scroll).setOnScrollChangeListener { _, _, y, _, _ ->
            val show = y > panelBottom()
            if (show && mini.visibility != View.VISIBLE) {
                mini.visibility = View.VISIBLE
                mini.translationY = -mini.height.toFloat().coerceAtLeast(200f)
                mini.animate().translationY(0f).setDuration(200).start()
            } else if (!show && mini.visibility == View.VISIBLE && mini.translationY == 0f) {
                mini.animate().translationY(-mini.height.toFloat()).setDuration(160)
                    .withEndAction { mini.visibility = View.GONE }.start()
            }
        }
        // The card follows the finger; letting go past a quarter of its width (or with a quick flick) moves on,
        // otherwise it springs back. Once the drag is clearly sideways the scroll view can't steal it.
        val card = view.findViewById<View>(R.id.preview_card)
        val slop = android.view.ViewConfiguration.get(requireContext()).scaledTouchSlop
        val minFling = Ui.px(requireContext(), 400f)
        var downX = 0f
        var downY = 0f
        var dragging = false
        var hitEnd = false
        var tracker: android.view.VelocityTracker? = null
        fun neighbour(dx: Float): Int? {
            val placed = viewModel.load(requireContext())
            val i = placed.indexOfFirst { it.id == selected?.id }
            if (i < 0) return null
            val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val step = if ((dx < 0) != rtl) 1 else -1
            return if (placed.getOrNull(i + step) != null) step else null
        }
        swipeTouch = View.OnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    if (settling) return@OnTouchListener false
                    downX = e.rawX; downY = e.rawY; dragging = false
                    card.animate().cancel()
                    tracker?.recycle()
                    tracker = android.view.VelocityTracker.obtain().also { it.addMovement(e) }
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(e)
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                        dragging = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        val step = neighbour(dx)
                        if (step == null) {
                            // Rubber-band at the first / last widget, with one tick when you hit the end.
                            if (!hitEnd) { hitEnd = true; card.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
                            hidePeek(view)
                            card.translationX = dx * 0.25f
                            dragProgress(view, 0, 0f)
                        } else {
                            hitEnd = false
                            showPeek(view, step, dx)
                            card.translationX = dx
                            dragProgress(view, step, Math.abs(dx) / card.width.coerceAtLeast(1))
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(e)
                    tracker?.computeCurrentVelocity(1000)
                    val vx = tracker?.xVelocity ?: 0f
                    tracker?.recycle(); tracker = null
                    val dx = e.rawX - downX
                    val step = if (dragging && e.actionMasked == android.view.MotionEvent.ACTION_UP) neighbour(dx) else null
                    val far = Math.abs(dx) > card.width * 0.25f
                    val flick = Math.abs(vx) > minFling && (vx < 0) == (dx < 0)
                    if (!dragging) {
                        // A plain tap: dots still select their widget.
                        if (e.actionMasked == android.view.MotionEvent.ACTION_UP && v !== card) v.performClick()
                    } else if (step != null && (far || flick)) {
                        card.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        if (selected?.let { viewModel.hasChanges(requireContext(), it.id) } == true) {
                            springBack(view)
                            confirmLeave(view) { if (isAdded) step(view, step) }
                        } else settle(view, step, dx < 0)
                    } else {
                        springBack(view)
                    }
                    dragging = false
                    hitEnd = false
                    true
                }
                else -> true
            }
        }
        // Swipes can start on the card (with its size label), anywhere in the pager frame, or on the dots row.
        listOf(R.id.preview_card, R.id.preview_pager, R.id.placed_widgets).forEach {
            view.findViewById<View>(it).setOnTouchListener(swipeTouch)
        }
    }

    /** One touch handler shared by the card, the size label under it and the page dots. */
    private var swipeTouch: View.OnTouchListener? = null

    /** Which neighbour the peek card currently shows (its widget id), so it is built once per drag. */
    private var peekId: Int? = null
    /** True while a swipe is finishing or springing back; new touches wait for it. */
    private var settling = false
    private val peekGap get() = Ui.px(requireContext(), 12f)

    private fun neighbourItem(by: Int): PlacedWidget? {
        val placed = viewModel.load(requireContext())
        val i = placed.indexOfFirst { it.id == selected?.id }
        return if (i < 0) null else placed.getOrNull(i + by)
    }

    /** Fills the peek card with the neighbouring widget's preview and keeps it just beside the dragged card. */
    private fun showPeek(view: View, by: Int, dx: Float) {
        val peek = view.findViewById<FrameLayout>(R.id.preview_peek)
        val card = view.findViewById<View>(R.id.preview_card)
        val item = neighbourItem(by) ?: return hidePeek(view)
        if (peekId != item.id) {
            peekId = item.id
            fillPeek(peek, item)
            peek.layoutParams = peek.layoutParams.apply {
                height = peekBitmap(item)?.let { faceHeight(it) }
                    ?: view.findViewById<View>(R.id.preview_face).height.coerceAtLeast(Ui.pxi(requireContext(), 128f))
            }
        }
        peek.visibility = View.VISIBLE
        val offset = card.width + peekGap
        peek.translationX = dx + if (dx < 0) offset else -offset
        peek.alpha = 1f
    }

    /** Neighbour previews drawn ahead of time, keyed by widget id; reused while the config is unchanged. */
    private val peekCache = HashMap<Int, Pair<WidgetStore.Config, android.graphics.Bitmap?>>()
    private var prewarm: Runnable? = null

    private fun peekBitmap(item: PlacedWidget, model: WidgetModel? = null): android.graphics.Bitmap? {
        peekCache[item.id]?.let { (config, bmp) -> if (config == item.config) return bmp }
        val bmp = widgetBitmap(item, model)
        peekCache[item.id] = item.config to bmp
        return bmp
    }

    /** Right after a widget is shown, draws both neighbours' previews so the first drag doesn't stutter. */
    private fun prewarmPeeks(view: View) {
        prewarm?.let { view.removeCallbacks(it) }
        prewarm = Runnable {
            if (!isAdded) return@Runnable
            val keep = mutableSetOf<Int>().apply { selected?.let { add(it.id) } }
            listOf(-1, 1).forEach { by -> neighbourItem(by)?.let { keep += it.id; peekBitmap(it) } }
            peekCache.keys.retainAll(keep)
        }.also { view.postDelayed(it, 150) }
    }

    private fun hidePeek(view: View) {
        view.findViewById<View>(R.id.preview_peek).apply { animate().cancel(); visibility = View.GONE; translationX = 0f }
    }

    private fun fillPeek(peek: FrameLayout, item: PlacedWidget) {
        val ctx = requireContext()
        peek.removeAllViews()
        val model = WidgetModel.load(ctx, item.id, item.config.folder)
        val bitmap = peekBitmap(item, model)
        if (bitmap != null) {
            peek.addView(ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(bitmap)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            return
        }
        val appearance = item.config.appearance
        peek.addView(TextView(ctx, null, 0, R.style.ArB).apply {
            text = model.cur?.arabic ?: getString(R.string.widget_no_azkar)
            textSize = appearance.fontSizeSp
            gravity = Gravity.CENTER
            setTextColor(appearance.textColor.takeIf { it != 0 } ?: ctx.getColor(R.color.widget_editor_gold))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }

    /** Moves the page dots and the mini bar along with the drag: 0 = resting, 1 = fully on the neighbour. */
    private fun dragProgress(view: View, by: Int, p: Float) {
        val ctx = requireContext()
        val t = p.coerceIn(0f, 1f)
        val placed = viewModel.load(ctx)
        val i = placed.indexOfFirst { it.id == selected?.id }
        val small = Ui.px(ctx, 6f)
        val big = Ui.px(ctx, 18f)
        for (root in listOf(view.findViewById<LinearLayout>(R.id.placed_widgets), view.findViewById(R.id.mini_dots))) {
            for (k in 0 until root.childCount) {
                val dot = (root.getChildAt(k) as? ViewGroup)?.getChildAt(0) ?: continue
                val w = when {
                    k == i -> big - (big - small) * t
                    by != 0 && k == i + by -> small + (big - small) * t
                    else -> small
                }.toInt()
                if (dot.layoutParams.width != w) dot.layoutParams = dot.layoutParams.apply { width = w }
            }
        }
        view.findViewById<View>(R.id.mini_title).alpha = 1f - t * 0.8f
        val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val nudge = Ui.px(ctx, 6f) * t
        view.findViewById<View>(R.id.mini_next).translationX = if (by == 1) (if (rtl) -nudge else nudge) else 0f
        view.findViewById<View>(R.id.mini_prev).translationX = if (by == -1) (if (rtl) nudge else -nudge) else 0f
    }

    /** Finishes a swipe: the card slides out, the peek slides into place, then the editor switches to it. */
    private fun settle(view: View, by: Int, towardStart: Boolean) {
        val target = neighbourItem(by) ?: return springBack(view)
        val card = view.findViewById<View>(R.id.preview_card)
        val peek = view.findViewById<View>(R.id.preview_peek)
        val out = (card.width + peekGap) * if (towardStart) -1 else 1
        val start = card.translationX
        val total = Math.abs(out - start).coerceAtLeast(1f)
        val ms = (220 * total / card.width.coerceAtLeast(1)).toLong().coerceIn(90, 220)
        val interp = android.view.animation.DecelerateInterpolator()
        settling = true
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).setDuration(ms)
        anim.interpolator = interp
        anim.addUpdateListener { a ->
            if (!isAdded) return@addUpdateListener
            val f = a.animatedValue as Float
            val tx = start + (out - start) * f
            card.translationX = tx
            peek.translationX = tx - out
            dragProgress(view, by, Math.abs(tx) / card.width.coerceAtLeast(1))
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) {
                settling = false
                if (!isAdded) return
                selected = target
                viewModel.setSelectedId(target.id)
                // The dots are already in their new place; stop renderChips from animating them again.
                view.findViewById<View>(R.id.placed_widgets).tag = target.id
                view.findViewById<View>(R.id.mini_dots).tag = target.id
                renderChips(view, viewModel.load(requireContext()))
                render(view)
                card.translationX = 0f
                card.alpha = 1f
                resetDrag(view)
            }
        })
        anim.start()
    }

    private fun springBack(view: View) {
        val card = view.findViewById<View>(R.id.preview_card)
        val peek = view.findViewById<View>(R.id.preview_peek)
        val start = card.translationX
        val peekStart = peek.translationX
        val by = when {
            peek.visibility != View.VISIBLE -> 0
            else -> neighbourItem(1)?.takeIf { it.id == peekId }?.let { 1 } ?: -1
        }
        settling = true
        android.animation.ValueAnimator.ofFloat(1f, 0f).setDuration(200).apply {
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { a ->
                if (!isAdded) return@addUpdateListener
                val f = a.animatedValue as Float
                card.translationX = start * f
                peek.translationX = peekStart + start * (f - 1f)
                dragProgress(view, by, Math.abs(card.translationX) / card.width.coerceAtLeast(1))
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { settling = false; if (isAdded) resetDrag(view) }
            })
            start()
        }
    }

    private fun resetDrag(view: View) {
        settling = false
        hidePeek(view)
        peekId = null
        view.findViewById<View>(R.id.mini_title).alpha = 1f
        view.findViewById<View>(R.id.mini_next).translationX = 0f
        view.findViewById<View>(R.id.mini_prev).translationX = 0f
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

    /**
     * The widget exactly as the home screen draws it, with the current draft settings: the OLED renderer for the
     * new designs, the classic renderer (at the widget's real launcher size) for the original ones.
     */
    private fun widgetBitmap(item: PlacedWidget, model: WidgetModel? = null): android.graphics.Bitmap? {
        val ctx = requireContext()
        return try {
            val family = item.family
            if (family == null) com.azkar.app.widgets.WidgetUpdater.renderBitmap(ctx, item.id, item.kind, item.config)
            else {
                val (dw, dh) = AzkarWidgetSizes.design(family)
                NewWidgetRenderer(ctx).render(family, model ?: WidgetModel.load(ctx, item.id, item.config.folder), dw, dh,
                    if (family.tracking) TrackingModel.load(ctx, item.config.folder) else null)
            }
        } catch (e: Exception) {
            Log.e("WidgetEditor", "preview failed for ${item.family ?: item.kind}", e)
            null
        }
    }

    /** Height of the preview frame for [bmp]: same proportions as the real widget, within sensible limits. */
    private fun faceHeight(bmp: android.graphics.Bitmap): Int {
        val ctx = requireContext()
        val innerWidth = ctx.resources.displayMetrics.widthPixels - Ui.pxi(ctx, 104f)
        val wanted = (innerWidth.toFloat() * bmp.height / bmp.width.coerceAtLeast(1)).toInt() + Ui.pxi(ctx, 32f)
        return wanted.coerceIn(Ui.pxi(ctx, 128f), Ui.pxi(ctx, 420f))
    }

    /** Live preview: the real widget picture; the Arabic text mock-up only if drawing it fails. */
    private fun applyPreview(view: View, item: PlacedWidget, model: WidgetModel) {
        val ctx = requireContext()
        val bitmap = widgetBitmap(item, model)
        if (bitmap == null) {
            showTextPreview(view, item)
            return
        }
        peekCache[item.id] = item.config to bitmap
        val face = view.findViewById<LinearLayout>(R.id.preview_face)
        face.layoutParams = face.layoutParams.apply { height = faceHeight(bitmap) }
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
