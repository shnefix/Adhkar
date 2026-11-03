package com.azkar.app.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.azkar.app.launchWhenReady
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles widget taps that change state (count, reset, next, undo). It is not exported, so only this
 * app's own PendingIntents can reach it; the widget providers must stay exported for the launcher.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || intent.action !in ACTIONS) return
        val family = intent.getStringExtra(EXTRA_FAMILY)?.let { name -> AzkarWidgetFamily.values().firstOrNull { it.name == name } }
        val kind = family?.kind ?: intent.getStringExtra(EXTRA_KIND)
            ?.let { name -> WidgetKind.values().firstOrNull { it.name == name } } ?: return
        launchWhenReady(context) {
            // Each tap runs in its own background task; one at a time, so two quick taps can't read the same count.
            taps.withLock {
                when (intent.action) {
                    ACTION_COUNT -> WidgetLogic.tap(context, id, kind)
                    ACTION_RESET -> WidgetLogic.reset(context, id, kind)
                    ACTION_NEXT -> WidgetLogic.next(context, id, kind)
                    ACTION_UNDO -> WidgetLogic.undo(context, id, kind)
                }
            }
            if (family != null) NewWidgetUpdater.updateAll(context)
            else WidgetUpdater.update(context, AppWidgetManager.getInstance(context), id, kind)
        }
    }

    companion object {
        const val EXTRA_FAMILY = "com.azkar.app.widget.FAMILY"
        const val EXTRA_KIND = "com.azkar.app.widget.KIND"
        val ACTIONS = setOf(ACTION_COUNT, ACTION_RESET, ACTION_NEXT, ACTION_UNDO)
        private val taps = Mutex()
    }
}
