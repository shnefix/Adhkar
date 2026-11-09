package com.azkar.app.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.ReaderResume
import com.azkar.app.data.AppPrefs
import com.azkar.app.reminders.PrayerSessions
import com.azkar.app.data.Store
import com.azkar.app.data.Tracking
import com.azkar.app.widgets.WidgetStore

class ReaderFragment : Fragment(R.layout.fragment_reader), HandlesOwnInsets {
    private lateinit var rn: ReaderNav
    private var lastBack = 0L

    /**
     * Swipes, the slider and the source flip finish on animation callbacks, which can fire after the reader
     * has closed (e.g. back pressed while the card is still sliding). Each of them checks this first.
     */
    private val alive get() = isAdded && view != null && !isRemoving

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::rn.isInitialized) { outState.putInt("i", rn.index); outState.putInt("count", rn.count) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        view.keepScreenOn = AppPrefs.keepScreenOn()   // a view flag: released automatically when the reader goes away
        view.isHapticFeedbackEnabled = false
        view.findViewById<View>(R.id.btn_back).isHapticFeedbackEnabled = false

        val key = requireArguments().getString("key")
        val items = key?.let { Store.readerItems(it) }.orEmpty()
        if (key == null || items.isEmpty()) { nav().pop(); return }
        val explicit = requireArguments().getInt("start", -1)
        val remember = Store.rememberPosition(key)
        val (startIdx, startCount) = when {
            savedInstanceState != null -> savedInstanceState.getInt("i") to savedInstanceState.getInt("count")
            explicit >= 0 -> explicit to 0
            remember -> {
                // Morning / evening positions only last until Dhuhr / Maghrib; after that, start from the first zikr.
                if (PrayerSessions.positionExpired(key, AppPrefs.savedPositionTime(key))) AppPrefs.clearPosition(key)
                val sv = AppPrefs.savedPosition(key)
                ReaderResume.start(items.map { it.id }, items.map { it.target }, sv?.first, sv?.second ?: 0)
            }
            else -> 0 to 0
        }
        rn = ReaderNav(items.map { it.target }, startIdx, startCount)
        val folderTitle = Store.folderName(key)
        val sizeSp = AppPrefs.arabicSp()
        val gold = ctx.getColor(R.color.gold)
        val teal = ctx.getColor(R.color.teal)

        // Full screen: small margins plus whatever the cutout / bars need.
        val d = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding((16 * d).toInt() + b.left, (12 * d).toInt() + b.top, (16 * d).toInt() + b.right, (16 * d).toInt() + b.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)

        // Back on the source page turns back to the zikr (set once the views below exist).
        var closeSource: () -> Boolean = { false }

        // A stray system back (edge swipe) must not close the reader: it needs a second press.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (closeSource()) return
                val now = SystemClock.uptimeMillis()
                if (now - lastBack < 2000) nav().pop()
                else { lastBack = now; Toast.makeText(ctx, R.string.reader_back_again, Toast.LENGTH_SHORT).show() }
            }
        })

        view.findViewById<View>(R.id.btn_back).setOnClickListener { if (!closeSource()) nav().pop() }
        view.findViewById<TextView>(R.id.title).text = folderTitle
        val ar = view.findViewById<TextView>(R.id.ar).apply {
            textSize = sizeSp.toFloat()
            val lhPx = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp * 1.85f, resources.displayMetrics)
            androidx.core.widget.TextViewCompat.setLineHeight(this, lhPx.toInt())
        }
        val titleAbove = view.findViewById<TextView>(R.id.title_above)
        val titleBelow = view.findViewById<TextView>(R.id.title_below)
        val note = view.findViewById<TextView>(R.id.note)
        val arScroll = view.findViewById<ScrollView>(R.id.ar_scroll)
        val focus = AppPrefs.focusMode()
        val segs = view.findViewById<ReaderScrubber>(R.id.segs).apply {
            slider = AppPrefs.readerSlider()
            if (focus) visibility = View.GONE
        }
        val ring = view.findViewById<RingView>(R.id.ring).apply { isHapticFeedbackEnabled = false }
        val countTv = view.findViewById<TextView>(R.id.count)
        val sub = view.findViewById<TextView>(R.id.sub)
        val hint = view.findViewById<TextView>(R.id.hint)
        val bottomBar = view.findViewById<View>(R.id.bottom_bar)
        val pos = view.findViewById<TextView>(R.id.pos)
        val titleTv = view.findViewById<TextView>(R.id.title)
        // Focus mode: no folder title; the back button stays so the reader can always be left with one tap.
        if (focus) titleTv.visibility = View.INVISIBLE
        val swipe = view as SwipeLayout
        val flipArea = view.findViewById<View>(R.id.flip_area).apply { cameraDistance = 8000 * d }
        val front = view.findViewById<View>(R.id.front)
        val sourcePage = view.findViewById<View>(R.id.source_page)
        // The source page is always in Arabic, whatever the phone's language.
        val arRes = ctx.createConfigurationContext(android.content.res.Configuration(resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag("ar"))
        }).resources
        val sourceBtn = view.findViewById<View>(R.id.btn_source).apply { isHapticFeedbackEnabled = false }
        var showingSource = false
        var shownIndex = -1

        /** [text] with the parts that repeat the zikr [zikr] in gold (none when [zikr] is blank). */
        fun marked(text: String, zikr: String): CharSequence {
            if (zikr.isBlank()) return text
            val ranges = com.azkar.app.data.SourceHighlight.ranges(text, zikr)
            if (ranges.isEmpty()) return text
            return android.text.SpannableString(text).apply {
                ranges.forEach { r ->
                    setSpan(android.text.style.ForegroundColorSpan(ctx.getColor(R.color.gold)), r.first, r.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        /** Reader (front) or source page (back), without animation; the header follows. */
        fun showSide(back: Boolean) {
            showingSource = back
            front.visibility = if (back) View.INVISIBLE else View.VISIBLE
            sourcePage.visibility = if (back) View.VISIBLE else View.GONE
            swipe.paused = back
            titleTv.text = if (back) arRes.getString(R.string.source_title) else folderTitle
            pos.visibility = if (focus && !back) View.GONE else View.VISIBLE
            // Arabic words, English digits («الذكر 6 من 37»), like the numbers in the references below.
            pos.text = if (back) String.format(java.util.Locale.US, arRes.getString(R.string.source_position), rn.index + 1, items.size)
                else getString(R.string.reader_position, rn.index + 1, items.size)
        }

        sourceBtn.setOnClickListener { SourceViews.showSheet(ctx, arRes, items[rn.index], ::marked) }
        closeSource = { false }

        /** [moved]: the user counted or changed zikr (not just opened the reader), so widgets should follow. */
        fun render(moved: Boolean = true) {
            if (!alive) return
            val i = rn.index
            val cur = items[i]
            val done = rn.isDone
            ar.text = cur.arabic
            val hasTitle = cur.title.isNotBlank()
            titleAbove.text = cur.title
            titleBelow.text = cur.belowText
            titleAbove.visibility = if (hasTitle && cur.titlePos.showsAbove) View.VISIBLE else View.GONE
            titleBelow.visibility = if (hasTitle && cur.titlePos.showsBelow) View.VISIBLE else View.GONE
            note.text = cur.note
            note.visibility = if (cur.note.isNotBlank()) View.VISIBLE else View.GONE
            // A new zikr always opens on its front; the source button only appears when there is a source.
            if (i != shownIndex) {
                shownIndex = i
                flipArea.animate().cancel()
                flipArea.rotationY = 0f
                showSide(false)
            }
            sourceBtn.visibility = if (focus || cur.source.isEmpty || !AppPrefs.showsSource(cur.id, Store.zikr(cur.id)?.folderId)) View.GONE else View.VISIBLE
            pos.text = getString(R.string.reader_position, i + 1, items.size)
            segs.setPosition(i, items.size, animate = moved)
            countTv.text = rn.count.toString()
            sub.text = if (done) getString(R.string.done) else getString(R.string.reader_of, cur.target)
            ring.setProgress(Math.round(rn.count.toFloat() / cur.target * 360f).toFloat(), if (done) teal else gold)
            ring.visibility = if (cur.target <= 1) View.GONE else View.VISIBLE
            hint.text = if (focus) "" else if (rn.isLast && cur.target > 1)
                getString(if (done) R.string.reader_finished else R.string.reader_tap_ring) else ""
            // No source button and no hint: the strip under the card goes, so the card fills the screen.
            bottomBar.visibility = if (sourceBtn.visibility == View.GONE && hint.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            // Saved only after the user acts, so just opening the reader doesn't move the "saved at" time forward.
            if (moved && remember && items[rn.index].id.isNotEmpty()) AppPrefs.savePosition(key, items[rn.index].id, rn.count)
            // Azkar behind the current one are read; the current one counts once its repeats are done.
            val finished = i + if (cur.target > 1 && rn.count >= cur.target) 1 else 0
            Tracking.record(key, finished, items.size)
            // Widgets on this folder follow along (redrawn when the app goes to the background). Only after the
            // user acts: merely opening a folder at its first zikr must not wipe a widget's count for today.
            if (moved) WidgetStore.syncFolderProgress(ctx, key, finished, if (finished == i) rn.count else 0)
        }

        ring.setOnClickListener {
            if (rn.tapRing()) render()
        }

        // Progress-bar slider: the position label follows the finger, and letting go opens that zikr.
        val cardView = view.findViewById<View>(R.id.card)
        segs.onScrub = { k -> if (alive && !focus) pos.text = getString(R.string.reader_position, k + 1, items.size) }
        segs.onPick = pick@{ k ->
            if (!alive) return@pick
            if (rn.jumpTo(k)) {
                arScroll.scrollTo(0, 0)
                render()
                cardView.alpha = 0.35f
                cardView.animate().alpha(1f).setDuration(180).start()
            } else pos.text = getString(R.string.reader_position, rn.index + 1, items.size)
        }

        swipe.apply {
            card = view.findViewById(R.id.card)
            noTapViews = listOf(ring)   // the ring is inside the card: tapping it counts, it never moves on
            tapEnabled = AppPrefs.tapAdvance()
            tapLeftBack = AppPrefs.tapLeftBack()
            // Focus mode: while a zikr's counter is running, tapping the card counts; otherwise it moves on as usual.
            if (focus) onTap = {
                val counting = alive && items[rn.index].target > 1 && !rn.isDone
                if (counting && rn.tapRing()) render()
                counting
            }
            canPage = { dir -> rn.canPage(dir) }
            canFinish = { rn.canFinish() }
            onFinish = finish@{
                if (!alive) return@finish
                Tracking.record(key, items.size, items.size)
                AppPrefs.clearPosition(key)
                WidgetStore.syncFolderProgress(ctx, key, items.size, 0)
                // finishReading() removes this fragment at once, so keep the activity before calling it.
                val main = nav()
                main.finishReading()
                if (AppPrefs.completionScreen()) main.push(CompleteFragment.of(folderTitle, key))
            }
            onPage = page@{ dir ->
                if (!alive) return@page
                if (dir > 0) rn.next() else rn.prev()
                arScroll.scrollTo(0, 0)
                render()
            }
        }
        // Screen readers can't swipe: the card offers next / previous as actions, and a double tap moves on.
        view.findViewById<View>(R.id.card).apply {
            isFocusable = true
            ViewCompat.replaceAccessibilityAction(this, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.next_zikr)) { _, _ -> swipe.next(); true }
            ViewCompat.addAccessibilityAction(this, getString(R.string.next_zikr)) { _, _ -> swipe.next(); true }
            ViewCompat.addAccessibilityAction(this, getString(R.string.previous_zikr)) { _, _ -> swipe.previous(); true }
        }
        render(moved = false)
    }

    private fun immersive(on: Boolean) {
        val w = requireActivity().window
        val c = WindowCompat.getInsetsController(w, w.decorView)
        if (on) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() { super.onResume(); immersive(true) }
    override fun onDestroyView() {
        // Stop anything still moving so its end callback never reaches a closed reader.
        view?.let { v ->
            (v as? SwipeLayout)?.apply { onPage = null; onFinish = null; onTap = null; card?.animate()?.cancel() }
            v.findViewById<View>(R.id.flip_area)?.animate()?.cancel()
            v.findViewById<ReaderScrubber>(R.id.segs)?.apply { onPick = null; onScrub = null }
        }
        immersive(false)
        super.onDestroyView()
    }

    companion object {
        fun of(key: String, start: Int = -1) = ReaderFragment().apply { arguments = Bundle().apply { putString("key", key); putInt("start", start) } }
    }
}
