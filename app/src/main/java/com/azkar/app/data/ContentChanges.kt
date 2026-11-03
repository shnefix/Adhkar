package com.azkar.app.data

/** The rows one content write must insert / update and delete, so an edit touches only what changed. */
data class ContentChanges(
    val folders: List<FolderEntity> = emptyList(),
    val deletedFolders: List<String> = emptyList(),
    val zikrs: List<ZikrEntity> = emptyList(),
    val deletedZikrs: List<String> = emptyList(),
    val schedules: List<ScheduleEntity> = emptyList(),
    val deletedSchedules: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = folders.isEmpty() && deletedFolders.isEmpty() && zikrs.isEmpty() && deletedZikrs.isEmpty() &&
            schedules.isEmpty() && deletedSchedules.isEmpty()

    companion object {
        /** Rows of [new] that are missing from or differ in [old] (position included), and ids only [old] has. */
        fun <E> diff(old: List<E>, new: List<E>, id: (E) -> String): Pair<List<E>, List<String>> {
            val before = old.associateBy(id)
            val upserts = new.filter { before[id(it)] != it }
            val kept = new.mapTo(HashSet(), id)
            return upserts to old.map(id).filter { it !in kept }
        }

        fun between(
            oldFolders: List<FolderEntity>, newFolders: List<FolderEntity>,
            oldZikrs: List<ZikrEntity>, newZikrs: List<ZikrEntity>,
            oldSchedules: List<ScheduleEntity>, newSchedules: List<ScheduleEntity>
        ): ContentChanges {
            val (f, df) = diff(oldFolders, newFolders) { it.id }
            val (z, dz) = diff(oldZikrs, newZikrs) { it.id }
            val (s, ds) = diff(oldSchedules, newSchedules) { it.id }
            return ContentChanges(f, df, z, dz, s, ds)
        }
    }
}
