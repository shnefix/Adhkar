package com.azkar.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How big a widget's picture is drawn. Android 12+ launchers usually list the exact size they show a widget at, for
 * each orientation; then each size gets its own picture and the launcher shows the one for the space it has. Others
 * only report a min/max range, and that range can't be trusted: ColorOS reports 118×86 to 258×183 dp and gives the
 * widget 123×146, so the old reading (min width × max height) drew it a quarter too tall and the stretched picture
 * came out squashed. Without exact sizes the picture is drawn in the design's own proportions instead, and the
 * layouts scale it evenly into whatever space the widget really has, on a card that always fills it.
 */
object WidgetSizing {
    private const val TAG = "AzkarWidgetSize"

    /** RemoteViews allows a handful of sizes; launchers send two (portrait, landscape), foldables four. */
    private const val MAX_SIZES = 4

    /** Pixels in one widget picture at most (one 4×4 widget at 3× density). */
    const val MAX_PIXELS = 1_100_000f

    /**
     * What to draw. [exact]: the launcher listed the sizes it shows the widget at, and each of [sizes] fills the
     * widget. Otherwise [sizes] holds just the design size, drawn at [scale] times the screen density so it stays
     * sharp in the largest space the launcher allows (the layout only ever shrinks it to fit).
     */
    data class Plan(val sizes: List<SizeF>, val exact: Boolean, val scale: Float)

    /** The [Plan] for a widget whose design is [design] dp; [fill] false always draws the design (the dials). */
    fun plan(ctx: Context, options: Bundle, design: Pair<Float, Float>, id: Int = 0, fill: Boolean = true): Plan {
        val exact = exactSizes(options)
        val plan = if (fill && exact.isNotEmpty()) Plan(exact, true, 1f) else {
            val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val largest = exact.ifEmpty { if (maxW > 0 && maxH > 0) listOf(SizeF(maxW.toFloat(), maxH.toFloat())) else emptyList() }
            val scale = largest.maxOfOrNull { min(it.width / design.first, it.height / design.second) } ?: 1f
            Plan(listOf(SizeF(design.first, design.second)), false, max(1f, scale))
        }
        if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d(TAG, "widget $id: sizes=${exact.joinToString()} " +
                "min=${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}x${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)} " +
                "max=${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)}x${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)} " +
                "-> ${if (plan.exact) "exact" else "design ×${"%.2f".format(plan.scale)}"} ${plan.sizes.joinToString()}")
        }
        return plan
    }

    /**
     * The launcher's best guess at the widget's size in the current orientation (exact when it listed sizes), for
     * in-app previews and for the classic widgets' card, which is stretched to fill the widget.
     */
    fun current(ctx: Context, options: Bundle, fallback: Pair<Float, Float>): SizeF {
        val exact = exactSizes(options)
        if (exact.isEmpty()) return fromRange(ctx, options, fallback)
        if (exact.size == 1) return exact[0]
        return if (landscape(ctx)) exact.maxBy { it.width / it.height } else exact.maxBy { it.height / it.width }
    }

    /** One RemoteViews per size (Android 12+ picks by the widget's real size), or just the one. */
    fun views(sizes: List<SizeF>, build: (SizeF) -> RemoteViews): RemoteViews =
        if (sizes.size == 1 || Build.VERSION.SDK_INT < 31) build(sizes[0])
        else RemoteViews(sizes.associateWith(build))

    /**
     * Pixels each of [count] pictures may use. The launcher refuses an update whose pictures together take
     * more than 1.5 screens of pixels, so several pictures share that, with room to spare.
     */
    fun maxPixels(ctx: Context, count: Int): Float {
        val dm = ctx.resources.displayMetrics
        val screen = dm.widthPixels.toFloat() * dm.heightPixels
        return minOf(MAX_PIXELS, screen * 1.5f * 0.7f / count.coerceAtLeast(1))
    }

    /**
     * Gives [bitmap] a natural size eight times its pixels. A layout that sizes a box to its picture
     * (adjustViewBounds) only ever shrinks the picture to fit, so this lets the box take all the room there is.
     */
    fun prepare(ctx: Context, bitmap: Bitmap): Bitmap =
        bitmap.apply { density = max(1, ctx.resources.displayMetrics.densityDpi / 8) }

    private fun exactSizes(options: Bundle): List<SizeF> {
        if (Build.VERSION.SDK_INT < 31) return emptyList()
        val list = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        return list.orEmpty()
            .filter { it.width >= 1f && it.height >= 1f }
            .distinctBy { it.width.roundToInt() to it.height.roundToInt() }
            .take(MAX_SIZES)
    }

    /** The classic reading of the range: narrow and tall in portrait, wide and short in landscape. */
    private fun fromRange(ctx: Context, o: Bundle, fallback: Pair<Float, Float>): SizeF {
        val land = landscape(ctx)
        val w = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return SizeF(w.takeIf { it > 0 }?.toFloat() ?: fallback.first, h.takeIf { it > 0 }?.toFloat() ?: fallback.second)
    }

    private fun landscape(ctx: Context) = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
