package com.azkar.app.ui

import android.content.Context
import com.azkar.app.R
import com.azkar.app.data.Store
import com.azkar.app.data.Tracking
import com.azkar.app.reminders.PrayerSessions
import com.azkar.app.widgets.NewWidgetUpdater
import com.azkar.app.widgets.WidgetUpdater

/** Morning and evening folders that exist on this install. */
internal fun sessionFolders(): List<String> =
    PrayerSessions.Session.values().map { it.folderId }.filter { Store.folder(it) != null }

/**
 * Turns tracking on or off for [folders], then shows or hides the tracking widgets
 * (they are only offered while at least one folder is tracked) and redraws the placed ones.
 */
internal fun setTracking(ctx: Context, folders: List<String>, on: Boolean) {
    folders.forEach { Tracking.setTracked(it, on) }
    NewWidgetUpdater.syncTrackingProviders(ctx)
    WidgetUpdater.updateAll(ctx)
}

/** "5-day streak · best 12", or what turning tracking on does when it is off. */
internal fun trackingSummary(ctx: Context, folders: List<String>, on: Boolean): String =
    if (!on || folders.isEmpty()) ctx.getString(R.string.tracking_off_summary)
    else ctx.getString(R.string.tracking_streak_summary, Tracking.streak(folders), Tracking.best(folders))
