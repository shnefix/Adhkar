package com.azkar.app.data

/** Folders inside folders: lookups over a flat folder list linked by [Folder.parent]. */
object FolderTree {
    /** The folders shown directly inside [parent] (null = top level). A folder whose parent is gone counts as top level. */
    fun children(folders: List<Folder>, parent: String?): List<Folder> {
        val ids = folders.mapTo(HashSet()) { it.id }
        return folders.filter { f -> if (parent == null) f.parent == null || f.parent !in ids else f.parent == parent }
    }

    /** [id] and every folder below it. */
    fun subtree(folders: List<Folder>, id: String): Set<String> {
        val kids = folders.groupBy { it.parent }
        val out = LinkedHashSet<String>()
        val stack = ArrayDeque(listOf(id))
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (out.add(cur)) kids[cur]?.forEach { stack.add(it.id) }
        }
        return out
    }

    /** True when walking up from [id] comes back to it. */
    fun cycles(folders: List<Folder>, id: String): Boolean {
        val byId = folders.associateBy { it.id }
        val seen = HashSet<String>()
        var cur = byId[id]?.parent
        while (cur != null) {
            if (cur == id || !seen.add(cur)) return true
            cur = byId[cur]?.parent
        }
        return false
    }

    /** Every folder in tree order (a parent before what it holds) with its depth, for pickers. */
    fun flatten(folders: List<Folder>): List<Pair<Folder, Int>> {
        val out = ArrayList<Pair<Folder, Int>>()
        fun walk(parent: String?, depth: Int, guard: Set<String>) {
            children(folders, parent).forEach { f ->
                if (f.id in guard) return@forEach
                out += f to depth
                walk(f.id, depth + 1, guard + f.id)
            }
        }
        walk(null, 0, emptySet())
        return out
    }
}
