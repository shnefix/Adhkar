package com.azkar.app.data

/** Pure merge of an import into the current data. Never overwrites or deletes anything that exists. */
object ImportMerge {
    data class State(val folders: List<Folder>, val zikrs: List<Zikr>, val schedules: List<Schedule>)
    data class Summary(val created: Int, val merged: Int, val added: Int, val skipped: Int, val reminders: Int)

    fun apply(s: State, p: ImportJson.Parsed.Ok, newId: () -> String): Pair<State, Summary> {
        val fl = s.folders.toMutableList()
        val zl = s.zikrs.toMutableList()
        val sl = s.schedules.toMutableList()
        val idMap = HashMap<String, String>()          // folder id in the file -> folder id in the app
        var created = 0; var merged = 0; var added = 0; var skipped = 0; var rem = 0

        fun addItems(folderId: String?, items: List<ImportJson.Item>) {
            items.forEach { item ->
                if (zl.any { it.folderId == folderId && it.arabic == item.text }) skipped++
                else { zl.add(Zikr(newId(), item.text, item.note, item.count, folderId)); added++ }
            }
        }

        p.folders.forEach { f ->
            val byId = f.id?.let { id -> fl.firstOrNull { it.id == id } }
            val existing = byId ?: fl.firstOrNull { it.name.trim().equals(f.name.trim(), ignoreCase = true) }
            val folder = existing ?: run {
                val id = f.id?.takeIf { c -> c != "anytime" && !c.startsWith("zikr:") && fl.none { it.id == c } } ?: newId()
                Folder(id, f.name.trim(), f.icon).also { fl.add(it); created++ }
            }
            if (existing != null) merged++
            f.id?.let { idMap[it] = folder.id }
            addItems(folder.id, f.items)
        }
        addItems(null, p.unfiled)
        p.reminders.forEach { r ->
            val id = r.id.ifBlank { newId() }
            if (sl.none { it.id == id }) {
                val target = r.folder?.let { ref -> idMap[ref] ?: fl.firstOrNull { it.id == ref }?.id }
                sl.add(Schedule(id, r.title, r.message, r.hour, r.minute, r.enabled, target)); rem++
            }
        }
        return State(fl, zl, sl) to Summary(created, merged, added, skipped, rem)
    }
}