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

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("azkar", Context.MODE_PRIVATE)
        if (!sp.contains(K_ZIKRS)) seed()
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

    /** Deletes the folder; its azkar stay saved as "Anytime". */
    fun deleteFolder(id: String) {
        saveFolders(folders().filter { it.id != id })
        saveZikrs(zikrs().map { if (it.folderId == id) it.copy(folderId = null) else it })
        setHomeOrder(homeOrder().filter { it != id })
        setHidden(hidden() - id)
    }

    /** Display name for a folder key (built-in or custom). */
    fun folderName(key: String?): String {
        if (key == null) return "Anytime"
        Builtins.byKey(key)?.let { return it.title }
        return folder(key)?.name ?: "Anytime"
    }

    fun isBuiltinFolder(key: String?) = Builtins.byKey(key) != null

    // ---------- Home customisation ----------
    val defaultOrder: List<String> get() = Builtins.homeCollections.map { it.key } + folders().map { it.id }

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
    }

    // ---------- Reader / reminder settings ----------
    fun showTranslation() = sp.getBoolean(K_TRANS, true)
    fun setShowTranslation(v: Boolean) = sp.edit().putBoolean(K_TRANS, v).apply()

    /** 0 = small, 1 = medium (default), 2 = large. */
    fun arabicSize() = sp.getInt(K_SIZE, 1)
    fun setArabicSize(v: Int) = sp.edit().putInt(K_SIZE, v).apply()

    fun reminderOn(key: String, default: Boolean) = sp.getBoolean(K_REM + key, default)
    fun setReminderOn(key: String, v: Boolean) = sp.edit().putBoolean(K_REM + key, v).apply()

    // ---------- Reader content ----------
    /** Built-in azkar for a collection followed by azkar the user filed in it. */
    fun readerItems(key: String): List<ReaderItem> {
        if (key.startsWith("zikr:")) {
            return zikr(key.removePrefix("zikr:"))?.let { listOf(ReaderItem(it.arabic, it.repeat)) } ?: emptyList()
        }
        val base = Builtins.byKey(key)?.items ?: emptyList()
        return base + zikrsIn(key).map { ReaderItem(it.arabic, it.repeat) }
    }

    /** Reader key that opens [z] inside its own collection/folder ("Anytime" azkar open on their own). */
    fun readerKey(z: Zikr): String = z.folderId ?: "zikr:${z.id}"

    /** Position of [z] inside that reader list. */
    fun readerIndex(z: Zikr): Int {
        val f = z.folderId ?: return 0
        val base = Builtins.byKey(f)?.items?.size ?: 0
        return base + zikrsIn(f).indexOfFirst { it.id == z.id }.coerceAtLeast(0)
    }

    /** All saved azkar for the Library, grouped by category: built-ins, then your folders, then Anytime. */
    fun libraryZikrs(): List<Zikr> {
        val all = zikrs()
        val keys = ArrayList<String?>().apply {
            addAll(Builtins.library.map { it.key }); addAll(folders().map { it.id }); add(null)
        }
        return keys.flatMap { k -> all.filter { it.folderId == k } } + all.filter { it.folderId !in keys }
    }
}
