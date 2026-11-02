package com.azkar.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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
