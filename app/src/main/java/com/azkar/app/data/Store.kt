package com.azkar.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persistent local storage (SharedPreferences + JSON). Survives app restarts. */
object Store {
    private lateinit var sp: SharedPreferences

    private const val K_ZIKRS = "zikrs"
    private const val K_FOLDERS = "folders"
    private const val K_ORDER = "home_order"
    private const val K_HIDDEN = "home_hidden"
    private const val K_PLACE = "home_place"
    private const val K_TRANS = "show_translation"
    private const val K_SIZE = "arabic_size"
    private const val K_REM = "reminder_"
    private const val K_NOTIF = "notif_on"
    private const val K_SCHED = "schedules"
    private const val K_MIG = "builtins_v2"

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("azkar", Context.MODE_PRIVATE)
        if (!sp.contains(K_ZIKRS)) seed()
        migrateBuiltins()
        migrateSchedules()
    }

    private fun seed() {
        saveFolders(listOf(Folder("family", "Family"), Folder("friday", "Friday")))
        saveZikrs(
            listOf(
                Zikr("seed1", "أَسْتَغْفِرُ اللَّهَ", "I seek forgiveness from Allah.", 100, null),
                Zikr("seed2", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", "There is no power and no strength except with Allah.", 10, "family"),
                Zikr("seed3", "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ", "O Allah, send prayers and peace upon our Prophet Muhammad.", 10, "friday")
            )
        )
    }

    /** One time: the built-in collections become ordinary, fully editable folders with the same ids. */
    private fun migrateBuiltins() {
        if (sp.getBoolean(K_MIG, false)) return
        val have = folders()
        val missing = Builtins.library.filter { c -> have.none { it.id == c.key } }
        saveFolders(missing.map { Folder(it.key, it.title) } + have)
        val old = zikrs()
        val seeded = missing.flatMap { c ->
            c.items.mapIndexed { n, r -> Zikr("b_${c.key}_$n", r.arabic, "", r.target, c.key) }
        }
        saveZikrs(seeded + old)
        setHidden(hidden() + setOf("morning", "worry", "forgiveness"))   // they were not on Home before
        sp.edit().putBoolean(K_MIG, true).apply()
    }

    /** One time: the three fixed reminders become editable schedules (same on/off state). */
    private fun migrateSchedules() {
        if (sp.contains(K_SCHED)) return
        saveSchedules(
            listOf(
                Schedule("m", "Morning azkar", "Time for your morning azkar · tap to read", 6, 0, reminderOn("m", true), "morning"),
                Schedule("e", "Evening azkar", "Time for your evening azkar · tap to read", 17, 0, reminderOn("e", true), "evening"),
                Schedule("s", "Before sleep", "Time for your before sleep azkar · tap to read", 22, 0, reminderOn("s", false), "sleep")
            )
        )
    }

    // ---------- Azkar ----------
    fun zikrs(): List<Zikr> {
        val arr = JSONArray(sp.getString(K_ZIKRS, "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Zikr(
                o.getString("id"), o.getString("ar"), o.optString("note", ""), o.optInt("n", 1),
                if (o.isNull("f")) null else o.getString("f")
            )
        }
    }

    private fun saveZikrs(list: List<Zikr>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("ar", it.arabic).put("note", it.note).put("n", it.repeat)
                .put("f", it.folderId ?: JSONObject.NULL))
        }
        sp.edit().putString(K_ZIKRS, arr.toString()).apply()
    }

    fun zikr(id: String): Zikr? = zikrs().firstOrNull { it.id == id }
    fun zikrsIn(folderId: String): List<Zikr> = zikrs().filter { it.folderId == folderId }

    fun addZikr(arabic: String, note: String, repeat: Int, folderId: String?): Zikr {
        zikrs().firstOrNull { it.arabic == arabic && it.folderId == folderId }?.let {
            val merged = it.copy(note = note.ifBlank { it.note }, repeat = repeat)
            updateZikr(merged)
            return merged
        }
        val z = Zikr(UUID.randomUUID().toString(), arabic, note, repeat, folderId)
        saveZikrs(zikrs() + z)
        return z
    }

    fun updateZikr(z: Zikr) = saveZikrs(zikrs().map { if (it.id == z.id) z else it })
    fun deleteZikr(id: String) = saveZikrs(zikrs().filter { it.id != id })

    /** Takes a zikr out of its folder (it stays saved as "Anytime"). */
    fun unfile(id: String) = saveZikrs(zikrs().map { if (it.id == id) it.copy(folderId = null) else it })

    /** Reorders the zikrs of one folder; other azkar keep their relative position. */
    fun reorderFolder(folderId: String, orderedIds: List<String>) {
        val all = zikrs()
        val byId = all.associateBy { it.id }
        val queue = orderedIds.mapNotNull { byId[it] }.toMutableList()
        saveZikrs(all.map { if (it.folderId == folderId && queue.isNotEmpty()) queue.removeAt(0) else it })
    }

    // ---------- Folders ----------
    fun folders(): List<Folder> {
        val arr = JSONArray(sp.getString(K_FOLDERS, "[]"))
        return (0 until arr.length()).map { Folder(arr.getJSONObject(it).getString("id"), arr.getJSONObject(it).getString("name")) }
    }

    private fun saveFolders(list: List<Folder>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        sp.edit().putString(K_FOLDERS, arr.toString()).apply()
    }

    fun folder(id: String): Folder? = folders().firstOrNull { it.id == id }

    fun addFolder(name: String): Folder {
        val f = Folder(UUID.randomUUID().toString(), name)
        saveFolders(folders() + f)
        return f
    }

    fun renameFolder(id: String, name: String) = saveFolders(folders().map { if (it.id == id) it.copy(name = name) else it })

    /** Moves a folder up (-1) or down (+1) in the Library order. */
    fun moveFolder(id: String, d: Int) {
        val l = folders().toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + d
        if (i < 0 || j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t
        saveFolders(l)
    }

    fun folderIndex(id: String) = folders().indexOfFirst { it.id == id }

    /** Deletes the folder; its azkar stay saved as "Anytime". */
    fun deleteFolder(id: String) {
        saveFolders(folders().filter { it.id != id })
        saveZikrs(zikrs().map { if (it.folderId == id) it.copy(folderId = null) else it })
        setHomeOrder(homeOrder().filter { it != id })
        setHidden(hidden() - id)
    }

    /** Display name for a folder key. */
    fun folderName(key: String?): String {
        if (key == null) return "Anytime"
        return folder(key)?.name ?: Builtins.byKey(key)?.title ?: "Anytime"
    }

    fun isBuiltinFolder(key: String?) = Builtins.byKey(key) != null

    data class ImportSummary(val created: Int, val merged: Int, val added: Int, val skipped: Int)

    /** Creates missing folders, merges same-named ones (case-insensitive), skips exact duplicates. Never overwrites. */
    fun importFolders(plan: List<ImportJson.FolderIn>): ImportSummary {
        var created = 0; var merged = 0; var added = 0; var skipped = 0
        val allZ = zikrs().toMutableList()
        val fl = folders().toMutableList()
        plan.forEach { f ->
            val name = f.name.trim()
            val existing = fl.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
            val folder = existing ?: Folder(UUID.randomUUID().toString(), name).also { fl.add(it); created++ }
            if (existing != null) merged++
            f.items.forEach { item ->
                if (allZ.any { z -> z.folderId == folder.id && z.arabic == item.text }) skipped++
                else { allZ.add(Zikr(UUID.randomUUID().toString(), item.text, "", item.count, folder.id)); added++ }
            }
        }
        saveFolders(fl)
        saveZikrs(allZ)
        return ImportSummary(created, merged, added, skipped)
    }

    // ---------- Home customisation ----------
    val defaultOrder: List<String> get() = folders().map { it.id }

    /** Effective order: saved order (pruned) followed by anything new. */
    fun homeOrder(): List<String> {
        val valid = defaultOrder
        val raw: String? = sp.getString(K_ORDER, null)
        val saved: List<String> = if (raw == null) emptyList() else {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }
        return saved.filter { it in valid }.distinct().let { kept -> kept + valid.filter { it !in kept } }
    }

    fun setHomeOrder(order: List<String>) {
        sp.edit().putString(K_ORDER, JSONArray(order).toString()).apply()
    }

    fun hidden(): Set<String> {
        val arr = JSONArray(sp.getString(K_HIDDEN, "[]"))
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun setHidden(set: Set<String>) {
        sp.edit().putString(K_HIDDEN, JSONArray(set.toList()).toString()).apply()
    }

    fun placement(): Placement = Placement.from(sp.getString(K_PLACE, null))
    fun setPlacement(p: Placement) = sp.edit().putString(K_PLACE, p.key).apply()

    fun resetHome() {
        sp.edit().remove(K_ORDER).remove(K_HIDDEN).remove(K_PLACE).apply()
        setHidden(setOf("morning", "worry", "forgiveness").filter { folder(it) != null }.toSet())
    }

    // ---------- Reader / reminder settings ----------
    fun showTranslation() = sp.getBoolean(K_TRANS, true)
    fun setShowTranslation(v: Boolean) = sp.edit().putBoolean(K_TRANS, v).apply()

    /** 0 = small, 1 = medium (default), 2 = large. */
    fun arabicSize() = sp.getInt(K_SIZE, 1)
    fun setArabicSize(v: Int) = sp.edit().putInt(K_SIZE, v).apply()

    fun reminderOn(key: String, default: Boolean) = sp.getBoolean(K_REM + key, default)

    // ---------- Notification schedules ----------
    fun notificationsOn() = sp.getBoolean(K_NOTIF, true)
    fun setNotificationsOn(v: Boolean) = sp.edit().putBoolean(K_NOTIF, v).apply()

    fun schedules(): List<Schedule> {
        val arr = JSONArray(sp.getString(K_SCHED, "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Schedule(
                o.getString("id"), o.optString("t", ""), o.optString("m", ""), o.optInt("h", 8), o.optInt("mi", 0),
                o.optBoolean("on", true), if (o.isNull("open")) null else o.getString("open")
            )
        }
    }

    private fun saveSchedules(list: List<Schedule>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("t", it.title).put("m", it.message).put("h", it.hour)
                .put("mi", it.minute).put("on", it.enabled).put("open", it.open ?: JSONObject.NULL))
        }
        sp.edit().putString(K_SCHED, arr.toString()).apply()
    }

    fun schedule(id: String): Schedule? = schedules().firstOrNull { it.id == id }
    fun newScheduleId(): String = UUID.randomUUID().toString()

    fun upsertSchedule(s: Schedule) {
        val l = schedules()
        saveSchedules(if (l.any { it.id == s.id }) l.map { if (it.id == s.id) s else it } else l + s)
    }

    fun deleteSchedule(id: String) = saveSchedules(schedules().filter { it.id != id })

    // ---------- Reader content ----------
    fun readerItems(key: String): List<ReaderItem> {
        if (key == "anytime") return zikrs().filter { it.folderId == null }.map { ReaderItem(it.arabic, it.repeat) }
        if (key.startsWith("zikr:")) {
            return zikr(key.removePrefix("zikr:"))?.let { listOf(ReaderItem(it.arabic, it.repeat)) } ?: emptyList()
        }
        return zikrsIn(key).map { ReaderItem(it.arabic, it.repeat) }
    }

    fun readerKey(z: Zikr): String = z.folderId ?: "anytime"

    fun readerIndex(z: Zikr): Int {
        val f = z.folderId ?: return zikrs().filter { it.folderId == null }.indexOfFirst { it.id == z.id }.coerceAtLeast(0)
        return zikrsIn(f).indexOfFirst { it.id == z.id }.coerceAtLeast(0)
    }
}