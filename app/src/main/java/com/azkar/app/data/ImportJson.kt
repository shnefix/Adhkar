package com.azkar.app.data

import android.content.Context
import com.azkar.app.R
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses and validates an Azkar JSON file (hand-written or produced by Export).
 * Only "folders" is required. Optional: folder "id"/"icon", zikr "note"/"source"/"sourceDetail"/"narrator"/"when"/"grade"/"title"/"titlePosition" ("above", "below", "both" or "hidden"),
 * top-level "unfiled", "reminders", "settings".
 */
object ImportJson {
    data class Item(val text: String, val count: Int, val note: String = "", val title: String = "", val titlePos: TitlePos = TitlePos.ABOVE, val source: ZikrSource = ZikrSource())
    data class FolderIn(val name: String, val items: List<Item>, val id: String? = null, val icon: String? = null, val remember: Boolean = false, val home: Boolean? = null)
    data class ReminderIn(val id: String, val title: String, val message: String, val hour: Int, val minute: Int, val enabled: Boolean, val folder: String?)
    data class SettingsIn(
        val arabicSize: Int?, val notificationsOn: Boolean?, val arabicSp: Int? = null,
        val prayerDelays: Map<String, Int> = emptyMap(), val nudgeOn: Boolean? = null, val nudgeMinutes: Int? = null,
        val calcMethod: String? = null, val asrHanafi: Boolean? = null,
        /** Reminder id -> prayer rule, and folder id -> start-over rule (ids as in the file). */
        val reminderRules: Map<String, String> = emptyMap(), val resetRules: Map<String, String> = emptyMap()
    )
    /** Tracked folder ids and their daily history, as written by Export (ids are the file's own). */
    data class TrackingIn(val folders: List<String>, val history: Map<String, Map<String, Tracking.Progress>>)
    sealed class Parsed {
        data class Ok(
            val folders: List<FolderIn>,
            val unfiled: List<Item> = emptyList(),
            val reminders: List<ReminderIn> = emptyList(),
            val settings: SettingsIn? = null,
            val tracking: TrackingIn? = null
        ) : Parsed()
        /** A string resource and its arguments; an argument that is itself an Error (where it happened) is resolved first. */
        data class Error(val res: Int, val args: List<Any> = emptyList()) : Parsed() {
            fun message(ctx: Context): String =
                ctx.getString(res, *args.map { if (it is Error) it.message(ctx) else it }.toTypedArray())
        }
    }

    private val TIME = Regex("^(\\d{1,2}):(\\d{2})$")
    private val DAY = Regex("^\\d{8}$")   // tracking history keys: yyyyMMdd

    private fun items(az: org.json.JSONArray, where: Parsed.Error): Any {
        val out = ArrayList<Item>()
        for (j in 0 until az.length()) {
            val o = az.optJSONObject(j) ?: return Parsed.Error(R.string.import_err_zikr_object, listOf(where, j + 1))
            val text = if (o.isNull("text")) "" else o.optString("text", "")
            if (text.isBlank()) return Parsed.Error(R.string.import_err_zikr_text, listOf(where, j + 1))
            val n = when (val c = o.opt("count")) {
                null, JSONObject.NULL -> 1
                is Int -> c
                // Any other number (Long, Double, or BigDecimal from some org.json versions): whole and in range.
                is Number -> c.toDouble().let { d -> if (d % 1.0 == 0.0 && d in 1.0..999.0) d.toInt() else -1 }
                is String -> c.trim().toIntOrNull() ?: -1
                else -> -1
            }
            if (n < 1 || n > 999) return Parsed.Error(R.string.import_err_zikr_count, listOf(where, j + 1))
            val note = if (o.isNull("note")) "" else o.optString("note", "")
            val title = if (o.isNull("title")) "" else o.optString("title", "").trim()
            val pos = TitlePos.ofKey(o.optString("titlePosition", "above"))
            fun str(k: String) = if (o.isNull(k)) "" else o.optString(k, "").trim()
            val source = ZikrSource(str("source"), str("sourceDetail"), str("narrator"), str("when"), str("grade"))
            out.add(Item(text, n, note, title, pos, source))
        }
        return out
    }

    private fun stringMap(o: JSONObject?): Map<String, String> =
        o?.keys()?.asSequence()?.mapNotNull { k -> (o.opt(k) as? String)?.let { k to it } }?.toMap().orEmpty()

    @Suppress("UNCHECKED_CAST")
    fun parse(raw: String): Parsed {
        val s = raw.removePrefix("\uFEFF").trim()
        if (s.isEmpty()) return Parsed.Error(R.string.import_err_empty)
        if (s.startsWith("[")) return Parsed.Error(R.string.import_err_bare_list)
        val root = try { JSONObject(s) } catch (e: JSONException) {
            return Parsed.Error(R.string.import_err_invalid_json, listOf((e.message ?: "").take(90)))
        }
        val arr = root.optJSONArray("folders") ?: return Parsed.Error(
            if (root.has("folders")) R.string.import_err_folders_not_list else R.string.import_err_no_folders
        )
        val folders = ArrayList<FolderIn>()
        for (i in 0 until arr.length()) {
            val at = Parsed.Error(R.string.import_where_folder_n, listOf(i + 1))
            val f = arr.optJSONObject(i) ?: return Parsed.Error(R.string.import_err_expected_object, listOf(at))
            val name = if (f.isNull("name")) "" else f.optString("name", "").trim()
            if (name.isEmpty()) return Parsed.Error(R.string.import_err_missing_name, listOf(at))
            val named = Parsed.Error(R.string.import_where_folder, listOf(name))
            val az = f.optJSONArray("azkar") ?: return Parsed.Error(R.string.import_err_missing_azkar, listOf(named))
            val r = items(az, named)
            if (r is Parsed.Error) return r
            val id = if (f.isNull("id")) null else (f.opt("id") as? String)?.trim()?.ifEmpty { null }
            val icon = (if (f.isNull("icon")) null else (f.opt("icon") as? String)?.trim()?.ifEmpty { null })
                ?.takeUnless { EmojiRules.isRestricted(it) }   // people/animal emojis are dropped
            if (id != null && folders.any { it.id == id }) {
                return Parsed.Error(R.string.import_err_duplicate_id, listOf(named, id))
            }
            folders.add(FolderIn(name, r as List<Item>, id, icon, f.optBoolean("remember", false), if (f.opt("home") is Boolean) f.getBoolean("home") else null))
        }
        var unfiled: List<Item> = emptyList()
        root.optJSONArray("unfiled")?.let {
            val r = items(it, Parsed.Error(R.string.import_where_unfiled))
            if (r is Parsed.Error) return r
            unfiled = r as List<Item>
        }
        val rems = ArrayList<ReminderIn>()
        root.optJSONArray("reminders")?.let { ra ->
            for (i in 0 until ra.length()) {
                val at = Parsed.Error(R.string.import_where_reminder_n, listOf(i + 1))
                val o = ra.optJSONObject(i) ?: return Parsed.Error(R.string.import_err_expected_object, listOf(at))
                val title = if (o.isNull("title")) "" else o.optString("title", "").trim()
                if (title.isEmpty()) return Parsed.Error(R.string.import_err_missing_title, listOf(at))
                val m = TIME.matchEntire(if (o.isNull("time")) "" else o.optString("time", "").trim())
                val h = m?.groupValues?.get(1)?.toInt() ?: -1
                val mi = m?.groupValues?.get(2)?.toInt() ?: -1
                if (h !in 0..23 || mi !in 0..59) {
                    return Parsed.Error(R.string.import_err_time, listOf(Parsed.Error(R.string.import_where_reminder, listOf(title))))
                }
                val msg = if (o.isNull("message")) "" else o.optString("message", "")
                val id = if (o.isNull("id")) "" else o.optString("id", "").trim()
                val folder = if (o.isNull("folder")) null else (o.opt("folder") as? String)?.trim()?.ifEmpty { null }
                // A blank message shows the default text, in the phone's language, when the reminder fires.
                rems.add(ReminderIn(id, title, msg.trim(), h, mi, o.optBoolean("enabled", true), folder))
            }
        }
        val st = root.optJSONObject("settings")?.let { so ->
            SettingsIn(
                (so.opt("arabicSize") as? Number)?.toInt()?.takeIf { it in 0..2 },
                if (so.opt("notificationsEnabled") is Boolean) so.getBoolean("notificationsEnabled") else null,
                (so.opt("arabicSp") as? Number)?.toInt()?.takeIf { it in ReaderPrefs.FONT_MIN..ReaderPrefs.FONT_MAX },
                so.optJSONObject("prayerDelays")?.let { d ->
                    d.keys().asSequence().mapNotNull { k -> (d.opt(k) as? Number)?.toInt()?.takeIf { it in 0..AppPrefs.SESSION_DELAY_MAX }?.let { k to it } }.toMap()
                }.orEmpty(),
                if (so.opt("nudgeEnabled") is Boolean) so.getBoolean("nudgeEnabled") else null,
                (so.opt("nudgeMinutes") as? Number)?.toInt()?.takeIf { it in 1..AppPrefs.SESSION_DELAY_MAX },
                (so.opt("calcMethod") as? String)?.takeIf { it.isNotBlank() },
                if (so.opt("asrHanafi") is Boolean) so.getBoolean("asrHanafi") else null,
                stringMap(so.optJSONObject("reminderRules")),
                stringMap(so.optJSONObject("resetRules"))
            )
        }
        // Tracking is optional and never fails an import: unreadable entries are skipped.
        val tracking = root.optJSONObject("tracking")?.let { t ->
            val ids = t.optJSONArray("folders")?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? String } }.orEmpty()
            val hist = t.optJSONObject("history")?.let { h ->
                h.keys().asSequence().mapNotNull { id ->
                    val days = h.optJSONObject(id) ?: return@mapNotNull null
                    id to days.keys().asSequence().mapNotNull { k ->
                        val parts = days.optString(k).split('/')
                        val d = parts.getOrNull(0)?.toIntOrNull(); val tot = parts.getOrNull(1)?.toIntOrNull()
                        if (d == null || tot == null || tot <= 0 || !DAY.matches(k)) null else k to Tracking.Progress(d.coerceIn(0, tot), tot)
                    }.toMap()
                }.toMap()
            }.orEmpty()
            TrackingIn(ids, hist).takeIf { ids.isNotEmpty() || hist.isNotEmpty() }
        }
        if (folders.sumOf { it.items.size } + unfiled.size == 0 && rems.isEmpty()) {
            return Parsed.Error(if (folders.isEmpty()) R.string.import_err_folders_empty else R.string.import_err_no_azkar)
        }
        return Parsed.Ok(folders, unfiled, rems, st, tracking)
    }
}