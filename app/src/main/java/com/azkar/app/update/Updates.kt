package com.azkar.app.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds out whether a newer version of the app is on GitHub. This is the app's only use of the internet: one
 * request to GitHub's public API, at most twice a day (only when the app is opened) and whenever "Check now" is
 * tapped. It sends nothing about the user; Settings can turn the automatic check off.
 */
object Updates {
    /** GitHub's newest published release of this app (drafts and pre-releases are never "latest"). */
    private const val LATEST = "https://api.github.com/repos/shnefix/Adhkar/releases/latest"
    private const val HOUR = 60 * 60 * 1000L
    /** An answer from GitHub is used for this long before asking again. */
    private const val CHECK_EVERY = 12 * HOUR
    /** After "Later", the same version is offered again a day later. */
    private const val LATER_FOR = 24 * HOUR

    private const val K_AUTO = "auto_check"
    private const val K_CHECKED = "checked_at"
    private const val K_RELEASE = "latest_release"
    private const val K_LATER_VERSION = "later_version"
    private const val K_LATER_UNTIL = "later_until"

    sealed interface Result {
        data class Available(val release: Release) : Result
        data object UpToDate : Result
        /** GitHub couldn't be reached (no connection, or it refused). */
        data object Failed : Result
    }

    /** Its own file, so backups and exports of the app's settings never carry it. */
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("azkar_updates", Context.MODE_PRIVATE)

    fun installedVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** On by default: check when the app is opened. */
    fun autoCheck(ctx: Context): Boolean = prefs(ctx).getBoolean(K_AUTO, true)
    fun setAutoCheck(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(K_AUTO, on).apply()

    /**
     * Whether a newer version is out. Asks GitHub when [force] (Check now) or when its last answer is older than
     * [CHECK_EVERY]; otherwise goes by that answer. Failed only when [force]: the automatic check just falls back
     * on what it knew. Runs on the IO dispatcher.
     */
    suspend fun check(ctx: Context, force: Boolean = false): Result = withContext(Dispatchers.IO) {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        var release = p.getString(K_RELEASE, null)?.let(Release::fromJson)
        if (force || now - p.getLong(K_CHECKED, 0L) !in 0..CHECK_EVERY) {
            try {
                release = fetch()
                p.edit().putString(K_RELEASE, release?.toJson()).putLong(K_CHECKED, now).apply()
            } catch (e: Exception) {
                android.util.Log.w("AzkarUpdate", "Update check failed", e)
                if (force) return@withContext Result.Failed
            }
        }
        val latest = release
        if (latest != null && Versions.isNewer(latest.version, installedVersion(ctx))) Result.Available(latest) else Result.UpToDate
    }

    /** True while [release] is put off: "Later" was tapped for it less than a day ago. */
    fun postponed(ctx: Context, release: Release): Boolean {
        val p = prefs(ctx)
        return p.getString(K_LATER_VERSION, null) == release.version && System.currentTimeMillis() < p.getLong(K_LATER_UNTIL, 0L)
    }

    fun later(ctx: Context, release: Release) {
        prefs(ctx).edit()
            .putString(K_LATER_VERSION, release.version)
            .putLong(K_LATER_UNTIL, System.currentTimeMillis() + LATER_FOR)
            .apply()
    }

    /** GitHub's latest release, null when none can be offered; throws when GitHub can't be reached. */
    private fun fetch(): Release? {
        val c = URL(LATEST).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Adhkar-Android")   // GitHub's API requires one
        try {
            return when (val code = c.responseCode) {
                HttpURLConnection.HTTP_OK -> Release.fromGitHub(c.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null   // nothing published
                else -> throw IOException("GitHub answered $code")
            }
        } finally {
            c.disconnect()
        }
    }
}
