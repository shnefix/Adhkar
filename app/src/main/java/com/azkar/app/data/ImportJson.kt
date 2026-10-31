package com.azkar.app.data

import org.json.JSONException
import org.json.JSONObject

/**
 * Parses and validates an Azkar JSON file (hand-written or produced by Export).
 * Only "folders" is required. Optional: folder "id"/"icon", zikr "note", top-level "unfiled", "reminders", "settings".
 */
object ImportJson {
    data class Item(val text: String, val count: Int, val note: String = "")
    data class FolderIn(val name: String, val items: List<Item>, val id: String? = null, val icon: String? = null)
    data class ReminderIn(val id: String, val title: String, val message: String, val hour: Int, val minute: Int, val enabled: Boolean, val folder: String?)
    data class SettingsIn(val arabicSize: Int?, val notificationsOn: Boolean?)
    sealed class Parsed {
        data class Ok(
            val folders: List<FolderIn>,
            val unfiled: List<Item> = emptyList(),
            val reminders: List<ReminderIn> = emptyList(),
            val settings: SettingsIn? = null
        ) : Parsed()
        data class Error(val message: String) : Parsed()
    }

    private val TIME = Regex("^(\\d{1,2}):(\\d{2})$")

    private fun items(az: org.json.JSONArray, where: String): Any {
        val out = ArrayList<Item>()
        for (j in 0 until az.length()) {
            val o = az.optJSONObject(j) ?: return "$where, zikr ${j + 1}: expected an object."
            val text = if (o.isNull("text")) "" else o.optString("text", "")
            if (text.isBlank()) return "$where, zikr ${j + 1}: missing \"text\"."
            val n = when (val c = o.opt("count")) {
                null, JSONObject.NULL -> 1
                is Int -> c
                is Long -> if (c in 1..999) c.toInt() else -1
                is Double -> if (c % 1.0 == 0.0) c.toInt() else -1
                is String -> c.trim().toIntOrNull() ?: -1
                else -> -1
            }
            if (n < 1 || n > 999) return "$where, zikr ${j + 1}: \"count\" must be a whole number from 1 to 999."
            val note = if (o.isNull("note")) "" else o.optString("note", "")
            out.add(Item(text, n, note))
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    fun parse(raw: String): Parsed {
        val s = raw.removePrefix("\uFEFF").trim()
        if (s.isEmpty()) return Parsed.Error("The file is empty.")
        val root = try { JSONObject(s) } catch (e: JSONException) {
            return Parsed.Error("This is not valid JSON. " + (e.message ?: "").take(90))
        }
        val arr = root.optJSONArray("folders") ?: return Parsed.Error("The file needs a top-level \"folders\" list.")
        val folders = ArrayList<FolderIn>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: return Parsed.Error("Folder ${i + 1}: expected an object.")
            val name = if (f.isNull("name")) "" else f.optString("name", "").trim()
            if (name.isEmpty()) return Parsed.Error("Folder ${i + 1}: missing \"name\".")
            val az = f.optJSONArray("azkar") ?: return Parsed.Error("Folder \"$name\": missing an \"azkar\" list.")
            val r = items(az, "Folder \"$name\"")
            if (r is String) return Parsed.Error(r)
            val id = if (f.isNull("id")) null else (f.opt("id") as? String)?.trim()?.ifEmpty { null }
            val icon = if (f.isNull("icon")) null else (f.opt("icon") as? String)?.trim()?.ifEmpty { null }
            folders.add(FolderIn(name, r as List<Item>, id, icon))
        }
        var unfiled: List<Item> = emptyList()
        root.optJSONArray("unfiled")?.let {
            val r = items(it, "Unfiled azkar")
            if (r is String) return Parsed.Error(r)
            unfiled = r as List<Item>
        }
        val rems = ArrayList<ReminderIn>()
        root.optJSONArray("reminders")?.let { ra ->
            for (i in 0 until ra.length()) {
                val o = ra.optJSONObject(i) ?: return Parsed.Error("Reminder ${i + 1}: expected an object.")
                val title = if (o.isNull("title")) "" else o.optString("title", "").trim()
                if (title.isEmpty()) return Parsed.Error("Reminder ${i + 1}: missing \"title\".")
                val m = TIME.matchEntire(if (o.isNull("time")) "" else o.optString("time", "").trim())
                val h = m?.groupValues?.get(1)?.toInt() ?: -1
                val mi = m?.groupValues?.get(2)?.toInt() ?: -1
                if (h !in 0..23 || mi !in 0..59) return Parsed.Error("Reminder \"$title\": \"time\" must look like 06:30.")
                val msg = if (o.isNull("message")) "" else o.optString("message", "")
                val id = if (o.isNull("id")) "" else o.optString("id", "").trim()
                val folder = if (o.isNull("folder")) null else (o.opt("folder") as? String)?.trim()?.ifEmpty { null }
                rems.add(ReminderIn(id, title, msg.ifBlank { "Time for your azkar · tap to read" }, h, mi, o.optBoolean("enabled", true), folder))
            }
        }
        val st = root.optJSONObject("settings")?.let { so ->
            SettingsIn(
                (so.opt("arabicSize") as? Number)?.toInt()?.takeIf { it in 0..2 },
                if (so.opt("notificationsEnabled") is Boolean) so.getBoolean("notificationsEnabled") else null
            )
        }
        if (folders.sumOf { it.items.size } + unfiled.size == 0 && rems.isEmpty()) {
            return Parsed.Error(if (folders.isEmpty()) "The \"folders\" list is empty." else "The file contains no azkar.")
        }
        return Parsed.Ok(folders, unfiled, rems, st)
    }
}