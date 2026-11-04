package com.azkar.app.data

import androidx.room.ColumnInfo
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
    val position: Int,
    val title: String = "",
    /** [TitlePos.code]; the column keeps its first name, from when it was only "below or not". */
    @ColumnInfo(name = "titleBelow") val titlePos: Int = 0,
    val source: String = ""
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
