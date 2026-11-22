package com.azkar.app.update

import org.json.JSONObject

/** One published version of the app on GitHub, with the APK that installs it. */
data class Release(
    /** "3.1": the release tag without its leading v. */
    val version: String,
    /** The release text (Markdown). */
    val notes: String,
    val apkUrl: String,
    /** Bytes; 0 when GitHub didn't say. */
    val apkSize: Long,
    /** The release's page on GitHub. */
    val pageUrl: String,
) {
    fun toJson(): String = JSONObject()
        .put("version", version).put("notes", notes).put("apk", apkUrl).put("size", apkSize).put("page", pageUrl)
        .toString()

    companion object {
        fun fromJson(s: String): Release? = runCatching {
            val o = JSONObject(s)
            Release(o.getString("version"), o.optString("notes"), o.getString("apk"), o.optLong("size"), o.optString("page"))
        }.getOrNull()

        /**
         * The release in GitHub's "latest release" answer, or null when there's nothing to offer: a draft or
         * pre-release, a tag that isn't a version number, or no APK (a debug APK is never offered).
         */
        fun fromGitHub(json: String): Release? {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
            val version = o.optString("tag_name").trim().removePrefix("v").removePrefix("V")
            if (Versions.parts(version).isEmpty()) return null
            val assets = o.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull {
                val name = it.optString("name")
                name.endsWith(".apk", ignoreCase = true) && !name.contains("debug", ignoreCase = true)
            } ?: return null
            val url = apk.optString("browser_download_url")
            if (!url.startsWith("https://")) return null
            return Release(version, o.optString("body"), url, apk.optLong("size"), o.optString("html_url"))
        }
    }
}

object Versions {
    /** The numbers in "3.1.2": [3, 1, 2]. Anything after them ("3.1-beta") is ignored; no number at all gives []. */
    fun parts(v: String): List<Int> =
        Regex("""^\d+(\.\d+)*""").find(v.trim())?.value?.split('.')?.mapNotNull { it.toIntOrNull() } ?: emptyList()

    /** True when [candidate] is a later version than [installed]: "3.10" > "3.9", "3.1" > "3.0.9", "3.0" = "3". */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parts(candidate)
        val b = parts(installed)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

object ReleaseNotes {
    /**
     * The release text's first [max] bullet points as plain text for a dialog ("• …"): headings dropped, Markdown
     * marks and link targets removed, a bullet's wrapped lines joined, each cut to [maxChars] characters.
     */
    fun summary(md: String, max: Int = 4, maxChars: Int = 140): List<String> {
        val bullets = mutableListOf<StringBuilder>()
        var inBullet = false
        for (raw in md.lines()) {
            val line = raw.trimEnd()
            val bullet = Regex("""^\s*[-*+]\s+(.*)""").find(line)
            when {
                bullet != null -> { bullets += StringBuilder(bullet.groupValues[1].trim()); inBullet = true }
                inBullet && line.startsWith(" ") && line.isNotBlank() -> bullets.last().append(' ').append(line.trim())
                else -> inBullet = false
            }
        }
        return bullets.take(max).map { b ->
            val text = b.toString()
                .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")   // [text](link) -> text
                .replace(Regex("""\*\*|__|`"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (text.length <= maxChars) text else text.take(maxChars - 1).trimEnd() + "…"
        }
    }
}
