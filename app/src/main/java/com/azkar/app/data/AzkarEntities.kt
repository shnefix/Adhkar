package com.azkar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String?,
    val remember: Boolean,
    val position: Int
)

@Entity(tableName = "zikrs")
data class ZikrEntity(
    @PrimaryKey val id: String,
    val arabic: String,
    val note: String,
    val repeat: Int,
    val folderId: String?,
    val position: Int
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val open: String?,
    val position: Int
)
