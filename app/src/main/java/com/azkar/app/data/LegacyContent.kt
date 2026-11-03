package com.azkar.app.data

import org.json.JSONArray

/** Pre-Room content as it was stored in SharedPreferences; kept for migration and recovery. */
internal data class LegacyContent(
    val zikrs: List<Zikr>,
    val folders: List<Folder>,
    val schedules: List<Schedule>
) {
    fun isValid(): Boolean =
        zikrs.map { it.id }.all { it.isNotBlank() } &&
            folders.map { it.id }.all { it.isNotBlank() } &&
            schedules.map { it.id }.all { it.isNotBlank() } &&
            zikrs.map { it.id }.distinct().size == zikrs.size &&
            folders.map { it.id }.distinct().size == folders.size &&
            schedules.map { it.id }.distinct().size == schedules.size
}

/** Parsers for the legacy SharedPreferences JSON arrays. */
internal object LegacyJson {
    fun parseZikrs(raw: String): List<Zikr> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Zikr(o.getString("id"), o.getString("ar"), o.optString("note", ""), o.optInt("n", 1), if (o.isNull("f")) null else o.getString("f"))
        }
    }

    fun parseFolders(raw: String): List<Folder> {
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

    fun parseSchedules(raw: String): List<Schedule> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Schedule(
                o.getString("id"), o.optString("t", ""), o.optString("m", ""), o.optInt("h", 8), o.optInt("mi", 0),
                o.optBoolean("on", true), if (o.isNull("open")) null else o.getString("open")
            )
        }
    }

    fun parseIds(raw: String): Set<String> {
        val a = JSONArray(raw)
        return (0 until a.length()).map { a.getString(it) }.toSet()
    }
}
