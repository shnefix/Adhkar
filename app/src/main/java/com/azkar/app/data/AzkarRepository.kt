package com.azkar.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AzkarContent(
    val folders: List<Folder>,
    val zikrs: List<Zikr>,
    val schedules: List<Schedule>
)

@Singleton
class AzkarRepository @Inject constructor(
    private val dao: AzkarDao
) {
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val pendingLock = Any()
    private var lastWrite: Deferred<Result<AzkarContent>>? = null

    @Volatile
    private var snapshot = AzkarContent(emptyList(), emptyList(), emptyList())

    /**
     * True once [snapshot] mirrors Room (after [loadContent] or any successful write). Until then a write replaces
     * every table; afterwards it writes only the rows that differ from the snapshot.
     */
    @Volatile
    private var snapshotIsRoom = false

    val content: Flow<AzkarContent> = combine(
        dao.observeFolders(),
        dao.observeZikrs(),
        dao.observeSchedules()
    ) { folders, zikrs, schedules ->
        AzkarContent(
            folders = folders.map { Folder(it.id, it.name, it.icon, it.remember) },
            zikrs = zikrs.map { Zikr(it.id, it.arabic, it.note, it.repeat, it.folderId, it.title, TitlePos.of(it.titlePos), it.sourceInfo(), it.titleBottom) },
            schedules = schedules.map {
                Schedule(it.id, it.title, it.message, it.hour, it.minute, it.enabled, it.open)
            }
        )
    }

    suspend fun loadContent(): AzkarContent = withContext(Dispatchers.IO) {
        AzkarContent(
            folders = dao.getFoldersOnce().map { Folder(it.id, it.name, it.icon, it.remember) },
            zikrs = dao.getZikrsOnce().map { Zikr(it.id, it.arabic, it.note, it.repeat, it.folderId, it.title, TitlePos.of(it.titlePos), it.sourceInfo(), it.titleBottom) },
            schedules = dao.getSchedulesOnce().map {
                Schedule(it.id, it.title, it.message, it.hour, it.minute, it.enabled, it.open)
            }
        ).also { snapshot = it; snapshotIsRoom = true }
    }

    suspend fun replaceAllAsync(content: AzkarContent) {
        val next = normalize(content)
        enqueue(next).await().getOrThrow()
    }

    /**
     * Compatibility write for platform callbacks that cannot suspend. It updates
     * the snapshot immediately and serializes the Room transaction on IO.
     */
    fun replaceAll(content: AzkarContent) {
        replaceAll(content) { }
    }

    fun replaceAll(content: AzkarContent, onComplete: (Result<AzkarContent>) -> Unit): Deferred<Result<AzkarContent>> {
        val next = normalize(content)
        val write = enqueue(next, onComplete)
        return write
    }

    private fun enqueue(
        next: AzkarContent,
        onComplete: (Result<AzkarContent>) -> Unit = {}
    ): Deferred<Result<AzkarContent>> {
        val previous = synchronized(pendingLock) { lastWrite }
        val write = writeScope.async {
            previous?.await()
            val result = runCatching {
                writeMutex.withLock {
                    if (snapshotIsRoom) {
                        // Writes are serialized and the snapshot only moves on success, so it is what Room holds now.
                        val old = snapshot
                        val changes = ContentChanges.between(
                            old.toFolders(), next.toFolders(), old.toZikrs(), next.toZikrs(),
                            old.toSchedules(), next.toSchedules()
                        )
                        if (!changes.isEmpty) dao.applyChanges(changes)
                    } else {
                        dao.replaceAllAsync(next.toFolders(), next.toZikrs(), next.toSchedules())
                    }
                }
                snapshot = next
                snapshotIsRoom = true
                next
            }
            result.exceptionOrNull()?.let { android.util.Log.e("Azkar", "Asynchronous content write failed", it) }
            runCatching { onComplete(result) }
                .onFailure { android.util.Log.e("Azkar", "Content write callback failed", it) }
            result
        }
        synchronized(pendingLock) { lastWrite = write }
        return write
    }

    suspend fun awaitPendingWrites() {
        val pending = synchronized(pendingLock) { lastWrite }
        pending?.await()?.getOrThrow()
    }

    // These compatibility reads are snapshots only; they never access Room or block.
    fun folders(): List<Folder> = snapshot.folders
    fun zikrs(): List<Zikr> = snapshot.zikrs
    fun schedules(): List<Schedule> = snapshot.schedules
    fun folderCount(): Int = snapshot.folders.size
    fun zikrCount(): Int = snapshot.zikrs.size
    fun scheduleCount(): Int = snapshot.schedules.size

    private fun normalize(content: AzkarContent) =
        AzkarContent(content.folders.toList(), content.zikrs.toList(), content.schedules.toList())

    private fun AzkarContent.toFolders() = folders.mapIndexed { index, item ->
        FolderEntity(item.id, item.name, item.icon, item.remember, index)
    }

    private fun AzkarContent.toZikrs() = zikrs.mapIndexed { index, item ->
        ZikrEntity(item.id, item.arabic, item.note, item.repeat, item.folderId, index, item.title, item.titlePos.code,
            item.source.reference, item.source.detail, item.source.narrator, item.source.whenToSay, item.source.grade, item.titleBelow)
    }

    private fun AzkarContent.toSchedules() = schedules.mapIndexed { index, item ->
        ScheduleEntity(item.id, item.title, item.message, item.hour, item.minute, item.enabled, item.open, index)
    }
}
