package com.azkar.app

import android.app.Application
import com.azkar.app.data.Store
import com.azkar.app.reminders.ReminderScheduler

class AzkarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        ThemeMode.apply(0)   // before any activity exists
        ReminderScheduler.createChannel(this)
        ReminderScheduler.rescheduleAll(this)
    }
}
