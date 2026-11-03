package com.azkar.app.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.Store
import com.azkar.app.widgets.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Keeps the saved location (used for prayer times) current; reschedules reminders when it moves. */
object LocationUpdater {
    /** Prayer times barely change within this distance, so smaller moves don't reschedule. */
    private const val RESCHEDULE_METERS = 5_000f

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Uses the newest cached fix right away, then asks for a fresh one. No-op without permission or with a hand-set location. */
    @SuppressLint("MissingPermission")
    fun refresh(ctx: Context) {
        if (!hasPermission(ctx) || AppPrefs.locationManual()) return
        val app = ctx.applicationContext
        val lm = app.getSystemService(LocationManager::class.java) ?: return
        val providers = lm.getProviders(true)
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { save(app, it) }
        // Not every phone has a working network or fused provider, so ask each enabled one; any fix will do.
        // Android passes null when no fix arrives in time; the type must allow it or Kotlin throws and the app closes.
        val onFix: androidx.core.util.Consumer<Location?> = androidx.core.util.Consumer { loc -> if (loc != null) save(app, loc) }
        val noCancel: android.os.CancellationSignal? = null
        listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { it in providers }
            .forEach { provider ->
                try {
                    LocationManagerCompat.getCurrentLocation(lm, provider, noCancel, ContextCompat.getMainExecutor(app), onFix)
                } catch (e: RuntimeException) {
                    android.util.Log.w("Azkar", "Location request failed for $provider", e)
                }
            }
    }

    private fun save(ctx: Context, loc: Location) {
        if (AppPrefs.locationManual()) return   // a fix that arrives after the user set the location by hand
        val old = AppPrefs.location()
        AppPrefs.setLocation(loc.latitude, loc.longitude)
        val moved = old == null || FloatArray(1).also {
            Location.distanceBetween(old.first, old.second, loc.latitude, loc.longitude, it)
        }[0] > RESCHEDULE_METERS
        if (moved) {
            // Off the main thread: rescheduling reads Store, and the location callback runs on the main executor.
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    Store.awaitReady()
                    ReminderScheduler.rescheduleAll(ctx)
                    // Widgets drawn with the old (or no) location show the wrong prayer windows until their next refresh.
                    WidgetUpdater.updateAll(ctx)
                }.onFailure { android.util.Log.w("Azkar", "Reschedule after location change failed", it) }
            }
        }
    }
}
