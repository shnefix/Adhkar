package com.azkar.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import com.azkar.app.R
import com.azkar.app.widgets.WidgetUpdater
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

/** Compatibility facade over Room content and lightweight SharedPreferences settings. */
object Store {
    /** Reader/widget key for azkar saved without a folder. */
    const val ANYTIME = "anytime"

    private lateinit var sp: SharedPreferences
    private lateinit var appCtx: Context

    private const val K_ZIKRS = "zikrs"
    private const val K_FOLDERS = "folders"
    private const val K_DEFAULT_FOLDERS_IMPORTED = "default_folders_imported"
    private const val K_DEFAULTS_VER = "defaults_version"
    private const val DEFAULTS_VERSION = 13
    private const val WAKE_FOLDER_NAME = "أذكار الاستيقاظ"
    /** Earlier names of the wake-up folder: an encoding bug saved "?????", and versions 4-6 misspelled it. */
    private val WAKE_OLD_NAMES = setOf("أذكار الإستيقاظ من النوم", "????? ????????", "أذكار الإستقاظ")
    /** Before version 7 the before-sleep reminder pointed at "sleep", which is no folder id. */
    private const val OLD_SLEEP_OPEN = "sleep"
    private const val K_SCHED = "schedules"
    private const val K_SCHEDULES_INITIALIZED = "schedules_initialized"
    private const val K_SCHEMA = "schema_version"
    private const val K_RECOVERED = "data_recovered"
    private const val K_ROOM_MIGRATION_VERIFIED = "room_migration_verified"
    private const val K_ROOM_MIGRATION_BACKUP = "room_migration_backup"
    private const val SCHEMA_VERSION = 1

    // Compatibility snapshots; Room is the durable source of truth for content.
    // Written under the Store lock (often on IO) but read without it from the main thread: @Volatile so readers see the latest list.
    @Volatile private var zCache: List<Zikr> = emptyList()
    @Volatile private var fCache: List<Folder> = emptyList()
    @Volatile private var sCache: List<Schedule> = emptyList()
    private var defaultFolderIds: Set<String> = emptySet()
    /** Home layout and hidden azkar (SharedPreferences); the functions below keep their old names for callers. */
    private val home = HomePrefs()
    private val hzCache: Set<String> get() = home.hiddenZikrs.value
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
        home.attach(sp)
        Tracking.attach(appCtx.getSharedPreferences("azkar_tracking", Context.MODE_PRIVATE))
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
        home.loadHiddenZikrs()
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
     * Runs once per DEFAULTS_VERSION (bump it when you add defaults). A full merge only happens on a fresh
     * install; upgrades receive just the folders added since, so default folders a user deleted stay deleted.
     */
    private fun syncDefaults() {
        val from = sp.getInt(K_DEFAULTS_VER, 0)
        if (from >= DEFAULTS_VERSION) return
        val p = defaults() ?: return                          // asset unreadable: try again next start
        val fresh = from == 0 && !sp.getBoolean(K_DEFAULT_FOLDERS_IMPORTED, false)
        if (fresh) mergeDefaults(p)
        else if (from < 4) mergeDefaults(p.copy(folders = p.folders.filter { it.id == DefaultFolders.HOME }))
        if (!fresh && from < 6) mergeDefaults(p.copy(folders = p.folders.filter { it.id == DefaultFolders.ZAD }))
        if (!fresh && from < 8) mergeDefaults(p.copy(folders = p.folders.filter { it.id in DefaultFolders.ADDED_IN_V8 }))
        if (from < 4) applyHomeDefaults()
        if (from in 1 until 5) applySessionDefaults()
        if (from < 7) fixWakeNameAndSleepReminder()
        if (from in 1 until 9) renameDefaultFolders()
        if (!fresh && from < 10) fillDefaultSources(p)
        if (!fresh && from < 11) clearPlaceholderSources()
        if (!fresh && from < 12) addShortAzkarAndPraised(p)
        if (!fresh && from < 13) addBookDuas(p)
        sp.edit().putInt(K_DEFAULTS_VER, DEFAULTS_VERSION).apply()
    }

    /**
     * Version 4: fixes the wake-up folder's name (it was saved as "?????" by an encoding bug) and puts
     * the [DefaultFolders.HOME_ORDER] folders first on Home. Folders the user renamed or deleted are left alone.
     */
    private fun applyHomeDefaults() {
        folder(DefaultFolders.WAKE)?.takeIf { it.name in WAKE_OLD_NAMES }?.let { renameFolder(it.id, WAKE_FOLDER_NAME) }
        val present = DefaultFolders.HOME_ORDER.filter { folder(it) != null }
        setHidden(hidden() - present.toSet())
        setHomeOrder(present + homeOrder().filter { it !in present })
    }

    /**
     * Version 5 (upgrades only; fresh installs get the same from the JSON and [migrateSchedules]):
     * morning / evening remember their position, and their reminders open those folders.
     */
    private fun applySessionDefaults() {
        listOf(DefaultFolders.MORNING, DefaultFolders.EVENING).filter { folder(it) != null }.forEach { setFolderRemember(it, true) }
        mapOf("m" to DefaultFolders.MORNING, "e" to DefaultFolders.EVENING).forEach { (sid, fid) ->
            val s = schedule(sid) ?: return@forEach
            if (folder(fid) != null && (s.open == null || folder(s.open) == null)) upsertSchedule(s.copy(open = fid))
        }
    }

    /**
     * Version 7: spells the wake-up folder's name correctly (a user's own name is left alone), and the before-sleep
     * reminder opens the sleep folder instead of the non-existent "sleep".
     */
    private fun fixWakeNameAndSleepReminder() {
        folder(DefaultFolders.WAKE)?.takeIf { it.name in WAKE_OLD_NAMES }?.let { renameFolder(it.id, WAKE_FOLDER_NAME) }
        sCache.filter { it.open == OLD_SLEEP_OPEN }.forEach {
            upsertSchedule(it.copy(open = DefaultFolders.SLEEP.takeIf { id -> folder(id) != null }))
        }
    }

    /** Version 9: clearer names for some default folders (id to old name to new name). */
    private val RENAMED_IN_V9 = mapOf(
        "d_1" to ("ٱسْتِغْفَار" to "الاستغفار"),
        "d_7" to ("بعد الصلاة" to "أذكار بعد الصلاة"),
        "d_17" to ("أذكار الصلاة" to "أذكار داخل الصلاة"),
        "d_6" to ("دُعَاءُ النَّبِيِّ" to "من أدعية النبي ﷺ"),
        "d_4" to ("فِي الْخِفَّةِ" to "أذكار خفيفة"),
        "d_5" to ("الْعِلْمُ" to "أدعية طلب العلم"),
        "d_8" to ("طلب الإجابة" to "مواطن إجابة الدعاء"),
        "d_15" to ("الواجهة" to "أذكار قصيرة"),
        "d_16" to ("زَادُ المَعَادِ" to "مختارات")
    )

    /** Renames those folders on upgrade; a folder the user renamed or deleted is left alone. */
    private fun renameDefaultFolders() {
        RENAMED_IN_V9.forEach { (id, names) ->
            folder(id)?.takeIf { it.name == names.first }?.let { renameFolder(it.id, names.second) }
        }
    }

    /**
     * Version 10: every default zikr has its source (the ayah or hadith text, narrator and grade). Fills it in for
     * default azkar whose text is unchanged and that have no source yet; azkar from the folders added in version 8
     * also get it over their old reference-only source. A source the user wrote is never replaced.
     */
    private fun fillDefaultSources(p: ImportJson.Parsed.Ok) {
        val bySource = p.folders.filter { it.id != null }.associate { f -> f.id to f.items.associate { it.text to it.source } }
        var changed = false
        val next = zCache.map { z ->
            val src = bySource[z.folderId]?.get(z.arabic) ?: return@map z
            val replaceable = z.source.isEmpty ||
                (z.folderId in DefaultFolders.ADDED_IN_V8 && z.source.detail.isBlank() && z.source.narrator.isBlank() && z.source.grade.isBlank())
            if (replaceable && z.source != src) { changed = true; z.copy(source = src) } else z
        }
        if (changed) saveZikrs(next)
    }

    /**
     * Version 11: azkar with no source in the Quran or Sunnah have no source at all. Earlier builds gave them a
     * "no source found" text as their source, which still showed the source button; those are cleared.
     */
    private fun clearPlaceholderSources() {
        val placeholders = listOf("دعاء عام", "لم يُعثر")
        if (zCache.none { z -> placeholders.any { z.source.reference.startsWith(it) } }) return
        saveZikrs(zCache.map { z -> if (placeholders.any { z.source.reference.startsWith(it) }) z.copy(source = ZikrSource()) else z })
    }

    /**
     * Version 12: the first Home folder becomes "short azkar" (renamed by [renameDefaultFolders] unless the user
     * renamed it) with three more azkar, and the new "when you are praised" folder. Only the new azkar are merged,
     * so ones the user removed from that folder stay removed.
     */
    private fun addShortAzkarAndPraised(p: ImportJson.Parsed.Ok) {
        renameDefaultFolders()   // users on 9 to 11 ran the renames before the first folder's was added
        val folders = p.folders.mapNotNull { f ->
            when (f.id) {
                // A user who deleted the folder doesn't get it back for three azkar.
                DefaultFolders.HOME -> f.takeIf { folder(DefaultFolders.HOME) != null }
                    ?.copy(items = f.items.filter { it.text in DefaultFolders.ADDED_TO_HOME_IN_V12 })
                DefaultFolders.PRAISED -> f
                else -> null
            }
        }
        mergeDefaults(p.copy(folders = folders))
    }

    /**
     * Version 13: the topic folders built from five dua books (they start hidden from Home, like other added
     * defaults), plus their duas that belong in existing folders. A folder the user deleted doesn't come back.
     */
    private fun addBookDuas(p: ImportJson.Parsed.Ok) {
        val folders = p.folders.mapNotNull { f ->
            val toExisting = DefaultFolders.ADDED_TO_EXISTING_IN_V13[f.id]
            when {
                f.id in DefaultFolders.ADDED_IN_V13 -> f
                toExisting != null -> f.takeIf { folder(f.id!!) != null }?.copy(items = f.items.filter { it.text in toExisting })
                else -> null
            }
        }
        mergeDefaults(p.copy(folders = folders))
    }

    private fun mergeDefaults(p: ImportJson.Parsed.Ok) {
        val before = fCache.map { it.id }.toSet()
        val (ns, _) = ImportMerge.apply(ImportMerge.State(fCache, zCache, sCache), p) { UUID.randomUUID().toString() }
        saveContent(ns.folders, ns.zikrs, ns.schedules)
        val created = ns.folders.map { it.id }.filter { it !in before }.toSet()
        val hide = p.folders.filter { it.home != true }.mapNotNull { it.id }.filter { it in created }
        if (hide.isNotEmpty()) setHidden(hidden() + hide)      // only brand-new default folders start hidden
    }

    /** One time: the three fixed reminders become editable schedules (same on/off state), named in the phone's language. */
    private fun migrateSchedules() {
        if (sp.getBoolean(K_SCHEDULES_INITIALIZED, false)) return
        fun s(id: String, title: Int, msg: Int, hour: Int, on: Boolean, open: String) =
            Schedule(id, appCtx.getString(title), appCtx.getString(msg), hour, 0, AppPrefs.reminderOn(id, on), open)
        saveSchedules(
            listOf(
                s("m", R.string.schedule_morning_title, R.string.schedule_morning_message, 6, true, DefaultFolders.MORNING),
                s("e", R.string.schedule_evening_title, R.string.schedule_evening_message, 17, true, DefaultFolders.EVENING),
                s("s", R.string.schedule_sleep_title, R.string.schedule_sleep_message, 22, false, DefaultFolders.SLEEP)
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
            androidx.core.content.ContextCompat.getMainExecutor(appCtx).execute {
                try {
                    WidgetUpdater.updateAll(appCtx)
                } finally {
                    widgetRefreshPending.set(false)
                }
            }
        }
    }

    /**
     * The in-memory content once loaded, else null. Screens start from it so they draw at once instead of
     * waiting for their first database read (which then only confirms the same data).
     */
    fun contentSnapshot(): AzkarContent? =
        if (initResult?.isCompleted == true) AzkarContent(fCache, zCache, sCache) else null

    // ---------- Azkar ----------
    fun zikrs(): List<Zikr> = zCache
    fun zikr(id: String): Zikr? = zCache.firstOrNull { it.id == id }
    fun zikrsIn(folderId: String): List<Zikr> = zCache.filter { it.folderId == folderId }

    /** Number of azkar per folder id (null key = Anytime), computed in one pass. */
    fun zikrCounts(): Map<String?, Int> = zCache.groupingBy { it.folderId }.eachCount()

    @Synchronized
    fun addZikr(arabic: String, note: String, repeat: Int, folderId: String?, title: String = "", titlePos: TitlePos = TitlePos.ABOVE, titleBelow: String = "", source: ZikrSource = ZikrSource()): Zikr {
        zCache.firstOrNull { it.arabic == arabic && it.folderId == folderId }?.let {
            val merged = it.copy(
                note = note.ifBlank { it.note }, repeat = repeat,
                title = title.ifBlank { it.title }, titlePos = if (title.isBlank()) it.titlePos else titlePos,
                titleBelow = if (title.isBlank()) it.titleBelow else titleBelow,
                source = if (source.isEmpty) it.source else source
            )
            updateZikr(merged)
            return merged
        }
        val z = Zikr(UUID.randomUUID().toString(), arabic, note, repeat, folderId, title, titlePos, source, titleBelow)
        saveZikrs(zCache + z)
        return z
    }

    @Synchronized
    fun updateZikr(z: Zikr) = saveZikrs(zCache.map { if (it.id == z.id) z else it })
    @Synchronized
    fun deleteZikr(id: String) {
        saveZikrs(zCache.filter { it.id != id })
        if (id in hzCache) setZikrsHidden(listOf(id), false)
        if (AppPrefs.zikrSourceHidden(id)) AppPrefs.setZikrSourceHidden(id, false)   // don't leave a stale per-zikr pref behind
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

    /** The Library / My azkar order, as dragged in Customize; folders missing from [ids] keep their place at the end. */
    @Synchronized
    fun setFolderOrder(ids: List<String>) {
        val byId = fCache.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        val rest = fCache.filter { it.id !in ids }
        if (ordered + rest != fCache) saveFolders(ordered + rest)
    }

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
        AppPrefs.clearResetRule(id)
        Tracking.setTracked(id, false)
    }

    fun folderName(key: String?): String =
        key?.let { folder(it)?.name } ?: appCtx.getString(R.string.anytime)

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
        if (!published) {
            // The user edited something while the import ran: the database now holds the import (plus any edit
            // written after it), so reload the caches from it instead of keeping a snapshot that no longer matches.
            repository.awaitPendingWrites()
            synchronized(this) {
                fCache = repository.folders()
                zCache = repository.zikrs()
                sCache = repository.schedules()
                cacheGeneration++
            }
        }
        notifyWidgets()
        sp.edit().putBoolean(K_SCHEDULES_INITIALIZED, true).apply()
        val sp2 = p.settings?.arabicSp ?: p.settings?.arabicSize?.let { ReaderPrefs.spFromOldIndex(it) }
        sp2?.let { AppPrefs.setArabicSp(it) }
        p.settings?.notificationsOn?.let { AppPrefs.setNotificationsOn(it) }
        p.settings?.prayerDelays?.forEach { (id, minutes) -> AppPrefs.setSessionDelayMinutes(id, minutes) }
        p.settings?.nudgeOn?.let { AppPrefs.setNudgeOn(it) }
        p.settings?.nudgeMinutes?.let { AppPrefs.setNudgeLeadMinutes(it) }
        p.settings?.calcMethod?.let { AppPrefs.setCalcMethod(it) }
        p.settings?.asrHanafi?.let { AppPrefs.setAsrHanafi(it) }
        // Rules follow their reminder / folder: same id if it kept it, else the id the merge gave it.
        // Only reminders this import added get a rule: one that already existed keeps its own setting.
        p.settings?.reminderRules?.forEach { (id, raw) ->
            val target = sum.reminderIds[id] ?: return@forEach
            com.azkar.app.reminders.TimeRule.decode(raw)?.let { r -> AppPrefs.setReminderRule(target, r) }
        }
        p.settings?.resetRules?.forEach { (id, raw) ->
            (sum.folderIds[id] ?: id).takeIf { folder(it) != null }?.let { AppPrefs.setResetRuleRaw(it, raw) }
        }
        // Tracked folders follow their folder: same id if it kept it, else the id the merge gave it.
        p.tracking?.let { t ->
            Tracking.restore(t.folders, t.history) { fileId -> (sum.folderIds[fileId] ?: fileId).takeIf { folder(it) != null } }
        }
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
            synchronized(this) {
                fCache = legacy.folders
                zCache = legacy.zikrs
                sCache = legacy.schedules
                cacheGeneration++
            }
            notifyWidgets()
            sp.edit()
                .putBoolean(K_ROOM_MIGRATION_VERIFIED, true)
                .putBoolean(K_SCHEDULES_INITIALIZED, true)
                .commit()
        }
        return restored
    }

    suspend fun exportJsonAsync(): String = withContext(Dispatchers.Default) {
        ExportJson.build(
            ImportMerge.State(fCache, zCache, sCache), AppPrefs.arabicSize(), AppPrefs.notificationsOn(), AppPrefs.arabicSp(),
            sessionDelays = listOf("m", "e", "s").associateWith { AppPrefs.sessionDelayMinutes(it) },
            nudgeOn = AppPrefs.nudgeOn(), nudgeMinutes = AppPrefs.nudgeLeadMinutes(),
            tracking = Tracking.toJson(),
            calcMethod = AppPrefs.calcMethod(), asrHanafi = AppPrefs.asrHanafi(),
            reminderRules = AppPrefs.reminderRules(), resetRules = AppPrefs.resetRules()
        )
    }

    /** One folder and its azkar only, in the same format, so it can be shared or imported elsewhere. */
    suspend fun exportFolderJsonAsync(folderId: String): String = withContext(Dispatchers.Default) {
        val folder = fCache.first { it.id == folderId }
        ExportJson.folder(folder, zCache.filter { it.folderId == folderId })
    }

    // ---------- Home customisation ----------
    val defaultOrder: List<String> get() = fCache.map { it.id }

    fun homeOrder(): List<String> = home.order(defaultOrder)
    fun setHomeOrder(order: List<String>) = home.setOrder(order)
    fun hidden(): Set<String> = home.hidden()
    fun setHidden(set: Set<String>) = home.setHidden(set)
    /** Resets the order; default folders that are not meant for Home (no "home": true) are hidden again. */
    fun resetHome() {
        val hide = defaults()?.folders?.filter { it.home != true }?.mapNotNull { it.id }.orEmpty()
        home.reset(hide.filter { folder(it) != null }.toSet())
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
    val hiddenZikrsFlow: StateFlow<Set<String>> get() = home.hiddenZikrs

    fun setZikrsHidden(ids: kotlin.collections.Collection<String>, hide: Boolean) = home.setZikrsHidden(ids, hide)


    // ---------- Notification schedules ----------
    fun schedules(): List<Schedule> = sCache
    fun schedule(id: String): Schedule? = sCache.firstOrNull { it.id == id }
    fun newScheduleId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun upsertSchedule(s: Schedule) {
        saveSchedules(if (sCache.any { it.id == s.id }) sCache.map { if (it.id == s.id) s else it } else sCache + s)
    }

    @Synchronized
    fun deleteSchedule(id: String) {
        saveSchedules(sCache.filter { it.id != id })
        AppPrefs.setReminderRule(id, null)
    }

    // ---------- Reader content ----------
    fun readerItems(key: String): List<ReaderItem> {
        if (key.startsWith("zikr:")) {
            return zikr(key.removePrefix("zikr:"))?.let { listOf(ReaderItem(it.arabic, it.repeat, it.id, it.title, it.titlePos, it.note, it.source, it.titleBelow)) } ?: emptyList()
        }
        val src = if (key == ANYTIME) zCache.filter { it.folderId == null } else zikrsIn(key)
        return Visibility.visible(src, hzCache).map { ReaderItem(it.arabic, it.repeat, it.id, it.title, it.titlePos, it.note, it.source, it.titleBelow) }
    }

    fun readerKey(z: Zikr): String = z.folderId ?: ANYTIME

    fun readerIndex(z: Zikr): Int {
        val src = if (z.folderId == null) zCache.filter { it.folderId == null } else zikrsIn(z.folderId)
        return Visibility.readerIndex(src, hzCache, z.id).coerceAtLeast(0)
    }
}