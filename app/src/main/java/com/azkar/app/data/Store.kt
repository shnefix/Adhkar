package com.azkar.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persistent local storage (SharedPreferences + JSON), cached in memory. All writes go through the save* functions. */
object Store {
    private lateinit var sp: SharedPreferences
    private lateinit var appCtx: Context

    private const val K_ZIKRS = "zikrs"
    private const val K_FOLDERS = "folders"
    private const val K_DEFAULT_FOLDERS_IMPORTED = "default_folders_imported"
    private const val K_DEFAULTS_VER = "defaults_version"
    private const val DEFAULTS_VERSION = 1
    private const val K_ORDER = "home_order"
    private const val K_HIDDEN = "home_hidden"
    private const val K_PLACE = "home_place"
    private const val K_SIZE = "arabic_size"
    private const val K_THEME = "theme_mode"
    private const val K_HZ = "hidden_zikrs"
    private const val K_REM = "reminder_"
    private const val K_NOTIF = "notif_on"
    private const val K_SCHED = "schedules"
    private const val K_SP = "arabic_sp"
    private const val K_TAP = "tap_advance"
    private const val K_AWAKE = "keep_awake"
    private const val K_POS = "pos_"      // pos_<folderId> = last zikr id
    private const val K_POSC = "posc_"    // posc_<folderId> = its count
    private const val K_SCHEMA = "schema_version"
    private const val K_RECOVERED = "data_recovered"
    private const val SCHEMA_VERSION = 1

    // In-memory copies: the single source of truth for every screen.
    private var zCache: List<Zikr> = emptyList()
    private var fCache: List<Folder> = emptyList()
    private var sCache: List<Schedule> = emptyList()
    private var hzCache: Set<String> = emptySet()

    @Synchronized
    fun init(context: Context) {
        if (::sp.isInitialized) return
        appCtx = context.applicationContext
        sp = appCtx.getSharedPreferences("azkar", Context.MODE_PRIVATE)
        val recovered = try {
            zCache = parseZikrs(sp.getString(K_ZIKRS, "[]") ?: "[]")
            fCache = parseFolders(sp.getString(K_FOLDERS, "[]") ?: "[]")
            sCache = parseSchedules(sp.getString(K_SCHED, "[]") ?: "[]")
            hzCache = parseIds(sp.getString(K_HZ, "[]") ?: "[]")
            false
        } catch (e: Exception) {
            android.util.Log.e("Azkar", "Stored data was corrupt; starting with empty data", e)
            zCache = emptyList()
            fCache = emptyList()
            sCache = emptyList()
            hzCache = emptySet()
            true
        }
        if (recovered) sp.edit().clear().putInt(K_SCHEMA, SCHEMA_VERSION).putBoolean(K_RECOVERED, true).commit()
        else migrateSchema()
        syncDefaults()
        migrateSchedules()
        sanitizeIcons()
    }

    private fun migrateSchema() {
        val version = sp.getInt(K_SCHEMA, 0)
        if (version < SCHEMA_VERSION) sp.edit().putInt(K_SCHEMA, SCHEMA_VERSION).commit()
    }

    fun consumeRecoveryNotice(): Boolean {
        val wasRecovered = sp.getBoolean(K_RECOVERED, false)
        if (wasRecovered) sp.edit().remove(K_RECOVERED).apply()
        return wasRecovered
    }

    // ---------- Default azkar (assets/default_folders.json) ----------
    private fun defaults(): ImportJson.Parsed.Ok? = try {
        val raw = appCtx.assets.open("default_folders.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        ImportJson.parse(raw) as? ImportJson.Parsed.Ok
    } catch (e: Exception) {
        android.util.Log.w("Azkar", "default folders were not loaded", e)
        null
    }

    /**
     * Gives every user the defaults from the JSON, additively: folders match by id then name,
     * azkar are skipped if the same text already exists in that folder, nothing is overwritten or deleted.
     * Runs once per DEFAULTS_VERSION (bump it when you add defaults). Users of the previous build already
     * received everything, so for them it only records the version.
     */
    private fun syncDefaults() {
        if (sp.getInt(K_DEFAULTS_VER, 0) >= DEFAULTS_VERSION) return
        val alreadyHave = sp.getBoolean(K_DEFAULT_FOLDERS_IMPORTED, false)
        if (!alreadyHave && !mergeDefaults()) return          // asset unreadable: try again next start
        sp.edit().putInt(K_DEFAULTS_VER, DEFAULTS_VERSION).apply()
    }

    private fun mergeDefaults(): Boolean {
        val p = defaults() ?: return false
        val before = fCache.map { it.id }.toSet()
        val (ns, _) = ImportMerge.apply(ImportMerge.State(fCache, zCache, sCache), p) { UUID.randomUUID().toString() }
        saveFolders(ns.folders)
        saveZikrs(ns.zikrs)
        val created = ns.folders.map { it.id }.filter { it !in before }.toSet()
        val hide = p.folders.filter { it.home != true }.mapNotNull { it.id }.filter { it in created }
        if (hide.isNotEmpty()) setHidden(hidden() + hide)      // only brand-new default folders start hidden
        return true
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
            Folder(
                o.getString("id"), o.getString("name"),
                if (o.isNull("ic")) null else o.optString("ic", "").ifEmpty { null },
                o.optBoolean("rm", false)
            )
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
            if (f.remember) o.put("rm", true)
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
        val f = Folder(UUID.randomUUID().toString(), name, icon?.takeUnless { EmojiRules.isRestricted(it) })
        saveFolders(fCache + f)
        return f
    }

    fun renameFolder(id: String, name: String) = saveFolders(fCache.map { if (it.id == id) it.copy(name = name) else it })
    fun setFolderIcon(id: String, icon: String?) = saveFolders(
        fCache.map { if (it.id == id) it.copy(icon = icon?.takeUnless { e -> EmojiRules.isRestricted(e) }) else it }
    )

    /** One time per start: folders that already use a people/animal emoji fall back to the default icon. */
    private fun sanitizeIcons() {
        if (fCache.any { it.icon != null && EmojiRules.isRestricted(it.icon) }) {
            saveFolders(fCache.map { if (it.icon != null && EmojiRules.isRestricted(it.icon)) it.copy(icon = null) else it })
        }
    }

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
        clearPosition(id)
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
        val sp2 = p.settings?.arabicSp ?: p.settings?.arabicSize?.let { ReaderPrefs.spFromOldIndex(it) }
        sp2?.let { setArabicSp(it) }
        p.settings?.notificationsOn?.let { setNotificationsOn(it) }
        return sum
    }

    fun exportJson(): String = ExportJson.build(ImportMerge.State(fCache, zCache, sCache), arabicSize(), notificationsOn(), arabicSp())

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

    /** Resets order/placement; default folders that are not meant for Home (no "home": true) are hidden again. */
    fun resetHome() {
        sp.edit().remove(K_ORDER).remove(K_HIDDEN).remove(K_PLACE).apply()
        val hide = defaults()?.folders?.filter { it.home != true }?.mapNotNull { it.id }.orEmpty()
        setHidden(hide.filter { folder(it) != null }.toSet())
    }

    // ---------- Settings ----------
    /** 0 = small, 1 = medium (default), 2 = large. */
    fun arabicSize() = sp.getInt(K_SIZE, 1)
    fun setArabicSize(v: Int) = sp.edit().putInt(K_SIZE, v).apply()

    // ---------- Per-folder reading position ----------
    fun rememberPosition(folderId: String) = folder(folderId)?.remember == true

    fun setFolderRemember(id: String, on: Boolean) {
        saveFolders(fCache.map { if (it.id == id) it.copy(remember = on) else it })
        if (!on) clearPosition(id)          // old progress must never leak back in
    }

    fun savePosition(folderId: String, zikrId: String, count: Int) {
        sp.edit().putString(K_POS + folderId, zikrId).putInt(K_POSC + folderId, count).apply()
    }

    fun savedPosition(folderId: String): Pair<String, Int>? {
        val id = sp.getString(K_POS + folderId, null) ?: return null
        return id to sp.getInt(K_POSC + folderId, 0)
    }

    fun clearPosition(folderId: String) {
        sp.edit().remove(K_POS + folderId).remove(K_POSC + folderId).apply()
    }

    // ---------- Reader settings ----------
    /** Arabic size in sp. Existing Small/Medium/Large choices carry over as 20/26/32. */
    fun arabicSp(): Int = ReaderPrefs.clampSp(sp.getInt(K_SP, ReaderPrefs.spFromOldIndex(arabicSize())))
    fun setArabicSp(v: Int) = sp.edit().putInt(K_SP, ReaderPrefs.clampSp(v)).apply()
    fun tapAdvance() = sp.getBoolean(K_TAP, true)
    fun setTapAdvance(v: Boolean) = sp.edit().putBoolean(K_TAP, v).apply()
    fun keepScreenOn() = sp.getBoolean(K_AWAKE, true)
    fun setKeepScreenOn(v: Boolean) = sp.edit().putBoolean(K_AWAKE, v).apply()

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
            return zikr(key.removePrefix("zikr:"))?.let { listOf(ReaderItem(it.arabic, it.repeat, it.id)) } ?: emptyList()
        }
        val src = if (key == "anytime") zCache.filter { it.folderId == null } else zikrsIn(key)
        return Visibility.visible(src, hzCache).map { ReaderItem(it.arabic, it.repeat, it.id) }
    }

    fun readerKey(z: Zikr): String = z.folderId ?: "anytime"

    fun readerIndex(z: Zikr): Int {
        val src = if (z.folderId == null) zCache.filter { it.folderId == null } else zikrsIn(z.folderId)
        return Visibility.readerIndex(src, hzCache, z.id).coerceAtLeast(0)
    }
}