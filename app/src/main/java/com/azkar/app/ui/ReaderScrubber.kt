package com.azkar.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import com.azkar.app.R

/**
 * The reader's progress bar, which is also a slider: one segment per zikr (a plain bar when there are too many to
 * tell apart), with a thumb on the current one. Dragging moves the thumb with the finger, [onScrub] reports the zikr
 * under it as it changes, and letting go reports it to [onPick] while the thumb eases onto that segment.
 * Right to left in Arabic, like the rest of the reader.
 */
class ReaderScrubber @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var onScrub: ((Int) -> Unit)? = null
    var onPick: ((Int) -> Unit)? = null

    private var total = 1
    private var current = 0
    /** Thumb position as a fraction of the width, from the start edge (right in Arabic). */
    private var thumb = 0f
    private var dragging = false
    private var dragIndex = 0
    private var anim: ValueAnimator? = null

    private val d = resources.displayMetrics.density
    private val gold = ctx.getColor(R.color.gold)
    private val teal = ctx.getColor(R.color.teal)
    private val track = ctx.getColor(R.color.track)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.getColor(R.color.bg); style = Paint.Style.STROKE }
    private val r = RectF()

    init {
        isFocusable = true
        isClickable = true
        contentDescription = ctx.getString(R.string.reader_slider)
    }

    private val rtl get() = layoutDirection == LAYOUT_DIRECTION_RTL

    /** Centre of segment [i] as a fraction of the width. */
    private fun centre(i: Int) = (i + 0.5f) / total

    /** Shows zikr [index] of [count]; the thumb glides there unless [animate] is false or the user is dragging. */
    fun setPosition(index: Int, count: Int, animate: Boolean = true) {
        val sizeChanged = count != total
        total = count.coerceAtLeast(1)
        current = index.coerceIn(0, total - 1)
        if (dragging) { invalidate(); return }
        val to = centre(current)
        anim?.cancel()
        if (!animate || sizeChanged || width == 0) { thumb = to; invalidate(); return }
        anim = ValueAnimator.ofFloat(thumb, to).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { thumb = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun fractionAt(x: Float): Float {
        val pad = 10 * d
        val f = ((x - pad) / (width - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return if (rtl) 1f - f else f
    }

    private fun indexAt(f: Float) = (f * total).toInt().coerceIn(0, total - 1)

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (total <= 1) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The swipe layout around the reader must not take this drag for a page swipe.
                parent?.requestDisallowInterceptTouchEvent(true)
                anim?.cancel()
                dragging = true
                dragIndex = current
                move(e.x)
            }
            MotionEvent.ACTION_MOVE -> move(e.x)
            MotionEvent.ACTION_UP -> {
                move(e.x)
                dragging = false
                val pick = dragIndex
                setPosition(pick, total)          // ease onto the chosen segment
                onPick?.invoke(pick)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; setPosition(current, total) }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun move(x: Float) {
        thumb = fractionAt(x)
        val i = indexAt(thumb)
        if (i != dragIndex) { dragIndex = i; onScrub?.invoke(i) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = 10 * d
        val w = width - 2 * pad
        val cy = height / 2f
        val h = (if (dragging) 6f else 4f) * d
        val shown = if (dragging) dragIndex else current
        fun x(f: Float) = if (rtl) pad + w * (1f - f) else pad + w * f
        val gap = when { total <= 12 -> 6 * d; total <= 40 -> 3 * d; else -> 0f }
        if (gap > 0f) {
            val seg = (w - gap * (total - 1)) / total
            for (k in 0 until total) {
                paint.color = if (k < shown) teal else if (k == shown) gold else track
                val start = k * (seg + gap)
                val a = if (rtl) pad + w - start - seg else pad + start
                r.set(a, cy - h / 2, a + seg, cy + h / 2)
                canvas.drawRoundRect(r, h / 2, h / 2, paint)
            }
        } else {
            paint.color = track
            r.set(pad, cy - h / 2, pad + w, cy + h / 2)
            canvas.drawRoundRect(r, h / 2, h / 2, paint)
            paint.color = teal
            val done = x(shown.toFloat() / total)
            r.set(minOf(x(0f), done), cy - h / 2, maxOf(x(0f), done), cy + h / 2)
            canvas.drawRoundRect(r, h / 2, h / 2, paint)
        }
        if (total > 1) {
            val tr = (if (dragging) 10f else 7f) * d
            paint.color = gold
            val tx = x(thumb)
            canvas.drawCircle(tx, cy, tr, paint)
            ring.strokeWidth = 2 * d
            canvas.drawCircle(tx, cy, tr, ring)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, rr: Int, b: Int) {
        super.onLayout(changed, l, t, rr, b)
        if (!dragging) thumb = centre(current)
        // Dragging from the very edge must move the slider, not open the system back gesture.
        if (Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
    }

    // Screen readers: announced as a slider, with "scroll forward / backward" moving one zikr.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 1f, total.toFloat(), (current + 1).toFloat()
        )
        if (current > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (current < total - 1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val to = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> current + 1
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> current - 1
            else -> return super.performAccessibilityAction(action, arguments)
        }
        if (to !in 0 until total) return false
        onPick?.invoke(to)
        return true
    }
}
