package com.azkar.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Ids per DELETE ... IN (...) statement, safely under SQLite's limit of 999 bound variables. */
private const val DELETE_CHUNK = 500

@Dao
interface AzkarDao {
    @Query("SELECT * FROM folders ORDER BY position")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM zikrs ORDER BY position")
    fun observeZikrs(): Flow<List<ZikrEntity>>

    @Query("SELECT * FROM schedules ORDER BY position")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM folders ORDER BY position")
    suspend fun getFoldersOnce(): List<FolderEntity>

    @Query("SELECT * FROM zikrs ORDER BY position")
    suspend fun getZikrsOnce(): List<ZikrEntity>

    @Query("SELECT * FROM schedules ORDER BY position")
    suspend fun getSchedulesOnce(): List<ScheduleEntity>

    @Query("DELETE FROM folders")
    suspend fun deleteFolders()

    @Query("DELETE FROM zikrs")
    suspend fun deleteZikrs()

    @Query("DELETE FROM schedules")
    suspend fun deleteSchedules()

    @Insert
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Insert
    suspend fun insertZikrs(zikrs: List<ZikrEntity>)

    @Insert
    suspend fun insertSchedules(schedules: List<ScheduleEntity>)

    @Upsert
    suspend fun upsertFolders(folders: List<FolderEntity>)

    @Upsert
    suspend fun upsertZikrs(zikrs: List<ZikrEntity>)

    @Upsert
    suspend fun upsertSchedules(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun deleteFoldersById(ids: List<String>)

    @Query("DELETE FROM zikrs WHERE id IN (:ids)")
    suspend fun deleteZikrsById(ids: List<String>)

    @Query("DELETE FROM schedules WHERE id IN (:ids)")
    suspend fun deleteSchedulesById(ids: List<String>)

    /** Writes only the rows that changed, in one transaction. Deletes go in chunks under SQLite's variable limit. */
    @Transaction
    suspend fun applyChanges(c: ContentChanges) {
        c.deletedFolders.chunked(DELETE_CHUNK).forEach { deleteFoldersById(it) }
        c.deletedZikrs.chunked(DELETE_CHUNK).forEach { deleteZikrsById(it) }
        c.deletedSchedules.chunked(DELETE_CHUNK).forEach { deleteSchedulesById(it) }
        if (c.folders.isNotEmpty()) upsertFolders(c.folders)
        if (c.zikrs.isNotEmpty()) upsertZikrs(c.zikrs)
        if (c.schedules.isNotEmpty()) upsertSchedules(c.schedules)
    }

    @Transaction
    suspend fun replaceAllAsync(
        folders: List<FolderEntity>,
        zikrs: List<ZikrEntity>,
        schedules: List<ScheduleEntity>
    ) {
        deleteFolders()
        deleteZikrs()
        deleteSchedules()
        insertFolders(folders)
        insertZikrs(zikrs)
        insertSchedules(schedules)
    }
}
