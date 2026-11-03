package com.azkar.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Home layout (folder order, hidden folders, placement) and the azkar hidden from reading. SharedPreferences only,
 * no Room content, so it can be tested on its own. [Store] owns one and keeps its old functions as thin wrappers.
 */
class HomePrefs {
    private lateinit var sp: SharedPreferences

    // Parsed once, then kept in step by the setters (Home renders read them often).
    @Volatile private var savedOrderCache: List<String>? = null
    @Volatile private var hiddenCache: Set<String>? = null
    private val hiddenZikrState = MutableStateFlow<Set<String>>(emptySet())

    /** Hidden zikr ids as they change. */
    val hiddenZikrs: StateFlow<Set<String>> = hiddenZikrState.asStateFlow()

    fun attach(prefs: SharedPreferences) {
        sp = prefs
        savedOrderCache = null
        hiddenCache = null
    }

    /** Reads the hidden azkar; [Store] calls it during initialization, off the main thread. */
    fun loadHiddenZikrs() {
        hiddenZikrState.value = readIds(K_HIDDEN_ZIKRS)
    }

    private fun readIds(key: String): Set<String> =
        try { LegacyJson.parseIds(sp.getString(key, "[]") ?: "[]") } catch (_: Exception) { emptySet() }

    private fun savedOrder(): List<String> = savedOrderCache ?: run {
        (if (sp.contains(K_ORDER)) readIds(K_ORDER).toList() else emptyList()).also { savedOrderCache = it }
    }

    /** The saved order, keeping only folders in [valid]; folders it doesn't know yet follow in [valid]'s order. */
    fun order(valid: List<String>): List<String> {
        val validSet = valid.toHashSet()
        val kept = savedOrder().filter { it in validSet }.distinct()
        val keptSet = kept.toHashSet()
        return kept + valid.filter { it !in keptSet }
    }

    fun setOrder(order: List<String>) {
        savedOrderCache = order.toList()
        sp.edit().putString(K_ORDER, JSONArray(order).toString()).apply()
    }

    fun hidden(): Set<String> = hiddenCache ?: readIds(K_HIDDEN).also { hiddenCache = it }

    fun setHidden(set: Set<String>) {
        hiddenCache = set.toSet()
        sp.edit().putString(K_HIDDEN, JSONArray(set.toList()).toString()).apply()
    }

    fun placement(): Placement = Placement.from(sp.getString(K_PLACE, null))
    fun setPlacement(p: Placement) = sp.edit().putString(K_PLACE, p.key).apply()

    /** Forgets order and placement; only [hideByDefault] stay hidden. */
    fun reset(hideByDefault: Set<String>) {
        sp.edit().remove(K_ORDER).remove(K_PLACE).apply()
        savedOrderCache = null
        setHidden(hideByDefault)
    }

    @Synchronized
    fun setZikrsHidden(ids: Collection<String>, hide: Boolean) {
        hiddenZikrState.value = Visibility.apply(hiddenZikrState.value, ids, hide)
        sp.edit().putString(K_HIDDEN_ZIKRS, JSONArray(hiddenZikrState.value.toList()).toString()).apply()
    }

    private companion object {
        const val K_ORDER = "home_order"
        const val K_HIDDEN = "home_hidden"
        const val K_PLACE = "home_place"
        const val K_HIDDEN_ZIKRS = "hidden_zikrs"
    }
}
