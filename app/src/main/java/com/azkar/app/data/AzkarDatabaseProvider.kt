package com.azkar.app.data

import android.content.Context
import androidx.room.Room

object AzkarDatabaseProvider {
    @Volatile
    private var instance: AzkarDatabase? = null

    fun get(context: Context): AzkarDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AzkarDatabase::class.java, "azkar.db")
                .addMigrations(AzkarRoomMigrations.V1_TO_V2)
                .build()
                .also { instance = it }
        }
}
