package com.azkar.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import com.azkar.app.widgets.WidgetUpdater
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Compatibility facade over Room content and lightweight SharedPreferences settings. */
object Store {
    private lateinit var sp: SharedPreferences
    private lateinit var appCtx: Context

    private const val K_ZIKRS = "zikrs"
    private const val K_FOLDERS = "folders"
    private const val K_DEFAULT_FOLDERS_IMPORTED = "default_folders_imported"
    private const val K_DEFAULTS_VER = "defaults_version"
    private const val DEFAULTS_VERSION = 3
    private const val K_ORDER = "home_order"
    private const val K_HIDDEN = "home_hidden"
    private const val K_PLACE = "home_place"
    private const val K_HZ = "hidden_zikrs"
    private const val K_SCHED = "schedules"
    private const val K_SCHEDULES_INITIALIZED = "schedules_initialized"
    private const val K_SCHEMA = "schema_version"
    private const val K_RECOVERED = "data_recovered"
    private const val K_ROOM_MIGRATION_VERIFIED = "room_migration_verified"
    private const val K_ROOM_MIGRATION_BACKUP = "room_migration_backup"
    private const val SCHEMA_VERSION = 1

    // Compatibility snapshots; Room is the durable source of truth for content.
    private var zCache: List<Zikr> = emptyList()
    private var fCache: List<Folder> = emptyList()
    private var sCache: List<Schedule> = emptyList()
    private var defaultFolderIds: Set<String> = emptySet()
    private val hzState = MutableStateFlow<Set<String>>(emptySet())
    private val hzCache: Set<String> get() = hzState.value
    private var cacheGeneration = 0L
    private lateinit var repository: AzkarRepository
    private val widgetRefreshPending = AtomicBoolean(false)
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initResult: CompletableDeferred<Result<Unit>>? = null
    private val initStarted = AtomicBoolean(false)

    @Synchronized
    fun init(context: Context) {
        if (::sp.isInitialized) return
        appCtx = context.applicationContext
        sp = appCtx.getSharedPreferences("azkar", Context.MODE_PRIVATE)
        AppPrefs.attach(sp)
        repository = AzkarRepository(AzkarDatabaseProvider.get(appCtx).azkarDao())
        val result = CompletableDeferred<Result<Unit>>()
        initResult = result
        if (initStarted.compareAndSet(false, true)) {
            initScope.launch {
                try {
                    initialize()
                    result.complete(Result.success(Unit))
                } catch (e: Exception) {
                    android.util.Log.e("Azkar", "Database initialization failed", e)
                    result.complete(Result.failure(e))
                }
            }
        }
    }

    suspend fun awaitReady() {
        check(::sp.isInitialized) { "Store.init(context) must be called first" }
        initResult?.await()?.getOrThrow()
    }

    private suspend fun initialize() {
        val loaded = repository.loadContent()
        zCache = loaded.zikrs
        fCache = loaded.folders
        sCache = loaded.schedules
        val legacy = try {
            LegacyContent(
                LegacyJson.parseZikrs(sp.getString(K_ZIKRS, "[]") ?: "[]"),
                LegacyJson.parseFolders(sp.getString(K_FOLDERS, "[]") ?: "[]"),
                LegacyJson.parseSchedules(sp.getString(K_SCHED, "[]") ?: "[]")
            )
        } catch (e: Exception) {
            android.util.Log.e("Azkar", "Stored content was corrupt; keeping it for recovery", e)
            null
        }
        val roomEmpty = repository.folderCount() == 0 && repository.zikrCount() == 0 && repository.scheduleCount() == 0
        val hasLegacy = sp.contains(K_ZIKRS) || sp.contains(K_FOLDERS) || sp.contains(K_SCHED)
        val validLegacy = legacy?.takeIf { it.isValid() }
        val migrated = if (roomEmpty && hasLegacy && validLegacy != null) {
            migrateLegacy(validLegacy)
        } else {
            false
        }
        if (!roomEmpty && validLegacy != null && !sp.getBoolean(K_ROOM_MIGRATION_VERIFIED, false)) {
            verifyRoomMigration(validLegacy)
        }
        if (legacy != null && sp.contains(K_SCHED)) {
            sp.edit().putBoolean(K_SCHEDULES_INITIALIZED, true).apply()
        }
        val recovered = legacy == null || validLegacy == null
        if (migrated || !roomEmpty) {
            zCache = repository.zikrs()
            fCache = repository.folders()
            sCache = repository.schedules()
        } else if (validLegacy != null) {
            zCache = validLegacy.zikrs
            fCache = validLegacy.folders
            sCache = validLegacy.schedules
        } else {
            zCache = emptyList()
            fCache = emptyList()
            sCache = emptyList()
        }
        hzState.value = try { LegacyJson.parseIds(sp.getString(K_HZ, "[]") ?: "[]") } catch (_: Exception) { emptySet() }
        if (recovered) sp.edit().putInt(K_SCHEMA, SCHEMA_VERSION).putBoolean(K_RECOVERED, true).commit()
        else migrateSchema()
        syncDefaults()
        defaultFolderIds = defaults()?.folders?.mapNotNull { it.id }?.toSet().orEmpty()
        migrateSchedules()
        sanitizeIcons()
        repository.awaitPendingWrites()
    }

    private suspend fun migrateLegacy(legacy: LegacyContent): Boolean {
        val backup = JSONObject()
            .put("folders", sp.getString(K_FOLDERS, "[]"))
            .put("zikrs", sp.getString(K_ZIKRS, "[]"))
            .put("schedules", sp.getString(K_SCHED, "[]"))
        sp.edit().putString(K_ROOM_MIGRATION_BACKUP, backup.toString()).commit()
        val verified = RoomMigration.importIfEmpty(
            repository,
            RoomMigration.LegacyData(legacy.folders, legacy.zikrs, legacy.schedules)
        )
        if (verified) sp.edit().putBoolean(K_ROOM_MIGRATION_VERIFIED, true).commit()
        return verified
    }

    private fun verifyRoomMigration(legacy: LegacyContent) {
        if (repository.folders() == legacy.folders &&
            repository.zikrs() == legacy.zikrs &&
            repository.schedules() == legacy.schedules
        ) {
            sp.edit().putBoolean(K_ROOM_MIGRATION_VERIFIED, true).commit()
        }
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
        saveContent(ns.folders, ns.zikrs, ns.schedules)
        val created = ns.folders.map { it.id }.filter { it !in before }.toSet()
        val hide = p.folders.filter { it.home != true }.mapNotNull { it.id }.filter { it in created }
        if (hide.isNotEmpty()) setHidden(hidden() + hide)      // only brand-new default folders start hidden
        return true
    }

    /** One time: the three fixed reminders become editable schedules (same on/off state). */
    private fun migrateSchedules() {
        if (sp.getBoolean(K_SCHEDULES_INITIALIZED, false)) return
        saveSchedules(
            listOf(
                Schedule("m", "Morning azkar", "Time for your morning azkar · tap to read", 6, 0, AppPrefs.reminderOn("m", true), "morning"),
                Schedule("e", "Evening azkar", "Time for your evening azkar · tap to read", 17, 0, AppPrefs.reminderOn("e", true), "evening"),
                Schedule("s", "Before sleep", "Time for your before sleep azkar · tap to read", 22, 0, AppPrefs.reminderOn("s", false), "sleep")
            )
        )
    }

    // ---------- Saving (the only code that touches the caches) ----------
    @Synchronized
    private fun saveZikrs(list: List<Zikr>) {
        saveContent(fCache, list, sCache)
    }

    @Synchronized
    private fun saveFolders(list: List<Folder>) {
        saveContent(list, zCache, sCache)
    }

    @Synchronized
    private fun saveSchedules(list: List<Schedule>) {
        saveContent(fCache, zCache, list)
        sp.edit().putBoolean(K_SCHEDULES_INITIALIZED, true).apply()
    }

    @Synchronized
    private fun saveContent(folders: List<Folder>, zikrs: List<Zikr>, schedules: List<Schedule>) {
        val next = AzkarContent(folders.toList(), zikrs.toList(), schedules.toList())
        val generation = ++cacheGeneration
        val previous = AzkarContent(fCache, zCache, sCache)
        fCache = next.folders
        zCache = next.zikrs
        sCache = next.schedules
        repository.replaceAll(next) { result ->
            if (result.isSuccess) {
                notifyWidgets()
            } else {
                reconcileFailedWrite(generation, previous, result.exceptionOrNull())
            }
        }
    }

    @Synchronized
    private fun reconcileFailedWrite(generation: Long, previous: AzkarContent, error: Throwable?) {
        if (generation != cacheGeneration) {
            android.util.Log.e("Azkar", "Discarded failed stale content write", error)
            return
        }
        fCache = previous.folders
        zCache = previous.zikrs
        sCache = previous.schedules
        android.util.Log.e("Azkar", "Content write failed; reverted in-memory snapshot", error)
        notifyWidgets()
    }

    private fun notifyWidgets() {
        if (::appCtx.isInitialized && widgetRefreshPending.compareAndSet(false, true)) {
            appCtx.mainExecutor.execute {
                try {
                    WidgetUpdater.updateAll(appCtx)
                } finally {
                    widgetRefreshPending.set(false)
                }
            }
        }
    }

    // ---------- Azkar ----------
    fun zikrs(): List<Zikr> = zCache
    fun zikr(id: String): Zikr? = zCache.firstOrNull { it.id == id }
    fun zikrsIn(folderId: String): List<Zikr> = zCache.filter { it.folderId == folderId }

    /** Number of azkar per folder id (null key = Anytime), computed in one pass. */
    fun zikrCounts(): Map<String?, Int> = zCache.groupingBy { it.folderId }.eachCount()

    @Synchronized
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

    @Synchronized
    fun updateZikr(z: Zikr) = saveZikrs(zCache.map { if (it.id == z.id) z else it })
    @Synchronized
    fun deleteZikr(id: String) {
        saveZikrs(zCache.filter { it.id != id })
        if (id in hzCache) setZikrsHidden(listOf(id), false)
    }

    /** Takes a zikr out of its folder (it stays saved as "Anytime"). */
    @Synchronized
    fun unfile(id: String) = saveZikrs(zCache.map { if (it.id == id) it.copy(folderId = null) else it })

    /** Reorders the zikrs of one folder; other azkar keep their relative position. */
    @Synchronized
    fun reorderFolder(folderId: String, orderedIds: List<String>) {
        val all = zCache
        val byId = all.associateBy { it.id }
        val queue = orderedIds.mapNotNull { byId[it] }.toMutableList()
        saveZikrs(all.map { if (it.folderId == folderId && queue.isNotEmpty()) queue.removeAt(0) else it })
    }

    // ---------- Folders ----------
    fun folders(): List<Folder> = fCache
    fun folder(id: String): Folder? = fCache.firstOrNull { it.id == id }

    @Synchronized
    fun addFolder(name: String, icon: String? = null): Folder {
        val f = Folder(UUID.randomUUID().toString(), name, icon?.takeUnless { EmojiRules.isRestricted(it) })
        saveFolders(fCache + f)
        return f
    }

    @Synchronized
    fun renameFolder(id: String, name: String) = saveFolders(fCache.map { if (it.id == id) it.copy(name = name) else it })
    @Synchronized
    fun setFolderIcon(id: String, icon: String?) = saveFolders(
        fCache.map { if (it.id == id) it.copy(icon = icon?.takeUnless { e -> EmojiRules.isRestricted(e) }) else it }
    )

    /** One time per start: folders that already use a people/animal emoji fall back to the default icon. */
    private fun sanitizeIcons() {
        if (fCache.any { it.icon != null && EmojiRules.isRestricted(it.icon) }) {
            saveFolders(fCache.map { if (it.icon != null && EmojiRules.isRestricted(it.icon)) it.copy(icon = null) else it })
        }
    }

    @Synchronized
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
    @Synchronized
    fun deleteFolder(id: String) {
        val nextFolders = fCache.filter { it.id != id }
        val nextZikrs = zCache.map { if (it.folderId == id) it.copy(folderId = null) else it }
        val nextSchedules = sCache.map { if (it.open == id) it.copy(open = null) else it }
        saveContent(nextFolders, nextZikrs, nextSchedules)
        setHomeOrder(homeOrder().filter { it != id })
        setHidden(hidden() - id)
        AppPrefs.clearPosition(id)
    }

    fun folderName(key: String?): String {
        if (key == null) return "Anytime"
        return folder(key)?.name ?: "Anytime"
    }

    fun isDefaultFolder(key: String?) = key != null && key in defaultFolderIds

    // ---------- Import / export ----------
    suspend fun importAllAsync(p: ImportJson.Parsed.Ok): ImportMerge.Summary {
        val (base, baseGeneration) = synchronized(this) {
            Pair(ImportMerge.State(fCache, zCache, sCache), cacheGeneration)
        }
        repository.awaitPendingWrites()
        val (ns, sum) = ImportMerge.apply(base, p) { UUID.randomUUID().toString() }
        val next = AzkarContent(ns.folders, ns.zikrs, ns.schedules)
        repository.replaceAllAsync(next)
        val published = synchronized(this) {
            if (cacheGeneration != baseGeneration) {
                false
            } else {
                fCache = next.folders
                zCache = next.zikrs
                sCache = next.schedules
                cacheGeneration++
                true
            }
        }
        if (published) notifyWidgets()
        sp.edit().putBoolean(K_SCHEDULES_INITIALIZED, true).apply()
        val sp2 = p.settings?.arabicSp ?: p.settings?.arabicSize?.let { ReaderPrefs.spFromOldIndex(it) }
        sp2?.let { AppPrefs.setArabicSp(it) }
        p.settings?.notificationsOn?.let { AppPrefs.setNotificationsOn(it) }
        return sum
    }

    /** Restores the retained pre-Room content only when Room is still completely empty. */
    suspend fun recoverMigrationBackup(): Boolean {
        if (repository.folderCount() != 0 || repository.zikrCount() != 0 || repository.scheduleCount() != 0) return false
        val raw = sp.getString(K_ROOM_MIGRATION_BACKUP, null) ?: return false
        val backup = try { JSONObject(raw) } catch (_: Exception) { return false }
        val legacy = try {
            LegacyContent(
                LegacyJson.parseZikrs(backup.optString("zikrs", "[]")),
                LegacyJson.parseFolders(backup.optString("folders", "[]")),
                LegacyJson.parseSchedules(backup.optString("schedules", "[]"))
            )
        } catch (_: Exception) {
            return false
        }
        if (!legacy.isValid()) return false
        val restored = RoomMigration.importIfEmpty(
            repository,
            RoomMigration.LegacyData(legacy.folders, legacy.zikrs, legacy.schedules)
        )
        if (restored) {
            fCache = legacy.folders
            zCache = legacy.zikrs
            sCache = legacy.schedules
            sp.edit()
                .putBoolean(K_ROOM_MIGRATION_VERIFIED, true)
                .putBoolean(K_SCHEDULES_INITIALIZED, true)
                .commit()
        }
        return restored
    }

    suspend fun exportJsonAsync(): String = withContext(Dispatchers.Default) {
        ExportJson.build(ImportMerge.State(fCache, zCache, sCache), AppPrefs.arabicSize(), AppPrefs.notificationsOn(), AppPrefs.arabicSp())
    }

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

    // ---------- Per-folder reading position ----------
    fun rememberPosition(folderId: String) = folder(folderId)?.remember == true

    @Synchronized
    fun setFolderRemember(id: String, on: Boolean) {
        saveFolders(fCache.map { if (it.id == id) it.copy(remember = on) else it })
        if (!on) AppPrefs.clearPosition(id)          // old progress must never leak back in
    }

    fun hiddenZikrs(): Set<String> = hzCache

    /** Hidden zikr ids as they change; SharedPreferences-backed, so not part of the Room content flow. */
    val hiddenZikrsFlow: StateFlow<Set<String>> = hzState.asStateFlow()

    @Synchronized
    fun setZikrsHidden(ids: kotlin.collections.Collection<String>, hide: Boolean) {
        hzState.value = Visibility.apply(hzCache, ids, hide)
        sp.edit().putString(K_HZ, JSONArray(hzCache.toList()).toString()).apply()
    }


    // ---------- Notification schedules ----------
    fun schedules(): List<Schedule> = sCache
    fun schedule(id: String): Schedule? = sCache.firstOrNull { it.id == id }
    fun newScheduleId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun upsertSchedule(s: Schedule) {
        saveSchedules(if (sCache.any { it.id == s.id }) sCache.map { if (it.id == s.id) s else it } else sCache + s)
    }

    @Synchronized
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