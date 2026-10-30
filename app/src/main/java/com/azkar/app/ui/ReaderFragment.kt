package com.azkar.app.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
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
import com.azkar.app.data.Store

class ReaderFragment : Fragment(R.layout.fragment_reader) {
    private var i = 0
    private var count = 0
    private var lastBack = 0L

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("i", i); outState.putInt("count", count)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        view.keepScreenOn = true

        val key = requireArguments().getString("key") ?: "morning"
        val col = Builtins.byKey(key)
        val items = Store.readerItems(key)
        if (items.isEmpty()) { nav().pop(); return }
        i = (savedInstanceState?.getInt("i") ?: requireArguments().getInt("start", 0)).coerceIn(0, items.size - 1)
        count = savedInstanceState?.getInt("count") ?: 0
        val title = when {
            key in listOf("morning", "evening", "afterPrayer", "sleep", "waking") -> (col?.title ?: "") + " azkar"
            col != null -> col.title
            else -> Store.folderName(key)
        }
        val sizeSp = intArrayOf(20, 26, 32)[Store.arabicSize().coerceIn(0, 2)]
        val last = items.size - 1
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
                else { lastBack = now; Toast.makeText(ctx, "Press back again to leave", Toast.LENGTH_SHORT).show() }
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
            val cur = items[i]
            val done = count >= cur.target
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
            countTv.text = count.toString()
            sub.text = if (done) "Done" else "of ${cur.target}"
            ring.setProgress(Math.round(count.toFloat() / cur.target * 360f).toFloat(), if (done) teal else gold)
            hint.text = if (i == last) (if (done) "You have finished this collection" else "Tap the circle to count") else ""
        }

        ring.setOnClickListener {
            if (count < items[i].target) {
                count++
                render()
                it.performHapticFeedback(
                    if (count == items[i].target) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
                )
            }
        }

        (view as SwipeLayout).apply {
            card = view.findViewById(R.id.card)
            canPage = { dir -> if (dir > 0) i < last else i > 0 }
            onPage = { dir ->
                i += dir; count = 0
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
        fun of(key: String, start: Int = 0) = ReaderFragment().apply { arguments = bundleOf("key" to key, "start" to start) }
    }
}
