package com.azkar.app

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import com.azkar.app.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Runs [block] on IO once [Store] is ready, keeping the broadcast alive with goAsync().
 * A failure (e.g. the database could not be opened) is logged instead of crashing the process
 * from a background receiver.
 */
fun BroadcastReceiver.launchWhenReady(context: Context, block: suspend () -> Unit) {
    Store.init(context)
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            Store.awaitReady()
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Azkar", "Background update failed", e)
        } finally {
            pending.finish()
        }
    }
}
