package com.azkar.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.azkar.app.R

/** 140dp ring: track #24504F, progress arc from 12 o'clock clockwise, 122dp inner disc #0D2124. */
class RingView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {
    private var deg = 0f
    private var arcColor = context.getColor(R.color.gold)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.track) }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.bg) }

    init { setWillNotDraw(false) }

    fun setProgress(degrees: Float, color: Int) {
        deg = degrees; arcColor = color; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = 70f * d
        canvas.drawCircle(cx, cy, r, track)
        if (deg > 0f) {
            arc.color = arcColor
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, deg, true, arc)
        }
        canvas.drawCircle(cx, cy, 61f * d, inner)
    }
}

/** Card-follows-finger swipe layout. dir: +1 = next (swipe left), -1 = previous (swipe right). */
class SwipeLayout @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : LinearLayout(ctx, attrs) {
    var card: View? = null
    var canPage: ((Int) -> Boolean)? = null
    var onPage: ((Int) -> Unit)? = null

    private val vc = android.view.ViewConfiguration.get(ctx)
    private val slop = vc.scaledTouchSlop
    private val minFling = vc.scaledMinimumFlingVelocity * 8f
    private val dp = resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tapOk = false
    private var busy = false
    private var vt: android.view.VelocityTracker? = null

    /** Stops the system back gesture from stealing swipes that start at the screen edge. */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val w = r - l
            val h = b - t
            val c = card
            val cy = if (c != null) (c.top + c.bottom) / 2 else h / 2
            val half = (100 * dp).toInt()
            val top = (cy - half).coerceAtLeast(0)
            val bottom = (cy + half).coerceAtMost(h)
            val edge = (40 * dp).toInt()
            setSystemGestureExclusionRects(
                listOf(android.graphics.Rect(0, top, edge, bottom), android.graphics.Rect(w - edge, top, w, bottom))
            )
        }
    }

    private fun inCard(x: Float, y: Float): Boolean {
        val c = card ?: return false
        return x >= c.left && x <= c.right && y >= c.top && y <= c.bottom
    }

    private fun far(e: MotionEvent) =
        kotlin.math.hypot(e.x - downX, e.y - downY) > slop

    private fun horizontal(e: MotionEvent) =
        kotlin.math.abs(e.x - downX) > slop && kotlin.math.abs(e.x - downX) > kotlin.math.abs(e.y - downY) * 1.2f

    private fun start(e: MotionEvent) {
        downX = e.x; downY = e.y; dragging = false
        tapOk = inCard(e.x, e.y)
        vt?.recycle(); vt = android.view.VelocityTracker.obtain(); vt?.addMovement(e)
    }

    private fun finishTap(e: MotionEvent) {
        if (tapOk && e.eventTime - e.downTime < android.view.ViewConfiguration.getLongPressTimeout()) next()
        tapOk = false
    }

    private fun cleanup() { vt?.recycle(); vt = null }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (busy) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> start(e)
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (!dragging && far(e)) tapOk = false
                if (!dragging && horizontal(e)) { dragging = true; parent?.requestDisallowInterceptTouchEvent(true) }
            }
            MotionEvent.ACTION_UP -> { if (!dragging) finishTap(e); cleanup() }
            MotionEvent.ACTION_CANCEL -> { tapOk = false; cleanup() }
        }
        return dragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (busy) return true
        if (vt == null) vt = android.view.VelocityTracker.obtain()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> start(e)
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (!dragging && far(e)) tapOk = false
                if (!dragging && horizontal(e)) { dragging = true; parent?.requestDisallowInterceptTouchEvent(true) }
                if (dragging) drag(e.x - downX)
            }
            MotionEvent.ACTION_UP -> {
                vt?.addMovement(e)
                if (dragging) release(true) else finishTap(e)
                dragging = false; cleanup()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) release(false)
                dragging = false; tapOk = false; cleanup()
            }
        }
        return true
    }

    private fun dirOf(t: Float) = if (t < 0f) 1 else -1   // finger moved left -> next

    private fun drag(dx: Float) {
        val c = card ?: return
        val d = dx - kotlin.math.sign(dx) * slop
        val ok = canPage?.invoke(dirOf(d)) ?: false
        val t = if (ok) d else d * 0.25f          // rubber band at the first/last azkar
        c.translationX = t
        c.alpha = 1f - 0.6f * minOf(kotlin.math.abs(t) / width.coerceAtLeast(1), 1f)
    }

    private fun release(up: Boolean) {
        val c = card ?: return
        vt?.computeCurrentVelocity(1000)
        val vx = vt?.xVelocity ?: 0f
        val t = c.translationX
        val dir = dirOf(t)
        val ok = canPage?.invoke(dir) ?: false
        val farEnough = kotlin.math.abs(t) > width * 0.28f
        val flung = kotlin.math.abs(vx) > minFling && kotlin.math.sign(vx) == kotlin.math.sign(t) && kotlin.math.abs(t) > slop * 2
        if (up && ok && (farEnough || flung)) commit(c, dir)
        else c.animate().translationX(0f).alpha(1f).setDuration(200)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    /** Next zikr, or a small bounce if this is the last one (never leaves the reader). */
    fun next() {
        val c = card ?: return
        if (busy) return
        if (canPage?.invoke(1) == true) commit(c, 1)
        else c.animate().translationX(-12 * dp).setDuration(90)
            .withEndAction { c.animate().translationX(0f).setDuration(160).start() }.start()
    }

    private fun commit(c: View, dir: Int) {
        busy = true
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        val w = width.toFloat()
        c.animate().translationX(-dir * w).alpha(0f).setDuration(140)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                onPage?.invoke(dir)
                c.translationX = dir * w * 0.35f   // new card enters from the side you swiped toward
                c.alpha = 0f
                c.animate().translationX(0f).alpha(1f).setDuration(220)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .withEndAction { busy = false }.start()
            }.start()
    }
}

/** 52x32 track with 24dp knob, right-aligned in a 56x44 touch area. */
class ToggleSwitch @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var on = false
        set(v) { field = v; invalidate() }
    var onToggle: ((Boolean) -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { on = !on; onToggle?.invoke(on) }
    }

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val left = width - 52f * d
        val top = (height - 32f * d) / 2f
        p.color = context.getColor(if (on) R.color.gold else R.color.track)
        c.drawRoundRect(RectF(left, top, left + 52f * d, top + 32f * d), 16f * d, 16f * d, p)
        p.color = context.getColor(if (on) R.color.bg else R.color.muted)
        val kx = left + (if (on) 24f else 4f) * d + 12f * d
        c.drawCircle(kx, top + 4f * d + 12f * d, 12f * d, p)
    }
}

/** Wrapping row layout with 8dp gaps (flex-wrap). */
class FlowLayout @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : ViewGroup(ctx, attrs) {
    private val gap = (8 * resources.displayMetrics.density).toInt()

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val maxW = MeasureSpec.getSize(wSpec) - paddingLeft - paddingRight
        var x = 0; var y = 0; var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += lineH + gap; lineH = 0 }
            x += c.measuredWidth + gap
            lineH = maxOf(lineH, c.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(wSpec), y + lineH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0; var y = 0; var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += lineH + gap; lineH = 0 }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + c.measuredWidth, paddingTop + y + c.measuredHeight)
            x += c.measuredWidth + gap
            lineH = maxOf(lineH, c.measuredHeight)
        }
    }
}
