package com.azkar.app.widgets

import android.content.Context
import com.azkar.app.R

internal class WidgetStrings(private val ctx: Context) {
    val done = ctx.getString(R.string.widget_done)
    val tasbih = ctx.getString(R.string.widget_tasbih)
    val reset = ctx.getString(R.string.widget_reset)
    val dhikr = ctx.getString(R.string.widget_dhikr)
    val noAzkar = ctx.getString(R.string.widget_no_azkar)
    val tapToOpen = ctx.getString(R.string.widget_tap_to_open)
    fun of(count: Int) = ctx.getString(R.string.widget_of, count)
    fun azkar(count: Int) = ctx.getString(R.string.widget_azkar, count)
    fun countOf(count: Int, total: Int) = ctx.getString(R.string.widget_count_of, count, total)
    fun round(round: Int, rounds: Int, target: Int) = ctx.getString(R.string.widget_round, round, rounds, target)
}
