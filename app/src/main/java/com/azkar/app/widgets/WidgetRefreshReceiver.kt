package com.azkar.app.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.azkar.app.data.Store

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        WidgetUpdater.updateAll(ctx)
    }
}
