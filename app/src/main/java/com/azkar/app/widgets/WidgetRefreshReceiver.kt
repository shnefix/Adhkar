package com.azkar.app.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.azkar.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Store.awaitReady()
                WidgetUpdater.updateAll(ctx)
            } finally {
                pending.finish()
            }
        }
    }
}
