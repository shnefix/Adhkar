package com.azkar.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, ZikrEntity::class, ScheduleEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AzkarDatabase : RoomDatabase() {
    abstract fun azkarDao(): AzkarDao
}
