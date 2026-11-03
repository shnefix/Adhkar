package com.azkar.app.ui

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.azkar.app.R
import com.azkar.app.data.Store
import com.azkar.app.widgets.AzkarWidgetFamily
import com.azkar.app.widgets.AzkarWidgetSizes
import com.azkar.app.widgets.NewWidgetRenderer
import com.azkar.app.widgets.NewWidgetUpdater
import com.azkar.app.widgets.WidgetModel

internal val FAMILY_LABELS = linkedMapOf(
    AzkarWidgetFamily.B_SMALL to R.string.azkar_widget_b_small_label,
    AzkarWidgetFamily.B_MEDIUM to R.string.azkar_widget_b_medium_label,
    AzkarWidgetFamily.B_LARGE to R.string.azkar_widget_b_large_label,
    AzkarWidgetFamily.D_SMALL to R.string.azkar_widget_d_small_label,
    AzkarWidgetFamily.D_MEDIUM to R.string.azkar_widget_d_medium_label,
    AzkarWidgetFamily.D_LARGE to R.string.azkar_widget_d_large_label,
    AzkarWidgetFamily.H_SMALL to R.string.azkar_widget_h_small_label,
    AzkarWidgetFamily.H_MEDIUM to R.string.azkar_widget_h_medium_label,
    AzkarWidgetFamily.H_LARGE to R.string.azkar_widget_h_large_label,
    AzkarWidgetFamily.I_SMALL to R.string.azkar_widget_i_small_label,
    AzkarWidgetFamily.I_MEDIUM to R.string.azkar_widget_i_medium_label,
    AzkarWidgetFamily.I_LARGE to R.string.azkar_widget_i_large_label,
    AzkarWidgetFamily.J_STRIP_DHIKR to R.string.azkar_widget_j_strip_dhikr_label,
    AzkarWidgetFamily.J_STRIP_PROGRESS to R.string.azkar_widget_j_strip_progress_label,
    AzkarWidgetFamily.J_STRIP_DOTS to R.string.azkar_widget_j_strip_dots_label,
    AzkarWidgetFamily.J_STRIP_NEXT to R.string.azkar_widget_j_strip_next_label,
    AzkarWidgetFamily.J_TALL_RING to R.string.azkar_widget_j_tall_ring_label,
    AzkarWidgetFamily.J_TALL_TIMELINE to R.string.azkar_widget_j_tall_timeline_label,
)

/** Full-screen list of the 18 new widget designs with live previews. Tap one to pin it to the home screen. */
fun showNewWidgetPicker(context: Context) {
    Store.init(context)
    fun dp(v: Float) = Ui.pxi(context, v)
    fun bg(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        setColor(context.getColor(fill))
        setStroke(dp(1f), context.getColor(stroke))
        cornerRadius = Ui.px(context, radius)
    }

    val dialog = Dialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20f), dp(18f), dp(20f), dp(24f))
    }
    val titleRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    titleRow.addView(TextView(context).apply {
        text = context.getString(R.string.widget_new_designs)
        textSize = 18f
        setTextColor(context.getColor(R.color.widget_editor_text))
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    titleRow.addView(TextView(context).apply {
        text = "×"
        textSize = 26f
        gravity = Gravity.CENTER
        setTextColor(context.getColor(R.color.widget_editor_text))
        background = bg(R.color.widget_editor_surface2, R.color.widget_editor_border, 22f)
        setOnClickListener { dialog.dismiss() }
    }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
    column.addView(titleRow)
    column.addView(TextView(context).apply {
        text = context.getString(R.string.pick_widget_subtitle)
        textSize = 11f
        setTextColor(context.getColor(R.color.widget_editor_muted))
    })

    val folder = Store.folders().firstOrNull()?.id ?: "anytime"
    val model = try {
        WidgetModel.load(context, AppWidgetManager.INVALID_APPWIDGET_ID, folder)
    } catch (e: Exception) {
        Log.e("NewWidgetPicker", "model load failed", e)
        null
    }
    val renderer = NewWidgetRenderer(context)
    val maxPx = context.resources.displayMetrics.widthPixels - dp(40f + 24f + 24f)

    FAMILY_LABELS.forEach { (family, labelRes) ->
        val (dw, dh) = AzkarWidgetSizes.design(family)
        var wPx = dp(dw)
        var hPx = dp(dh)
        if (wPx > maxPx) {
            val f = maxPx.toFloat() / wPx
            wPx = maxPx
            hPx = (hPx * f).toInt()
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = bg(R.color.widget_editor_surface2, R.color.widget_editor_border, 20f)
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            setOnClickListener { dialog.dismiss(); pinNewWidget(context, family) }
        }
        val preview: View = try {
            val bmp = if (model != null) renderer.render(family, model, dw, dh) else throw IllegalStateException("no model")
            ImageView(context).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        } catch (e: Exception) {
            Log.e("NewWidgetPicker", "preview failed for $family", e)
            TextView(context).apply {
                text = context.getString(R.string.widget_preview_unavailable)
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.widget_editor_muted))
            }
        }
        card.addView(preview, LinearLayout.LayoutParams(wPx, hPx))
        card.addView(TextView(context).apply {
            text = context.getString(labelRes)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(context.getColor(R.color.widget_editor_muted))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(8f)
        })
        column.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.topMargin = dp(12f)
        })
    }

    val scroll = ScrollView(context).apply {
        setBackgroundColor(context.getColor(R.color.widget_editor_surface1))
        addView(column)
    }
    dialog.setContentView(scroll)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.show()
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}

internal fun pinNewWidget(context: Context, family: AzkarWidgetFamily) {
    val manager = AppWidgetManager.getInstance(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
        try {
            manager.requestPinAppWidget(ComponentName(context, NewWidgetUpdater.providerClass(family)), null, null)
            Toast.makeText(context, R.string.widget_pin_requested, Toast.LENGTH_SHORT).show()
        } catch (e: RuntimeException) {
            Log.e("NewWidgetPicker", "pin failed for $family", e)
            Toast.makeText(context, R.string.widget_pin_failed, Toast.LENGTH_LONG).show()
        }
    } else {
        Toast.makeText(context, R.string.widget_pin_unsupported, Toast.LENGTH_LONG).show()
    }
}