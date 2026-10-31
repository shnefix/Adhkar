package com.azkar.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persistent local storage (SharedPreferences + JSON), cached in memory. All writes go through the save* functions. */
object Store {
    private lateinit var sp: SharedPreferences

    private const val K_ZIKRS = "zikrs"
    private const val K_FOLDERS = "folders"
    private const val K_ORDER = "home_order"
    private const val K_HIDDEN = "home_hidden"
    private const val K_PLACE = "home_place"
    private const val K_SIZE = "arabic_size"
private const val K_THEME = "theme_mode"
private const val K_HZ = "hidden_zikrs"
    private const val K_REM = "reminder_"
    private const val K_NOTIF = "notif_on"
    private const val K_SCHED = "schedules"
    private const val K_MIG = "builtins_v2"

    // In-memory copies: the single source of truth for every screen.
    private var zCache: List<Zikr> = emptyList()
    private var fCache: List<Folder> = emptyList()
    private var sCache: List<Schedule> = emptyList()
private var hzCache: Set<String> = emptySet()

    @Synchronized
    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("azkar", Context.MODE_PRIVATE)
        zCache = parseZikrs(sp.getString(K_ZIKRS, "[]") ?: "[]")
        fCache = parseFolders(sp.getString(K_FOLDERS, "[]") ?: "[]")
        sCache = parseSchedules(sp.getString(K_SCHED, "[]") ?: "[]")
hzCache = parseIds(sp.getString(K_HZ, "[]") ?: "[]")
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
        val have = fCache
        val missing = Builtins.library.filter { c -> have.none { it.id == c.key } }
        saveFolders(missing.map { Folder(it.key, it.title) } + have)
        val seeded = missing.flatMap { c ->
            c.items.mapIndexed { n, r -> Zikr("b_${c.key}_$n", r.arabic, "", r.target, c.key) }
        }
        saveZikrs(seeded + zCache)
        setHidden(hidden() + setOf("morning", "worry", "forgiveness"))
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
    // ---------- Parsing / saving (the only code that touches the caches) ----------
    private fun parseZikrs(raw: String): List<Zikr> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Zikr(o.getString("id"), o.getString("ar"), o.optString("note", ""), o.optInt("n", 1), if (o.isNull("f")) null else o.getString("f"))
        }
    }

    private fun parseFolders(raw: String): List<Folder> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Folder(o.getString("id"), o.getString("name"), if (o.isNull("ic")) null else o.optString("ic", "").ifEmpty { null })
        }
    }

    private fun parseSchedules(raw: String): List<Schedule> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Schedule(
                o.getString("id"), o.optString("t", ""), o.optString("m", ""), o.optInt("h", 8), o.optInt("mi", 0),
                o.optBoolean("on", true), if (o.isNull("open")) null else o.getString("open")
            )
        }
    }

    @Synchronized
    private fun saveZikrs(list: List<Zikr>) {
        zCache = list.toList()
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("ar", it.arabic).put("note", it.note).put("n", it.repeat)
                .put("f", it.folderId ?: JSONObject.NULL))
        }
        sp.edit().putString(K_ZIKRS, arr.toString()).apply()
    }

    @Synchronized
    private fun saveFolders(list: List<Folder>) {
        fCache = list.toList()
        val arr = JSONArray()
        list.forEach { f ->
            val o = JSONObject().put("id", f.id).put("name", f.name)
            f.icon?.let { o.put("ic", it) }
            arr.put(o)
        }
        sp.edit().putString(K_FOLDERS, arr.toString()).apply()
    }

    @Synchronized
    private fun saveSchedules(list: List<Schedule>) {
        sCache = list.toList()
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("t", it.title).put("m", it.message).put("h", it.hour)
                .put("mi", it.minute).put("on", it.enabled).put("open", it.open ?: JSONObject.NULL))
        }
        sp.edit().putString(K_SCHED, arr.toString()).apply()
    }

    // ---------- Azkar ----------
    fun zikrs(): List<Zikr> = zCache
    fun zikr(id: String): Zikr? = zCache.firstOrNull { it.id == id }
    fun zikrsIn(folderId: String): List<Zikr> = zCache.filter { it.folderId == folderId }

    /** Number of azkar per folder id (null key = Anytime), computed in one pass. */
    fun zikrCounts(): Map<String?, Int> = zCache.groupingBy { it.folderId }.eachCount()

    fun addZikr(arabic: String, note: String, repeat: Int, folderId: String?): Zikr {
        zCache.firstOrNull { it.arabic == arabic && it.folderId == folderId }?.let {
            val merged = it.copy(note = note.ifBlank { it.note }, repeat = repeat)
            updateZikr(merged)
            return merged
        }
        val z = Zikr(UUID.randomUUID().toString(), arabic, note, repeat, folderId)
        saveZikrs(zCache + z)
        return z
    }

    fun updateZikr(z: Zikr) = saveZikrs(zCache.map { if (it.id == z.id) z else it })
    fun deleteZikr(id: String) {
        saveZikrs(zCache.filter { it.id != id })
        if (id in hzCache) setZikrsHidden(listOf(id), false)
    }

    /** Takes a zikr out of its folder (it stays saved as "Anytime"). */
    fun unfile(id: String) = saveZikrs(zCache.map { if (it.id == id) it.copy(folderId = null) else it })

    /** Reorders the zikrs of one folder; other azkar keep their relative position. */
    fun reorderFolder(folderId: String, orderedIds: List<String>) {
        val all = zCache
        val byId = all.associateBy { it.id }
        val queue = orderedIds.mapNotNull { byId[it] }.toMutableList()
        saveZikrs(all.map { if (it.folderId == folderId && queue.isNotEmpty()) queue.removeAt(0) else it })
    }
    // ---------- Folders ----------
    fun folders(): List<Folder> = fCache
    fun folder(id: String): Folder? = fCache.firstOrNull { it.id == id }

    fun addFolder(name: String, icon: String? = null): Folder {
        val f = Folder(UUID.randomUUID().toString(), name, icon)
        saveFolders(fCache + f)
        return f
    }

    fun renameFolder(id: String, name: String) = saveFolders(fCache.map { if (it.id == id) it.copy(name = name) else it })
    fun setFolderIcon(id: String, icon: String?) = saveFolders(fCache.map { if (it.id == id) it.copy(icon = icon) else it })

    /** Moves a folder up (-1) or down (+1) in the Library order. */
    fun moveFolder(id: String, d: Int) {
        val l = fCache.toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + d
        if (i < 0 || j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t
        saveFolders(l)
    }

    fun folderIndex(id: String) = fCache.indexOfFirst { it.id == id }

    /** Deletes the folder; its azkar stay saved as "Anytime" and reminders pointing at it just open the app. */
    fun deleteFolder(id: String) {
        saveFolders(fCache.filter { it.id != id })
        saveZikrs(zCache.map { if (it.folderId == id) it.copy(folderId = null) else it })
        if (sCache.any { it.open == id }) saveSchedules(sCache.map { if (it.open == id) it.copy(open = null) else it })
        setHomeOrder(homeOrder().filter { it != id })
        setHidden(hidden() - id)
    }

    fun folderName(key: String?): String {
        if (key == null) return "Anytime"
        return folder(key)?.name ?: Builtins.byKey(key)?.title ?: "Anytime"
    }

    fun isBuiltinFolder(key: String?) = Builtins.byKey(key) != null

    // ---------- Import / export ----------
    fun importAll(p: ImportJson.Parsed.Ok): ImportMerge.Summary {
        val (ns, sum) = ImportMerge.apply(ImportMerge.State(fCache, zCache, sCache), p) { UUID.randomUUID().toString() }
        saveFolders(ns.folders); saveZikrs(ns.zikrs); saveSchedules(ns.schedules)
        p.settings?.arabicSize?.let { setArabicSize(it) }
        p.settings?.notificationsOn?.let { setNotificationsOn(it) }
        return sum
    }

    fun exportJson(): String = ExportJson.build(ImportMerge.State(fCache, zCache, sCache), arabicSize(), notificationsOn())

    // ---------- Home customisation ----------
    val defaultOrder: List<String> get() = fCache.map { it.id }

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

    // ---------- Settings ----------
    /** 0 = small, 1 = medium (default), 2 = large. */
    fun arabicSize() = sp.getInt(K_SIZE, 1)
    fun setArabicSize(v: Int) = sp.edit().putInt(K_SIZE, v).apply()

    /** 0 = current teal theme (default), 1 = OLED black. */
    fun theme() = sp.getInt(K_THEME, 0)
    fun setTheme(v: Int) = sp.edit().putInt(K_THEME, v).apply()

    private fun parseIds(raw: String): Set<String> {
        val a = JSONArray(raw)
        return (0 until a.length()).map { a.getString(it) }.toSet()
    }

    fun hiddenZikrs(): Set<String> = hzCache

    @Synchronized
    fun setZikrsHidden(ids: kotlin.collections.Collection<String>, hide: Boolean) {
        hzCache = Visibility.apply(hzCache, ids, hide)
        sp.edit().putString(K_HZ, JSONArray(hzCache.toList()).toString()).apply()
    }

    fun reminderOn(key: String, default: Boolean) = sp.getBoolean(K_REM + key, default)

    // ---------- Notification schedules ----------
    fun notificationsOn() = sp.getBoolean(K_NOTIF, true)
    fun setNotificationsOn(v: Boolean) = sp.edit().putBoolean(K_NOTIF, v).apply()

    fun schedules(): List<Schedule> = sCache
    fun schedule(id: String): Schedule? = sCache.firstOrNull { it.id == id }
    fun newScheduleId(): String = UUID.randomUUID().toString()

    fun upsertSchedule(s: Schedule) {
        saveSchedules(if (sCache.any { it.id == s.id }) sCache.map { if (it.id == s.id) s else it } else sCache + s)
    }

    fun deleteSchedule(id: String) = saveSchedules(sCache.filter { it.id != id })

    // ---------- Reader content ----------
    fun readerItems(key: String): List<ReaderItem> {
        if (key.startsWith("zikr:")) {
            return zikr(key.removePrefix("zikr:"))?.let { listOf(ReaderItem(it.arabic, it.repeat)) } ?: emptyList()
        }
        val src = if (key == "anytime") zCache.filter { it.folderId == null } else zikrsIn(key)
        return Visibility.visible(src, hzCache).map { ReaderItem(it.arabic, it.repeat) }
    }

    fun readerKey(z: Zikr): String = z.folderId ?: "anytime"

    fun readerIndex(z: Zikr): Int {
        val src = if (z.folderId == null) zCache.filter { it.folderId == null } else zikrsIn(z.folderId)
        return Visibility.readerIndex(src, hzCache, z.id).coerceAtLeast(0)
    }
}

