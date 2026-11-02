package com.azkar.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.azkar.app.data.Store
import com.azkar.app.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AzkarApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        ReminderScheduler.createChannel(this)
        appScope.launch {
            runCatching {
                Store.awaitReady()
                ReminderScheduler.rescheduleAll(this@AzkarApp)
            }.onFailure { android.util.Log.e("Azkar", "Background startup failed", it) }
        }
    }
}
