package com.azkar.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class StoreRollbackTest {
    private lateinit var context: Context
    private lateinit var dao: ControlledDao
    private lateinit var repository: AzkarRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("azkar", Context.MODE_PRIVATE).edit().clear().commit()
        dao = ControlledDao()
        repository = AzkarRepository(dao)
        repository.replaceAllAsync(initialContent)
        installStoreSnapshot(initialContent)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("azkar", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun failedStoreWriteRollsBackCacheAndKeepsRoomConsistent() = runBlocking {
        dao.failNext = true

        Store.addFolder("Failed")
        repository.awaitPendingWrites()

        assertEquals(initialContent.folders, Store.folders())
        assertEquals(initialContent, repository.loadContent())
    }

    @Test
    fun failedOlderWriteDoesNotRollbackNewerMutation() = runBlocking {
        dao.failNext = true

        Store.addFolder("Older")
        Store.addFolder("Newer")
        repository.awaitPendingWrites()

        val expected = listOf(
            initialContent.folders.single(),
            Store.folders().first { it.name == "Older" },
            Store.folders().first { it.name == "Newer" }
        )
        assertEquals(expected, Store.folders())
        assertEquals(AzkarContent(expected, emptyList(), emptyList()), repository.loadContent())
    }

    private fun installStoreSnapshot(content: AzkarContent) {
        setField("sp", context.getSharedPreferences("azkar", Context.MODE_PRIVATE))
        setField("appCtx", context)
        setField("repository", repository)
        setField("zCache", content.zikrs)
        setField("fCache", content.folders)
        setField("sCache", content.schedules)
        setField("cacheGeneration", 0L)
        setField("widgetRefreshPending", AtomicBoolean(true))
        setField("initStarted", AtomicBoolean(true))
        setField("initResult", kotlinx.coroutines.CompletableDeferred(Result.success(Unit)))
    }

    private fun setField(name: String, value: Any?) {
        Store::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(Store, value)
        }
    }

    private class ControlledDao : AzkarDao {
        private var folders = emptyList<FolderEntity>()
        var failNext = false

        override fun observeFolders(): Flow<List<FolderEntity>> = emptyFlow()
        override fun observeZikrs(): Flow<List<ZikrEntity>> = emptyFlow()
        override fun observeSchedules(): Flow<List<ScheduleEntity>> = emptyFlow()
        override suspend fun getFoldersOnce() = folders
        override suspend fun getZikrsOnce() = emptyList<ZikrEntity>()
        override suspend fun getSchedulesOnce() = emptyList<ScheduleEntity>()
        override suspend fun deleteFolders() = Unit
        override suspend fun deleteZikrs() = Unit
        override suspend fun deleteSchedules() = Unit
        override suspend fun insertFolders(folders: List<FolderEntity>) {
            this.folders = folders
        }
        override suspend fun insertZikrs(zikrs: List<ZikrEntity>) = Unit
        override suspend fun insertSchedules(schedules: List<ScheduleEntity>) = Unit
        override suspend fun replaceAllAsync(
            folders: List<FolderEntity>,
            zikrs: List<ZikrEntity>,
            schedules: List<ScheduleEntity>
        ) {
            if (failNext) {
                failNext = false
                error("simulated Room write failure")
            }
            this.folders = folders
        }
    }

    private companion object {
        val initialContent = AzkarContent(
            folders = listOf(Folder("initial", "Initial")),
            zikrs = emptyList(),
            schedules = emptyList()
        )
    }
}
