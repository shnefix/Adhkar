package com.azkar.app.data

object RoomMigration {
    data class LegacyData(
        val folders: List<Folder>,
        val zikrs: List<Zikr>,
        val schedules: List<Schedule>
    )

    fun validate(data: LegacyData): Boolean {
        fun uniqueIds(ids: List<String>) = ids.all { it.isNotBlank() } && ids.distinct().size == ids.size
        return uniqueIds(data.folders.map { it.id }) &&
            uniqueIds(data.zikrs.map { it.id }) &&
            uniqueIds(data.schedules.map { it.id }) &&
            data.zikrs.all { it.arabic.isNotBlank() && it.repeat in 1..999 } &&
            data.schedules.all { it.hour in 0..23 && it.minute in 0..59 }
    }

    suspend fun importIfEmpty(repository: AzkarRepository, data: LegacyData): Boolean {
        if (!validate(data) || (data.folders.isEmpty() && data.zikrs.isEmpty() && data.schedules.isEmpty()) ||
            repository.folderCount() != 0 || repository.zikrCount() != 0 ||
            repository.scheduleCount() != 0
        ) return false
        repository.replaceAllAsync(AzkarContent(data.folders, data.zikrs, data.schedules))
        return repository.folders() == data.folders &&
            repository.zikrs() == data.zikrs &&
            repository.schedules() == data.schedules
    }
}
