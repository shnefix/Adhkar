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
    private const val DEFAULTS_VERSION = 40
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

    // commit(), not apply(): migration markers must be on disk before the next step runs (off the main thread).
    @android.annotation.SuppressLint("ApplySharedPref")
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

    // commit(), not apply(): migration markers must be on disk before the next step runs (off the main thread).
    @android.annotation.SuppressLint("ApplySharedPref")
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

    // commit(), not apply(): migration markers must be on disk before the next step runs (off the main thread).
    @android.annotation.SuppressLint("ApplySharedPref")
    private fun verifyRoomMigration(legacy: LegacyContent) {
        if (repository.folders() == legacy.folders &&
            repository.zikrs() == legacy.zikrs &&
            repository.schedules() == legacy.schedules
        ) {
            sp.edit().putBoolean(K_ROOM_MIGRATION_VERIFIED, true).commit()
        }
    }

    // commit(), not apply(): migration markers must be on disk before the next step runs (off the main thread).
    @android.annotation.SuppressLint("ApplySharedPref")
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
        if (!fresh && from < 21) fillMissingDetails(p)   // 15: the fire hadith; 21: hadith text for reference-only sources
        if (from < 16) mergeTwinFolders()
        if (!fresh && from < 19) fillMissingSources(p)   // 17, 19: sources found for azkar that had none
        if (from < 18) createGroupFolders()
        if (from < 20) removeShortAzkar()
        if (!fresh && from < 22) removeUnsourcedDuas()
        if (!fresh && from < 23) { tashkeelFolderNames(p); removeUnsourcedDuas("removed_v23.json"); arabicGrades() }
        if (!fresh && from < 25) cleanZikrTexts()   // 25: matched ignoring tashkeel and spacing
        if (!fresh && from < 27) fullChains(p)   // 26: texts with no chain; 27: also chains cut short
        if (from < 28) putDhikrFirst()
        if (!fresh && from < 29) mergeSharedSources()
        if (!fresh && from < 30) fillMissingDetails(p)   // 30: Quran ayahs and checked hadith texts for more azkar
        if (from < 31) trimLibraryV31()
        if (from < 32) sortMorningEvening()
        if (from < 33) removeUnsourcedDuas("removed_v33.json", requireNoSource = false)  // 33: the weak azkar (backup in WEAK_AZKAR_BACKUP.json)
        if (from < 34) moveNonDuas()
        if (from < 35) groupNonDuas()
        if (from < 36) fillGrades("grades_v36.json")
        if (from < 37) fillGrades("grades_v37.json")  // 37: grades copied from matching graded azkar, plus mismatch warnings
        if (from < 40) istighfarFirstInTasabih()   // 31 tried this but missed the hamza in «أستغفر»
        if (from < 39) hideWeakAndUngraded()   // 38: folders; 39: also the azkar with no folder
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

    /**
     * Version 14: default azkar whose source was a reference only get the full hadith text. Only filled in where the
     * zikr text and reference are still the default's and no detail was written, so a user's own edits stay.
     */
    private fun fillMissingDetails(p: ImportJson.Parsed.Ok) {
        val byText = p.folders.filter { it.id != null }.associate { f -> f.id to f.items.associate { it.text to it.source } }
        val anyFolder = HashMap<String, ZikrSource>()   // azkar moved by the folder merge (16) match on text alone
        p.folders.forEach { f -> f.items.forEach { if (it.source.detail.isNotBlank()) anyFolder.putIfAbsent(it.text, it.source) } }
        var changed = false
        val next = zCache.map { z ->
            val src = byText[z.folderId]?.get(z.arabic)?.takeIf { it.detail.isNotBlank() } ?: anyFolder[z.arabic] ?: return@map z
            if (z.source.detail.isBlank() && src.detail.isNotBlank() && z.source.reference == src.reference) {
                changed = true
                z.copy(source = z.source.copy(detail = src.detail, grade = z.source.grade.ifBlank { src.grade }))
            } else z
        }
        if (changed) saveZikrs(next)
    }

    /**
     * Version 16: twin built-in folders become one ([FolderGroups.MERGES]). Azkar move to the kept folder (a text it
     * already has is dropped); reminders, Home and tracking that pointed at the absorbed folder now use the kept one.
     * Skipped for a pair whose kept folder the user deleted.
     */
    @Synchronized
    private fun mergeTwinFolders() {
        val into = HashMap<String, String>()
        FolderGroups.MERGES.forEach { (keep, absorbed) ->
            if (folder(keep) != null) absorbed.filter { folder(it) != null }.forEach { into[it] = keep }
        }
        if (into.isEmpty()) return
        val texts = HashMap<String, MutableSet<String>>()
        zCache.forEach { z -> z.folderId?.let { texts.getOrPut(it) { HashSet() } += z.arabic.trim() } }
        val dropped = HashSet<String>()
        val nextZikrs = zCache.mapNotNull { z ->
            val keep = into[z.folderId] ?: return@mapNotNull z
            if (!texts.getOrPut(keep) { HashSet() }.add(z.arabic.trim())) { dropped += z.id; null }
            else z.copy(folderId = keep)
        }
        val nextSchedules = sCache.map { s -> into[s.open]?.let { s.copy(open = it) } ?: s }
        saveContent(fCache.filter { it.id !in into }, nextZikrs, nextSchedules)
        setHomeOrder(homeOrder().filter { it !in into })
        setHidden(hidden() - into.keys)
        setZikrsHidden(dropped, false)
        into.keys.forEach { AppPrefs.clearPosition(it); AppPrefs.clearResetRule(it); Tracking.setTracked(it, false) }
    }

    /**
     * Version 17: built-in azkar that had no source get the one added to the defaults. Matched by text (twin folders
     * were merged in 16, so a zikr may now sit in another folder), and only where the user's zikr still has no source
     * at all, so nothing the user wrote is replaced.
     */
    private fun fillMissingSources(p: ImportJson.Parsed.Ok) {
        val byFolder = HashMap<Pair<String?, String>, ZikrSource>()
        val byText = HashMap<String, ZikrSource>()
        p.folders.forEach { f ->
            f.items.forEach {
                if (it.source.isEmpty) return@forEach
                byFolder[f.id to it.text.trim()] = it.source
                byText.putIfAbsent(it.text.trim(), it.source)
            }
        }
        var changed = false
        val next = zCache.map { z ->
            val text = z.arabic.trim()
            val src = byFolder[z.folderId to text] ?: byText[text]
            if (src != null && z.source.isEmpty) { changed = true; z.copy(source = src) } else z
        }
        if (changed) saveZikrs(next)
    }

    /**
     * Version 20: the built-in «أذكار قصيرة» folder is gone, with its azkar (they are all in other folders too).
     * Folders the user put inside it move up a level; a reminder that opened it now just opens the app.
     */
    @Synchronized
    private fun removeShortAzkar() {
        val id = DefaultFolders.HOME
        val f = folder(id) ?: return
        saveContent(
            fCache.filter { it.id != id }.map { if (it.parent == id) it.copy(parent = f.parent) else it },
            zCache.filter { it.folderId != id },
            sCache.map { if (it.open == id) it.copy(open = null) else it }
        )
        setHomeOrder(homeOrder().filter { it != id })
        setHidden(hidden() - id)
        AppPrefs.clearPosition(id); AppPrefs.clearResetRule(id); Tracking.setTracked(id, false)
    }

    /**
     * Version 31: the folders الاستغفار، أذكار خفيفة، الغسل and التشهد are gone with their azkar; دعاء القنوت keeps only
     * its first card; and «أستغفر الله» is the first zikr in تسابيح.
     */
    @Synchronized
    private fun trimLibraryV31() {
        val gone = setOf("d_1", "d_4", "d_54", "d_69", "d_70")
        val parentOf = fCache.associate { it.id to it.parent }
        val qunutFirst = zCache.firstOrNull { it.folderId == "d_3" }?.id
        var z = zCache.filter { it.folderId !in gone && !(it.folderId == "d_3" && it.id != qunutFirst) }
        val bare = Regex("[ً-ٰٟ]")
        val i = z.indexOfFirst { it.folderId == DefaultFolders.TASABIH && bare.replace(it.arabic, "").trim() == "استغفر الله" }
        val j = z.indexOfFirst { it.folderId == DefaultFolders.TASABIH }
        if (i > j && j >= 0) z = z.toMutableList().also { l -> l.add(j, l.removeAt(i)) }
        saveContent(
            fCache.filter { it.id !in gone }.map { f -> if (f.parent in gone) f.copy(parent = parentOf[f.parent]) else f },
            z,
            sCache.map { if (it.open in gone) it.copy(open = null) else it }
        )
        setHomeOrder(homeOrder().filter { it !in gone })
        setHidden(hidden() - gone)
        gone.forEach { AppPrefs.clearPosition(it); AppPrefs.clearResetRule(it); Tracking.setTracked(it, false) }
    }

    /**
     * Version 32: in أذكار الصباح and أذكار المساء the sound azkar come first, then those that are sound but not specific to
     * the time, then the weak ones; repeated azkar are dropped (assets/order_v32.json). Matched by exact text; a zikr the
     * user added or edited keeps its place after the listed ones.
     */
    @Synchronized
    private fun sortMorningEvening() {
        val o = try {
            org.json.JSONObject(appCtx.assets.open("order_v32.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) { android.util.Log.w("Azkar", "order_v32.json unreadable", e); return }
        var z: List<Zikr> = zCache
        for (fid in o.keys()) {
            val spec = o.getJSONObject(fid)
            val order = spec.getJSONArray("order").let { a -> (0 until a.length()).map { a.getString(it) } }
            val remove = spec.getJSONArray("remove").let { a -> (0 until a.length()).map { a.getString(it) }.toHashSet() }
            val at = z.indexOfFirst { it.folderId == fid }
            if (at < 0) continue
            val mine = z.filter { it.folderId == fid && it.arabic !in remove }
            val rank = order.withIndex().associate { (i, t) -> t to i }
            val sorted = mine.withIndex().sortedWith(compareBy({ rank[it.value.arabic] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
            val others = z.filter { it.folderId != fid }
            val before = z.subList(0, at).count { it.folderId != fid }
            z = others.toMutableList().also { it.addAll(before, sorted) }
        }
        if (z != zCache) saveZikrs(z)
    }

    /**
     * Version 34: cards that are advice, rulings, virtues, narrations or notes rather than a dua or dhikr move out of their
     * folders into «فوائد وتنبيهات» (one subfolder per kind, hidden from Home); assets/non_dua_v34.json lists them by exact
     * text. Each keeps a «من: <folder>» note so its origin is clear. A card the user edited keeps its place.
     */
    @Synchronized
    private fun moveNonDuas() {
        val spec = try {
            org.json.JSONArray(appCtx.assets.open("non_dua_v34.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) { android.util.Log.w("Azkar", "non_dua_v34.json unreadable", e); return }
        val kindOf = HashMap<String, String>()
        val kinds = ArrayList<Pair<String, String>>()
        for (i in 0 until spec.length()) {
            val o = spec.getJSONObject(i); val id = o.getString("id")
            kinds += id to o.getString("name")
            o.getJSONArray("texts").let { a -> for (j in 0 until a.length()) kindOf.putIfAbsent(a.getString(j), id) }
        }
        val names = fCache.associate { it.id to it.name }
        val moved = HashSet<String>()
        val next = zCache.map { z ->
            val kind = kindOf[z.arabic]
            if (kind == null || z.folderId == null || z.folderId in kinds.map { it.first }) z else {
                moved += kind
                val from = names[z.folderId]?.let { "من: $it" }
                z.copy(folderId = kind, note = listOfNotNull(from, z.note.takeIf { it.isNotBlank() }).joinToString(" — "))
            }
        }
        if (moved.isEmpty()) return
        val root = "g_notes"
        val folders = ArrayList(fCache)
        if (folders.none { it.id == root }) folders += Folder(root, "فوائد وتنبيهات")
        val made = ArrayList<String>(listOf(root))
        kinds.filter { it.first in moved }.forEach { (id, name) ->
            if (folders.none { it.id == id }) { folders += Folder(id, name, parent = root); made += id }
        }
        saveContent(folders, next, sCache)
        setHidden(hidden() + made)
    }

    /**
     * Version 35: inside each «فوائد وتنبيهات» subfolder the cards are grouped into topic folders (الصلاة، النوم، المرض والموت…),
     * and a card with no title gets a short one (assets/non_dua_v35.json, matched by exact text; an existing title stays).
     */
    @Synchronized
    private fun groupNonDuas() {
        val o = try {
            org.json.JSONObject(appCtx.assets.open("non_dua_v35.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) { android.util.Log.w("Azkar", "non_dua_v35.json unreadable", e); return }
        val themes = o.getJSONArray("themes").let { a -> (0 until a.length()).map { a.getString(it) } }
        val items = o.getJSONArray("items")
        class Spec(val kind: String, val theme: Int, val title: String)
        val byText = HashMap<String, Spec>()
        for (i in 0 until items.length()) items.getJSONObject(i).let {
            byText.putIfAbsent(it.getString("text"), Spec(it.getString("kind"), if (it.isNull("theme")) -1 else it.getInt("theme"), it.optString("title")))
        }
        val kinds = byText.values.map { it.kind }.toSet()
        val made = LinkedHashMap<String, Folder>()
        var changed = false
        val next = zCache.map { z ->
            val sp = byText[z.arabic]
            if (sp == null || z.folderId != sp.kind) z else {
                changed = true
                val title = if (z.title.isBlank() && sp.title.isNotBlank()) sp.title else z.title
                if (sp.theme < 0 || sp.kind == "n_poem") z.copy(title = title) else {
                    val id = "nt_${sp.kind}_${sp.theme}"
                    made.getOrPut(id) { Folder(id, themes[sp.theme], parent = sp.kind) }
                    z.copy(folderId = id, title = title)
                }
            }
        }
        if (!changed) return
        val have = fCache.map { it.id }.toSet()
        saveContent(fCache + made.values.filter { it.id !in have }, next, sCache)
        setHidden(hidden() + made.keys)
    }

    /**
     * Version 36: grades and research links for azkar that had no grade (assets/grades_v36.json, matched by exact text).
     * Only a zikr whose grade is still blank changes; a link is added to the source page's text once.
     */
    @Synchronized
    private fun fillGrades(asset: String) {
        val rows = try {
            org.json.JSONArray(appCtx.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) { android.util.Log.w("Azkar", "$asset unreadable", e); return }
        class Fill(val grade: String, val add: String)
        val by = HashMap<String, Fill>()
        for (i in 0 until rows.length()) rows.getJSONObject(i).let { by[it.getString("text")] = Fill(it.optString("grade"), it.optString("add")) }
        var changed = false
        val next = zCache.map { z ->
            val f = by[z.arabic]
            if (f == null || z.source.grade.isNotBlank()) z else {
                val detail = if (f.add.isNotBlank() && !z.source.detail.contains(f.add)) listOf(z.source.detail, f.add).filter { it.isNotBlank() }.joinToString("\n\n") else z.source.detail
                val grade = f.grade.ifBlank { z.source.grade }
                if (detail == z.source.detail && grade == z.source.grade) z else { changed = true; z.copy(source = z.source.copy(grade = grade, detail = detail)) }
            }
        }
        if (changed) saveZikrs(next)
    }

    /**
     * Version 38: in every folder except مُخْتَارَاتٌ (d_16) and «فوائد وتنبيهات», the weak azkar and those with no grade go to the
     * end of the folder and are hidden (the folder screen's eye button shows them). Only built-in azkar are touched: a card
     * with no grade counts when it has a source or its text is one of the built-in texts, so cards the user wrote stay.
     */
    @Synchronized
    private fun hideWeakAndUngraded() {
        val builtIn = HashSet<String>()
        defaults()?.let { p -> p.folders.forEach { f -> f.items.forEach { builtIn += it.text } }; p.unfiled.forEach { builtIn += it.text } }
        val weak = Regex("ضعيف|موضوع|منكر|لا أصل")
        fun skipFolder(id: String?) = id == "d_16" || id == "g_notes" || id?.startsWith("n_") == true || id?.startsWith("nt_") == true
        fun flagged(z: Zikr): Boolean {
            if (skipFolder(z.folderId)) return false
            val g = z.source.grade.trim()
            if (g.isNotEmpty()) return weak.containsMatchIn(g)
            val ref = z.source.reference
            if (ref.startsWith("سورة") || ref.startsWith("مقتبس من القرآن")) return false
            return ref.isNotBlank() || z.arabic in builtIn
        }
        val flaggedIds = zCache.filter { flagged(it) }.map { it.id }.toHashSet()
        if (flaggedIds.isEmpty()) return
        val queues = HashMap<String, ArrayDeque<Zikr>>()
        zCache.groupBy { it.folderId }.forEach { (fid, list) ->
            if (fid != null && list.any { it.id in flaggedIds }) queues[fid] = ArrayDeque(list.filter { it.id !in flaggedIds } + list.filter { it.id in flaggedIds })
        }
        val next = zCache.map { z -> queues[z.folderId]?.removeFirst() ?: z }
        saveZikrs(next)
        setZikrsHidden(flaggedIds, true)
    }

    /** Version 40: «أستغفر الله» is the first card in تسابيح (the hamza had made version 31 miss it). */
    @Synchronized
    private fun istighfarFirstInTasabih() {
        val marks = Regex("[\u064B-\u065F\u0670\u0640]")
        val folder = DefaultFolders.TASABIH
        val i = zCache.indexOfFirst { it.folderId == folder && marks.replace(it.arabic, "").replace('أ', 'ا').trim() == "استغفر الله" }
        val j = zCache.indexOfFirst { it.folderId == folder }
        if (i < 0 || j < 0 || i <= j) return
        saveZikrs(zCache.toMutableList().also { it.add(j, it.removeAt(i)) })
    }

    /**
     * Version 22: the built-in azkar with no source in four folders (أدعية للميّت، جوامع الدعاء، أدعية طلب العلم،
     * أذكار داخل الصلاة) are dropped. assets/removed_v22.json lists them by folder; one is removed only while it is
     * still in that folder with its text unchanged and no source, so the user's own azkar and edits stay.
     */
    @Synchronized
    private fun removeUnsourcedDuas(asset: String = "removed_v22.json", requireNoSource: Boolean = true) {
        val listed = try {
            val o = org.json.JSONObject(appCtx.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() })
            o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) }.toHashSet() } }
        } catch (e: Exception) { android.util.Log.w("Azkar", "$asset unreadable", e); return }
        val gone = zCache.filter { z -> (!requireNoSource || z.source.isEmpty) && listed[z.folderId]?.contains(z.arabic) == true }.map { it.id }.toHashSet()
        if (gone.isEmpty()) return
        saveZikrs(zCache.filter { it.id !in gone })
        setZikrsHidden(gone, false)
    }

    /**
     * Version 23: folder names gain their tashkeel. A built-in or parent folder is renamed only while its name, without
     * tashkeel, is still the default one, so a name the user changed stays.
     */
    @Synchronized
    private fun tashkeelFolderNames(p: ImportJson.Parsed.Ok) {
        val bare = Regex("[\u064B-\u065F\u0670]")
        fun plain(s: String) = bare.replace(s, "").replace('ٱ', 'ا').trim()
        val wanted = p.folders.mapNotNull { f -> f.id?.let { it to f.name } }.toMap() + FolderGroups.ALL.associate { it.id to it.name }
        var changed = false
        val next = fCache.map { f ->
            val name = wanted[f.id]
            if (name != null && f.name != name && plain(f.name) == plain(name)) { changed = true; f.copy(name = name) } else f
        }
        if (changed) saveFolders(next)
    }

    /** Version 23: grades filled in English («Sahih Isnaad — الألباني») read in Arabic. */
    @Synchronized
    private fun arabicGrades() {
        val en = mapOf("Sahih Isnaad" to "صحيح الإسناد", "Hasan Isnaad" to "حسن الإسناد", "Daif Isnaad" to "ضعيف الإسناد",
            "Very Daif" to "ضعيف جدًا", "Hasan Sahih" to "حسن صحيح", "Sahih" to "صحيح", "Hasan" to "حسن", "Daif" to "ضعيف")
        var changed = false
        val next = zCache.map { z ->
            val m = Regex("^([A-Za-z ]+?)\\s*—\\s*(.*)$").find(z.source.grade) ?: return@map z
            val ar = en[m.groupValues[1].trim()] ?: return@map z
            changed = true
            z.copy(source = z.source.copy(grade = "$ar — ${m.groupValues[2]}"))
        }
        if (changed) saveZikrs(next)
    }

    /**
     * Version 24: seven built-in azkar whose text held the whole narration, a citation or several duas keep only the
     * zikr itself, the rest moving to the note and source (assets/fixes_v24.json). A zikr is changed only while its text
     * is still the old one; the «أعوذ بالله…» for animals' cries is added after the cock-crow dua.
     */
    @Synchronized
    private fun cleanZikrTexts() {
        val o = try {
            org.json.JSONObject(appCtx.assets.open("fixes_v24.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) { android.util.Log.w("Azkar", "fixes_v24.json unreadable", e); return }
        fun org.json.JSONObject.str(k: String): String? = if (has(k)) optString(k) else null
        // Older installs may hold the same text with slightly different tashkeel or spacing.
        val marks = Regex("[\\u064B-\\u065F\\u0670\\u0640\\s\\p{P}]")
        fun plain(t: String) = marks.replace(t, "")
        val list = zCache.toMutableList()
        val fixes = o.getJSONArray("fix")
        for (i in 0 until fixes.length()) {
            val f = fixes.getJSONObject(i)
            val old = f.getString("old"); val n = f.getJSONObject("new")
            val at = list.indexOfFirst { plain(it.arabic) == plain(old) }
            if (at < 0) continue
            val z = list[at]
            list[at] = z.copy(
                arabic = n.str("text") ?: z.arabic,
                note = n.str("note") ?: z.note,
                title = n.str("title") ?: z.title,
                source = z.source.copy(
                    reference = n.str("source") ?: z.source.reference,
                    narrator = n.str("narrator") ?: z.source.narrator,
                    grade = n.str("grade") ?: z.source.grade
                )
            )
        }
        val add = o.getJSONObject("add")
        val item = add.getJSONObject("item")
        val after = list.indexOfFirst { plain(it.arabic) == plain(add.getString("after")) }
        if (after >= 0 && list.none { it.folderId == list[after].folderId && plain(it.arabic) == plain(item.getString("text")) }) {
            list.add(after + 1, Zikr(
                UUID.randomUUID().toString(), item.getString("text"), item.optString("note"), item.optInt("count", 1),
                list[after].folderId, item.optString("title"), TitlePos.ABOVE,
                ZikrSource(item.optString("source"), "", item.optString("narrator"), "", item.optString("grade"))
            ))
        }
        if (list != zCache) saveZikrs(list)
    }

    /**
     * Versions 26–27: hadith texts whose chain was cut short get the book's full text, so the whole isnad is drawn. Only
     * where the zikr still has the default reference and the default text gives a longer chain than its own.
     */
    @Synchronized
    private fun fullChains(p: ImportJson.Parsed.Ok) {
        val byText = HashMap<String, ZikrSource>()
        p.folders.forEach { f -> f.items.forEach { if (it.source.detail.isNotBlank()) byText.putIfAbsent(it.text, it.source) } }
        var changed = false
        val next = zCache.map { z ->
            val d = byText[z.arabic] ?: return@map z
            if (d.detail == z.source.detail || d.reference != z.source.reference) return@map z
            val mine = if (z.source.detail.isBlank()) 0 else NarratorChain.parse(z.source.detail, z.source.reference)?.size ?: 0
            if ((NarratorChain.parse(d.detail, d.reference)?.size ?: 0) <= mine) return@map z
            changed = true
            z.copy(source = z.source.copy(detail = d.detail))
        }
        if (changed) saveZikrs(next)
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

    /** One background thread draws the widgets after a change, so the screen never waits on the bitmaps. */
    private val widgetExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "azkar-widgets").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }

    private fun notifyWidgets() {
        if (::appCtx.isInitialized && widgetRefreshPending.compareAndSet(false, true)) {
            widgetExecutor.execute {
                // Cleared first: a change made while drawing queues one more pass instead of being missed.
                widgetRefreshPending.set(false)
                runCatching { WidgetUpdater.updateAll(appCtx) }
                    .onFailure { android.util.Log.e("Azkar", "Widget refresh failed", it) }
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
    fun addFolder(name: String, icon: String? = null, parent: String? = null): Folder {
        val f = Folder(UUID.randomUUID().toString(), name, icon?.takeUnless { EmojiRules.isRestricted(it) },
            parent = parent?.takeIf { p -> fCache.any { it.id == p } })
        saveFolders(fCache + f)
        return f
    }

    /** Folders directly inside [parent] (null = the Library's top level). */
    fun childFolders(parent: String?): List<Folder> = FolderTree.children(fCache, parent)

    /** Ids of the folders that have something to read: at least one zikr of their own that isn't hidden. */
    private fun foldersWithAzkar(): Set<String> {
        val hidden = hzCache
        return zCache.mapNotNullTo(HashSet()) { z -> z.folderId?.takeIf { z.id !in hidden } }
    }

    /** The folder's name after the folders it sits in («الْيَوْمُ وَاللَّيْلَةُ ‹ أذكار الصباح»), for pickers. */
    fun folderPath(id: String): String = FolderTree.path(fCache, id).ifEmpty { folderName(id) }

    /** Folders a widget can show, in Library order. Parent folders that only hold other folders are left out. */
    fun readableFolders(): List<Folder> = FolderTree.readable(fCache, foldersWithAzkar())

    /**
     * The folder a widget set to [key] actually reads: [key] itself, or, when it is a parent folder with no azkar of its
     * own, the first folder inside it that has some. A deleted folder's azkar were moved to "Anytime", so that is read.
     */
    fun readableFolder(key: String): String {
        if (key == ANYTIME || key == Tracking.ALL || key.startsWith("zikr:")) return key
        // Only once the content is loaded: before that every folder looks deleted.
        if (folder(key) == null) return if (initResult?.isCompleted == true) ANYTIME else key
        return FolderTree.firstReadable(fCache, key, foldersWithAzkar())
    }

    /** Where [id] may be moved: null (top level) and every folder that isn't [id] itself or inside it. */
    fun moveTargets(id: String): List<Pair<Folder, Int>> {
        val blocked = FolderTree.subtree(fCache, id)
        return FolderTree.flatten(fCache).filter { it.first.id !in blocked }
    }

    /** Puts folder [id] inside [parent] (null = top level). Refused when that would put a folder inside itself. */
    @Synchronized
    fun moveFolderTo(id: String, parent: String?): Boolean {
        if (parent != null && (parent in FolderTree.subtree(fCache, id) || fCache.none { it.id == parent })) return false
        saveFolders(fCache.map { if (it.id == id) it.copy(parent = parent) else it })
        return true
    }

    /**
     * Version 18: the Library's ten parent folders ([FolderGroups.ALL]) become real folders the user can rename, move or
     * delete. Each takes the built-in folders listed for it that are still at the top level; a group with none of its
     * folders left isn't created. They are left off Home, since they hold folders rather than azkar.
     */
    /** Version 28: the Library opens with الذكر المطلق first, and تسابيح is the first folder inside it. */
    @Synchronized
    private fun putDhikrFirst() {
        val group = fCache.firstOrNull { it.id == FolderGroups.DHIKR } ?: return
        val tasabih = fCache.firstOrNull { it.id == DefaultFolders.TASABIH && it.parent == group.id }
        val rest = fCache.filter { it.id != group.id && it.id != tasabih?.id }
        val next = listOfNotNull(group, tasabih) + rest
        if (next != fCache) saveFolders(next)
    }

    /**
     * Version 29: a zikr that sits in several folders cites every source it has in all of them (assets/merged_sources_v29.json).
     * Only a zikr that still has the old default reference is changed; one the user edited is left alone.
     */
    @Synchronized
    private fun mergeSharedSources() {
        val rows = try {
            val raw = appCtx.assets.open("merged_sources_v29.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            org.json.JSONArray(raw)
        } catch (e: Exception) { return }
        var changed = false
        var next: List<Zikr> = zCache
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val folder = r.getString("folder"); val text = r.getString("text"); val old = r.getString("old")
            val ref = r.getString("source"); val detail = r.optString("detail", "")
            next = next.map { z ->
                if (z.folderId != folder || z.arabic != text || z.source.reference != old) z else {
                    changed = true
                    z.copy(source = z.source.copy(reference = ref, detail = z.source.detail.ifBlank { detail }))
                }
            }
        }
        if (changed) saveZikrs(next)
    }

    @Synchronized
    private fun createGroupFolders() {
        val present = fCache.associateBy { it.id }
        val groups = ArrayList<Folder>()
        val parentOf = HashMap<String, String>()
        FolderGroups.ALL.forEach { g ->
            val kids = g.folders.filter { present[it]?.parent == null && it in present }
            if (kids.isEmpty() || g.id in present) return@forEach
            groups += Folder(g.id, g.name)
            kids.forEach { parentOf[it] = g.id }
        }
        if (groups.isEmpty()) return
        saveFolders(groups + fCache.map { f -> parentOf[f.id]?.let { f.copy(parent = it) } ?: f })
        setHidden(hidden() + groups.map { it.id })
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
        val up = folder(id)?.parent        // folders inside it move up to where it was
        val nextFolders = fCache.filter { it.id != id }.map { if (it.parent == id) it.copy(parent = up) else it }
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
        restoreHiddenAndHome(p, sum)
        // Tracked folders follow their folder: same id if it kept it, else the id the merge gave it.
        p.tracking?.let { t ->
            Tracking.restore(t.folders, t.history) { fileId -> (sum.folderIds[fileId] ?: fileId).takeIf { folder(it) != null } }
        }
        return sum
    }

    /**
     * After an import: azkar the file marks hidden are hidden again (matched by folder and text, since the merge gives
     * them new ids), and the file's Home order and hidden folders apply to the folders it holds. Folders that are only
     * in this app keep their place after them.
     */
    private fun restoreHiddenAndHome(p: ImportJson.Parsed.Ok, sum: ImportMerge.Summary) {
        fun mapped(fileId: String) = (sum.folderIds[fileId] ?: fileId).takeIf { folder(it) != null }
        val hide = HashSet<String>()
        fun hideMatching(folderId: String?, items: List<ImportJson.Item>) {
            val texts = items.filter { it.hidden }.mapTo(HashSet()) { it.text }
            if (texts.isEmpty()) return
            zCache.forEach { z -> if (z.folderId == folderId && z.arabic in texts) hide += z.id }
        }
        p.folders.forEach { f -> f.id?.let { mapped(it) }?.let { hideMatching(it, f.items) } }
        hideMatching(null, p.unfiled)
        if (hide.isNotEmpty()) setZikrsHidden(hide, true)

        val s = p.settings ?: return
        s.homeOrder?.let { order ->
            val ids = order.mapNotNull { mapped(it) }.distinct()
            if (ids.isNotEmpty()) setHomeOrder(ids + homeOrder().filter { it !in ids })
        }
        s.homeHidden?.let { fileHidden ->
            val inFile = p.folders.mapNotNull { f -> f.id?.let { mapped(it) } }.toSet()
            val nowHidden = fileHidden.mapNotNull { mapped(it) }.toSet()
            setHidden(hidden() - inFile + nowHidden)
        }
    }

    /** Restores the retained pre-Room content only when Room is still completely empty. */
    // commit(), not apply(): migration markers must be on disk before the next step runs (off the main thread).
    @android.annotation.SuppressLint("ApplySharedPref")
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
            reminderRules = AppPrefs.reminderRules(), resetRules = AppPrefs.resetRules(),
            hiddenZikrs = hzCache, homeOrder = homeOrder(), homeHidden = hidden()
        )
    }

    /** One folder and its azkar only, in the same format, so it can be shared or imported elsewhere. */
    suspend fun exportFolderJsonAsync(folderId: String): String = withContext(Dispatchers.Default) {
        val folder = fCache.firstOrNull { it.id == folderId } ?: error("Folder no longer exists")
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