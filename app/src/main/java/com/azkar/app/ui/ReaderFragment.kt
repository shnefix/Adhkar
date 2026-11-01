package com.azkar.app.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.Builtins
import com.azkar.app.data.ReaderResume
import com.azkar.app.data.Store

class ReaderFragment : Fragment(R.layout.fragment_reader) {
    private lateinit var rn: ReaderNav
    private var lastBack = 0L

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::rn.isInitialized) { outState.putInt("i", rn.index); outState.putInt("count", rn.count) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        view.keepScreenOn = Store.keepScreenOn()   // a view flag: released automatically when the reader goes away
        view.isHapticFeedbackEnabled = false
        view.findViewById<View>(R.id.ring).isHapticFeedbackEnabled = false
        view.findViewById<View>(R.id.btn_back).isHapticFeedbackEnabled = false

        val key = requireArguments().getString("key") ?: "morning"
        val items = Store.readerItems(key)
        if (items.isEmpty()) { nav().pop(); return }
        val explicit = requireArguments().getInt("start", -1)
val remember = Store.rememberPosition(key)
val (startIdx, startCount) = when {
    savedInstanceState != null -> savedInstanceState.getInt("i") to savedInstanceState.getInt("count")
    explicit >= 0 -> explicit to 0
    remember -> {
        val sv = Store.savedPosition(key)
        ReaderResume.start(items.map { it.id }, items.map { it.target }, sv?.first, sv?.second ?: 0)
    }
    else -> 0 to 0
}
rn = ReaderNav(items.map { it.target }, startIdx, startCount)
        val folderTitle = Store.folderName(key)
        val title = if (key in listOf("morning", "evening", "afterPrayer", "sleep", "waking") &&
            Builtins.byKey(key)?.title == folderTitle) "$folderTitle azkar" else folderTitle
        val sizeSp = Store.arabicSp()
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
                else { lastBack = now; Toast.makeText(ctx, "Press back again to exit", Toast.LENGTH_SHORT).show() }
            }
        })

        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }
        view.findViewById<TextView>(R.id.title).text = title
        val ar = view.findViewById<TextView>(R.id.ar).apply {
            textSize = sizeSp.toFloat()
            val lhPx = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp * 1.85f, resources.displayMetrics)
            androidx.core.widget.TextViewCompat.setLineHeight(this, lhPx.toInt())
        }
        val arScroll = view.findViewById<ScrollView>(R.id.ar_scroll)
        val segs = view.findViewById<LinearLayout>(R.id.segs)
        val ring = view.findViewById<RingView>(R.id.ring)
        val countTv = view.findViewById<TextView>(R.id.count)
        val sub = view.findViewById<TextView>(R.id.sub)
        val hint = view.findViewById<TextView>(R.id.hint)
        val pos = view.findViewById<TextView>(R.id.pos)

        fun render() {
            val i = rn.index
            val cur = items[i]
            val done = rn.isDone
            ar.text = cur.arabic
            pos.text = "${i + 1} of ${items.size}"
            segs.removeAllViews()
            items.indices.forEach { k ->
                val c = if (k < i) teal else if (k == i) gold else track
                val seg = View(ctx).apply { background = Ui.shape(ctx, c, null, 2f) }
                val p = LinearLayout.LayoutParams(0, Ui.pxi(ctx, 4f), 1f)
                if (k > 0) p.marginStart = Ui.pxi(ctx, 6f)
                segs.addView(seg, p)
            }
            countTv.text = rn.count.toString()
            sub.text = if (done) "Done" else "of ${cur.target}"
            ring.setProgress(Math.round(rn.count.toFloat() / cur.target * 360f).toFloat(), if (done) teal else gold)
            ring.visibility = if (cur.target <= 1) View.GONE else View.VISIBLE
            hint.text = if (rn.isLast && cur.target > 1)
                (if (done) "You have finished this collection" else "Tap the circle to count") else ""
            if (remember && items[rn.index].id.isNotEmpty()) Store.savePosition(key, items[rn.index].id, rn.count)
        }

        ring.setOnClickListener {
            if (rn.tapRing()) render()
        }

        (view as SwipeLayout).apply {
            card = view.findViewById(R.id.card)
            tapEnabled = Store.tapAdvance()
            canPage = { dir -> rn.canPage(dir) }
            canFinish = { rn.canFinish() }
            onFinish = {
                Store.clearPosition(key)
                nav().finishReading()
            }
            onPage = { dir ->
                if (dir > 0) rn.next() else rn.prev()
                arScroll.scrollTo(0, 0)
                render()
            }
        }
        render()
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
        fun of(key: String, start: Int = -1) = ReaderFragment().apply { arguments = bundleOf("key" to key, "start" to start) }
    }
}
