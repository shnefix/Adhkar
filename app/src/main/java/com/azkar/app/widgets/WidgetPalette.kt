package com.azkar.app.widgets

import android.content.Context
import com.azkar.app.R

internal data class WidgetPalette(
    val background: Int,
    val border: Int,
    val accent: Int,
    val accentSoft: Int,
    val text: Int,
    val muted: Int,
    val soft: Int,
    val track: Int,
    val beadTrack: Int,
    val chip: Int,
    val panel: Int,
    val panelBorder: Int,
    val outline: Int,
) {
    companion object {
        fun from(ctx: Context, oled: Boolean): WidgetPalette {
            fun color(tealId: Int, oledId: Int): Int = ctx.getColor(if (oled) oledId else tealId)
            return WidgetPalette(
                background = color(R.color.widget_teal_background, R.color.widget_oled_background),
                border = color(R.color.widget_teal_border, R.color.widget_oled_border),
                accent = color(R.color.widget_teal_accent, R.color.widget_oled_accent),
                accentSoft = color(R.color.widget_teal_accent_soft, R.color.widget_oled_accent_soft),
                text = color(R.color.widget_teal_text, R.color.widget_oled_text),
                muted = color(R.color.widget_teal_muted, R.color.widget_oled_muted),
                soft = color(R.color.widget_teal_soft, R.color.widget_oled_soft),
                track = color(R.color.widget_teal_track, R.color.widget_oled_track),
                beadTrack = color(R.color.widget_teal_bead_track, R.color.widget_oled_bead_track),
                chip = color(R.color.widget_teal_chip, R.color.widget_oled_chip),
                panel = color(R.color.widget_teal_panel, R.color.widget_oled_panel),
                panelBorder = color(R.color.widget_teal_panel_border, R.color.widget_oled_panel_border),
                outline = color(R.color.widget_teal_outline, R.color.widget_oled_outline),
            )
        }
    }
}
