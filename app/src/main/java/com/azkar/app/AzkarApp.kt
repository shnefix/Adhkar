package com.azkar.app

import android.app.Application
import com.azkar.app.data.Store
import com.azkar.app.reminders.ReminderScheduler

class AzkarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        ReminderScheduler.createChannel(this)
        ReminderScheduler.rescheduleAll(this)
    }
}
