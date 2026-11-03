package com.azkar.app.data

import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Opt-in daily tracking (off by default): which folders are tracked, and how far each one got per day.
 * A day counts towards a streak when every tracked folder in question was finished that day.
 * Stored in its own preferences file, so it never touches the Room content or backups.
 */
object Tracking {
    private lateinit var sp: SharedPreferences

    private const val K_TRACKED = "tracked"           // JSON list of folder ids
    private const val K_DAYS = "days_"                // days_<folderId> = {"yyyyMMdd": "done/total"}
    private const val K_TODAY = "today_"              // today_<folderId> = "yyyyMMdd:done/total", for every folder
    private const val KEEP_DAYS = 400

    /** Widget folder value meaning "every tracked folder". */
    const val ALL = "tracked"

    enum class Level { MISSED, LOW, MID, FULL, TODAY, UPCOMING }

    data class Progress(val done: Int, val total: Int) {
        val fraction get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
        val complete get() = total > 0 && done >= total
    }

    /** Called by [Store.init]. */
    internal fun attach(prefs: SharedPreferences) {
        sp = prefs
    }

    fun dayKey(at: Calendar): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(at.time)
    private fun dayKey(at: Long) = dayKey(Calendar.getInstance().apply { timeInMillis = at })

    // ---------- Which folders ----------
    /** Tracked folder ids that still exist, in folder order. */
    fun tracked(): List<String> {
        val saved = readIds()
        return Store.folders().map { it.id }.filter { it in saved }
    }

    fun isTracked(folderId: String) = folderId in readIds()
    fun anyTracked() = tracked().isNotEmpty()

    fun setTracked(folderId: String, on: Boolean) {
        val next = if (on) readIds() + folderId else readIds() - folderId
        sp.edit().putString(K_TRACKED, org.json.JSONArray(next.toList()).toString()).apply()
    }

    /** [folder] if it is tracked, otherwise every tracked folder (the widgets' default). */
    fun resolve(folder: String?): List<String> =
        folder?.takeIf { it != ALL && isTracked(it) && Store.folder(it) != null }?.let { listOf(it) } ?: tracked()

    private fun readIds(): Set<String> = try {
        val arr = org.json.JSONArray(sp.getString(K_TRACKED, "[]"))
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    } catch (_: Exception) { emptySet() }

    // ---------- Recording ----------
    /**
     * Saves how far [folderId] got today; a day keeps its best result. Every folder keeps today's value
     * (widgets and the "not finished yet" reminder read it); only tracked folders keep a history.
     */
    fun record(folderId: String, done: Int, total: Int, at: Long = System.currentTimeMillis()) {
        if (total <= 0 || !::sp.isInitialized) return
        val key0 = dayKey(at)
        val prev = today(folderId, at)
        if (prev == null || prev.total != total || done > prev.done) {
            sp.edit().putString(K_TODAY + folderId, "$key0:${done.coerceIn(0, total)}/$total").apply()
        }
        if (!isTracked(folderId)) return
        val days = history(folderId).toMutableMap()
        val key = dayKey(at)
        val before = days[key]
        val next = Progress(done.coerceIn(0, total), total)
        if (before != null && before.fraction >= next.fraction && before.total == total) return
        days[key] = if (before != null && before.total == total) Progress(maxOf(before.done, next.done), total) else next
        val kept = days.keys.sorted().takeLast(KEEP_DAYS)
        val o = JSONObject()
        kept.forEach { k -> days[k]?.let { o.put(k, "${it.done}/${it.total}") } }
        sp.edit().putString(K_DAYS + folderId, o.toString()).apply()
    }

    /** How far [folderId] got today (any folder, tracked or not); null if it wasn't opened today. */
    fun today(folderId: String, at: Long = System.currentTimeMillis()): Progress? {
        if (!::sp.isInitialized) return null
        val raw = sp.getString(K_TODAY + folderId, null) ?: return null
        val (day, value) = raw.split(':').takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return null
        if (day != dayKey(at)) return null
        val parts = value.split('/')
        val d = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val t = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return Progress(d, t)
    }

    // Streaks walk the history day by day, so each folder's JSON is parsed once per change, not per day.
    private val parsed = HashMap<String, Pair<String, Map<String, Progress>>>()

    @Synchronized
    fun history(folderId: String): Map<String, Progress> {
        val raw = sp.getString(K_DAYS + folderId, "{}") ?: "{}"
        parsed[folderId]?.takeIf { it.first == raw }?.let { return it.second }
        val days = try {
            val o = JSONObject(raw)
            o.keys().asSequence().mapNotNull { k ->
                val parts = o.optString(k).split('/')
                val d = parts.getOrNull(0)?.toIntOrNull()
                val t = parts.getOrNull(1)?.toIntOrNull()
                if (d == null || t == null) null else k to Progress(d, t)
            }.toMap()
        } catch (_: Exception) { emptyMap() }
        parsed[folderId] = raw to days
        return days
    }

    // ---------- Reading back ----------
    /** Combined progress of [folders] on [day]: done and total azkar summed over the folders. */
    fun progress(folders: List<String>, day: Calendar): Progress {
        val key = dayKey(day)
        var done = 0; var total = 0
        folders.forEach { f ->
            val p = history(f)[key] ?: today(f, day.timeInMillis)
            if (p != null) { done += p.done; total += p.total }
            else total += Store.readerItems(f).size
        }
        return Progress(done, total)
    }

    fun completeOn(folders: List<String>, day: Calendar): Boolean {
        if (folders.isEmpty()) return false
        val key = dayKey(day)
        return folders.all { history(it)[key]?.complete == true }
    }

    /** Heat level of one day for the calendar: the average fraction over [folders]. */
    fun level(folders: List<String>, day: Calendar): Level {
        if (folders.isEmpty()) return Level.MISSED
        val key = dayKey(day)
        val avg = folders.map { history(it)[key]?.fraction ?: 0f }.average()
        return when {
            avg >= 1.0 -> Level.FULL
            avg >= 0.5 -> Level.MID
            avg > 0.0 -> Level.LOW
            else -> Level.MISSED
        }
    }

    /**
     * Consecutive finished days up to today. Today only adds once it is finished; an unfinished today
     * doesn't break the streak until the day is over.
     */
    fun streak(folders: List<String>, today: Calendar = Calendar.getInstance()): Int {
        if (folders.isEmpty()) return 0
        val day = today.clone() as Calendar
        if (!completeOn(folders, day)) day.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (n <= KEEP_DAYS && completeOn(folders, day)) {
            n++
            day.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    /** Longest run of finished days in the kept history. */
    fun best(folders: List<String>): Int {
        if (folders.isEmpty()) return 0
        val keys = folders.flatMap { history(it).keys }.toSortedSet()
        val first = keys.firstOrNull() ?: return 0
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val day = Calendar.getInstance().apply { time = fmt.parse(first) ?: return 0 }
        val end = dayKey(Calendar.getInstance())
        var best = 0; var run = 0
        while (dayKey(day) <= end) {
            if (completeOn(folders, day)) { run++; best = maxOf(best, run) } else run = 0
            day.add(Calendar.DAY_OF_YEAR, 1)
        }
        return best
    }
}
