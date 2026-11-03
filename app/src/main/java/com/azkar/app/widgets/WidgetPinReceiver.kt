package com.azkar.app.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.azkar.app.launchWhenReady

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val (kind, style) = WidgetStore.consumePendingPin(context) ?: return
        launchWhenReady(context) {
            WidgetStore.setConfig(context, id, style, WidgetStore.config(context, id, kind).folder)
            WidgetUpdater.update(context, AppWidgetManager.getInstance(context), id, kind)
            WidgetUpdater.scheduleRefresh(context, id, kind)
        }
    }
}
