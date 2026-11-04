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
        val track = ctx.getColor(R.color.track)

        // Full screen: small margins plus whatever the cutout / bars need.
        val d = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding((16 * d).toInt() + b.left, (12 * d).toInt() + b.top, (16 * d).toInt() + b.right, (16 * d).toInt() + b.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)

        // A stray system back (edge swipe) must not close the reader: it needs a second press.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = SystemClock.uptimeMillis()
                if (now - lastBack < 2000) nav().pop()
                else { lastBack = now; Toast.makeText(ctx, R.string.reader_back_again, Toast.LENGTH_SHORT).show() }
            }
        })

        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }
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
        val segs = view.findViewById<LinearLayout>(R.id.segs)
        val ring = view.findViewById<RingView>(R.id.ring).apply { isHapticFeedbackEnabled = false }
        val countTv = view.findViewById<TextView>(R.id.count)
        val sub = view.findViewById<TextView>(R.id.sub)
        val hint = view.findViewById<TextView>(R.id.hint)
        val pos = view.findViewById<TextView>(R.id.pos)
        val flipBox = view.findViewById<View>(R.id.flip_box).apply { cameraDistance = 8000 * d }
        val sourceScroll = view.findViewById<ScrollView>(R.id.source_scroll)
        val sourceTv = view.findViewById<TextView>(R.id.source)
        val sourceBtn = view.findViewById<View>(R.id.btn_source).apply { isHapticFeedbackEnabled = false }
        var showingSource = false
        var shownIndex = -1

        /** Front (the zikr) or back (its source) of the card, without animation. */
        fun showSide(back: Boolean) {
            showingSource = back
            arScroll.visibility = if (back) View.INVISIBLE else View.VISIBLE
            sourceScroll.visibility = if (back) View.VISIBLE else View.GONE
            sourceBtn.contentDescription = getString(if (back) R.string.source_hide else R.string.source_show)
            sourceBtn.isActivated = back
        }

        /** Turns the card over: a half turn to edge-on, swap sides, then the other half back to flat. */
        fun flip() {
            flipBox.animate().cancel()
            val toBack = !showingSource
            flipBox.animate().rotationY(90f).setDuration(160)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    showSide(toBack)
                    if (toBack) sourceScroll.scrollTo(0, 0)
                    flipBox.rotationY = -90f
                    flipBox.animate().rotationY(0f).setDuration(160)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }.start()
        }
        sourceBtn.setOnClickListener { flip() }

        /** [moved]: the user counted or changed zikr (not just opened the reader), so widgets should follow. */
        fun render(moved: Boolean = true) {
            val i = rn.index
            val cur = items[i]
            val done = rn.isDone
            ar.text = cur.arabic
            val hasTitle = cur.title.isNotBlank()
            titleAbove.text = cur.title
            titleBelow.text = cur.title
            titleAbove.visibility = if (hasTitle && !cur.titleBelow) View.VISIBLE else View.GONE
            titleBelow.visibility = if (hasTitle && cur.titleBelow) View.VISIBLE else View.GONE
            note.text = cur.note
            note.visibility = if (cur.note.isNotBlank()) View.VISIBLE else View.GONE
            // A new zikr always opens on its front; the source button only appears when there is a source.
            if (i != shownIndex) {
                shownIndex = i
                flipBox.animate().cancel()
                flipBox.rotationY = 0f
                showSide(false)
            }
            sourceTv.text = cur.source
            sourceBtn.visibility = if (cur.source.isNotBlank()) View.VISIBLE else View.GONE
            pos.text = getString(R.string.reader_position, i + 1, items.size)
            segs.removeAllViews()
            items.indices.forEach { k ->
                val c = if (k < i) teal else if (k == i) gold else track
                val seg = View(ctx).apply { background = Ui.shape(ctx, c, null, 2f) }
                val p = LinearLayout.LayoutParams(0, Ui.pxi(ctx, 4f), 1f)
                if (k > 0) p.marginStart = Ui.pxi(ctx, 6f)
                segs.addView(seg, p)
            }
            countTv.text = rn.count.toString()
            sub.text = if (done) getString(R.string.done) else getString(R.string.reader_of, cur.target)
            ring.setProgress(Math.round(rn.count.toFloat() / cur.target * 360f).toFloat(), if (done) teal else gold)
            ring.visibility = if (cur.target <= 1) View.GONE else View.VISIBLE
            hint.text = if (rn.isLast && cur.target > 1)
                getString(if (done) R.string.reader_finished else R.string.reader_tap_ring) else ""
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

        val swipe = view as SwipeLayout
        swipe.apply {
            card = view.findViewById(R.id.card)
            noTapViews = listOf(sourceBtn)
            tapEnabled = AppPrefs.tapAdvance()
            canPage = { dir -> rn.canPage(dir) }
            canFinish = { rn.canFinish() }
            onFinish = {
                Tracking.record(key, items.size, items.size)
                AppPrefs.clearPosition(key)
                WidgetStore.syncFolderProgress(ctx, key, items.size, 0)
                // finishReading() removes this fragment at once, so keep the activity before calling it.
                val main = nav()
                main.finishReading()
                if (AppPrefs.completionScreen()) main.push(CompleteFragment.of(folderTitle, key))
            }
            onPage = { dir ->
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
    override fun onDestroyView() { immersive(false); super.onDestroyView() }

    companion object {
        fun of(key: String, start: Int = -1) = ReaderFragment().apply { arguments = Bundle().apply { putString("key", key); putInt("start", start) } }
    }
}
