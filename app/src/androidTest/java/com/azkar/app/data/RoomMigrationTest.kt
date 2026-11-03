package com.azkar.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    private lateinit var database: AzkarDatabase
    private lateinit var repository: AzkarRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AzkarDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = AzkarRepository(database.azkarDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun firstInstallationLeavesEmptyRoomUsable() {
        assertEquals(0, repository.folderCount())
        assertFalse(runBlocking {
            RoomMigration.importIfEmpty(repository, RoomMigration.LegacyData(emptyList(), emptyList(), emptyList()))
        })
    }

    @Test
    fun existingUserDataIsImportedAndPreserved() {
        val data = RoomMigration.LegacyData(
            listOf(Folder("f", "Personal", "📚", true)),
            listOf(Zikr("z", "Sample zikr", "note", 7, "f")),
            listOf(Schedule("s", "Reminder", "message", 8, 30, true, "f"))
        )
        assertTrue(runBlocking { RoomMigration.importIfEmpty(repository, data) })
        assertEquals(data.folders, repository.folders())
        assertEquals(data.zikrs, repository.zikrs())
        assertEquals(data.schedules, repository.schedules())
    }

    @Test
    fun repeatedMigrationDoesNotOverwriteExistingData() {
        val first = RoomMigration.LegacyData(
            listOf(Folder("f", "Original")),
            listOf(Zikr("z", "Original", "", 1, "f")),
            emptyList()
        )
        val second = first.copy(folders = listOf(Folder("f2", "New")))
        assertTrue(runBlocking { RoomMigration.importIfEmpty(repository, first) })
        assertFalse(runBlocking { RoomMigration.importIfEmpty(repository, second) })
        assertEquals("Original", repository.folders().single().name)
    }

    @Test
    fun duplicateIdsAreRejected() {
        val data = RoomMigration.LegacyData(
            listOf(Folder("f", "One"), Folder("f", "Two")),
            emptyList(),
            emptyList()
        )
        assertFalse(RoomMigration.validate(data))
        assertFalse(runBlocking { RoomMigration.importIfEmpty(repository, data) })
    }

    @Test
    fun invalidCountsAndTimesAreRejected() {
        assertFalse(RoomMigration.validate(RoomMigration.LegacyData(
            listOf(Folder("f", "Folder")),
            listOf(Zikr("z", "Sample zikr", "", 0, "f")),
            emptyList()
        )))
        assertFalse(RoomMigration.validate(RoomMigration.LegacyData(
            emptyList(),
            emptyList(),
            listOf(Schedule("s", "Reminder", "message", 24, 0, true, null))
        )))
    }

    @Test
    fun replaceAllPreservesAllTablesAndOrdering() {
        val data = RoomMigration.LegacyData(
            listOf(Folder("f1", "First"), Folder("f2", "Second")),
            listOf(
                Zikr("z1", "One", "", 1, "f1"),
                Zikr("z2", "Two", "", 2, "f2")
            ),
            listOf(Schedule("s1", "Reminder", "message", 8, 0, true, "f1"))
        )
        runBlocking {
            repository.replaceAllAsync(AzkarContent(data.folders, data.zikrs, data.schedules))
        }
        assertEquals(data.folders, repository.folders())
        assertEquals(data.zikrs, repository.zikrs())
        assertEquals(data.schedules, repository.schedules())
    }

    @Test
    fun suspendOperationsReadWriteAndObserveRoomContent() = runBlocking {
        val data = AzkarContent(
            listOf(Folder("f", "Personal")),
            listOf(Zikr("z", "Sample zikr", "", 1, "f")),
            emptyList()
        )

        repository.replaceAllAsync(data)

        assertEquals(data, repository.loadContent())
        assertEquals(data, repository.content.first())
    }

    @Test
    fun concurrentSuspendReplacementsRemainConsistent() = runBlocking {
        coroutineScope {
            (1..4).map { index ->
                async {
                    repository.replaceAllAsync(
                        AzkarContent(
                            listOf(Folder("f$index", "Folder $index")),
                            listOf(Zikr("z$index", "Sample zikr $index", "", index, "f$index")),
                            emptyList()
                        )
                    )
                }
            }.awaitAll()
        }

        val result = repository.loadContent()
        assertEquals(1, result.folders.size)
        assertEquals(1, result.zikrs.size)
        assertEquals(result.folders.single().id, result.zikrs.single().folderId)
    }

    @Test
    fun flowCollectionCanBeCancelled() = runBlocking {
        val collection = launch {
            repository.content.collect { }
        }
        collection.cancel()
        assertTrue(collection.isCancelled)
    }

    @Test
    fun compatibilityWriteReportsRoomFailure() = runBlocking {
        val failing = AzkarRepository(FailingDao())
        val completion = CompletableDeferred<Result<AzkarContent>>()
        val result = failing.replaceAll(
            AzkarContent(listOf(Folder("f", "Folder")), emptyList(), emptyList())
        ) { completion.complete(it) }.await()

        assertTrue(result.isFailure)
        assertTrue(completion.await().isFailure)
        assertEquals(0, failing.folderCount())
    }

    @Test
    fun awaitPendingWritesPropagatesRoomFailure() = runBlocking {
        val failing = AzkarRepository(FailingDao())
        failing.replaceAll(AzkarContent(listOf(Folder("f", "Folder")), emptyList(), emptyList()))

        try {
            failing.awaitPendingWrites()
            fail("Expected the pending Room failure to be propagated")
        } catch (_: IllegalStateException) {
            // Expected: initialization callers must not observe readiness after a failed write.
        }
    }

    private class FailingDao : AzkarDao {
        override fun observeFolders() = emptyFlow<List<FolderEntity>>()
        override fun observeZikrs() = emptyFlow<List<ZikrEntity>>()
        override fun observeSchedules() = emptyFlow<List<ScheduleEntity>>()
        override suspend fun getFoldersOnce() = emptyList<FolderEntity>()
        override suspend fun getZikrsOnce() = emptyList<ZikrEntity>()
        override suspend fun getSchedulesOnce() = emptyList<ScheduleEntity>()
        override suspend fun deleteFolders() = Unit
        override suspend fun deleteZikrs() = Unit
        override suspend fun deleteSchedules() = Unit
        override suspend fun insertFolders(folders: List<FolderEntity>) = Unit
        override suspend fun insertZikrs(zikrs: List<ZikrEntity>) = Unit
        override suspend fun insertSchedules(schedules: List<ScheduleEntity>) = Unit
        override suspend fun replaceAllAsync(
            folders: List<FolderEntity>,
            zikrs: List<ZikrEntity>,
            schedules: List<ScheduleEntity>
        ): Unit = error("simulated Room failure")
    }
}
