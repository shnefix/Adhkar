package com.azkar.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Builds a backup file that ImportJson reads back. Read-only: never touches stored data. */
object ExportJson {
    private fun item(z: Zikr) = JSONObject().put("text", z.arabic).put("count", z.repeat).also {
        if (z.note.isNotBlank()) it.put("note", z.note)
        if (z.title.isNotBlank()) it.put("title", z.title).put("titlePosition", if (z.titleBelow) "below" else "above")
    }

    /**
     * [sessionDelays]: minutes after the prayer per reminder id ("m", "e", "s"); [nudgeMinutes] null leaves the
     * "not finished yet" reminder out; [tracking] is [Tracking.toJson] (tracked folders and their history).
     */
    fun build(
        s: ImportMerge.State, arabicSize: Int, notificationsOn: Boolean, arabicSp: Int = ReaderPrefs.FONT_DEFAULT,
        sessionDelays: Map<String, Int> = emptyMap(), nudgeOn: Boolean = true, nudgeMinutes: Int? = null,
        tracking: JSONObject? = null, calcMethod: String? = null, asrHanafi: Boolean = false
    ): String {
        val folders = JSONArray()
        s.folders.forEach { f ->
            val az = JSONArray()
            s.zikrs.filter { it.folderId == f.id }.forEach { az.put(item(it)) }
            val o = JSONObject().put("id", f.id).put("name", f.name)
            f.icon?.let { o.put("icon", it) }
            if (f.remember) o.put("remember", true)
            folders.put(o.put("azkar", az))
        }
        val root = JSONObject().put("app", "azkar").put("version", 1).put("folders", folders)
        val unfiled = JSONArray()
        s.zikrs.filter { it.folderId == null }.forEach { unfiled.put(item(it)) }
        if (unfiled.length() > 0) root.put("unfiled", unfiled)
        val rems = JSONArray()
        s.schedules.forEach {
            val o = JSONObject().put("id", it.id).put("title", it.title).put("message", it.message)
                .put("time", String.format(java.util.Locale.US, "%02d:%02d", it.hour, it.minute))   // ASCII digits on any phone.put("enabled", it.enabled)
            it.open?.let { f -> o.put("folder", f) }
            rems.put(o)
        }
        root.put("reminders", rems)
        val settings = JSONObject().put("arabicSize", arabicSize).put("notificationsEnabled", notificationsOn).put("arabicSp", arabicSp)
        if (sessionDelays.isNotEmpty()) settings.put("prayerDelays", JSONObject().apply { sessionDelays.forEach { (k, v) -> put(k, v) } })
        nudgeMinutes?.let { settings.put("nudgeEnabled", nudgeOn).put("nudgeMinutes", it) }
        calcMethod?.let { settings.put("calcMethod", it).put("asrHanafi", asrHanafi) }
        root.put("settings", settings)
        tracking?.let { root.put("tracking", it) }
        return root.toString(2)
    }
}