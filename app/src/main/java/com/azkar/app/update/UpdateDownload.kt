package com.azkar.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release's APK into the app's cache and checks it before Android's installer is opened on it. It runs
 * apart from any screen, so turning the phone or leaving the app doesn't stop it.
 */
object UpdateDownload {
    sealed interface State {
        data object Idle : State
        /** [percent] is -1 while the size isn't known. */
        data class Downloading(val release: Release, val percent: Int) : State
        data class Ready(val release: Release, val file: File) : State
        data class Failed(val release: Release, val reason: Reason) : State
    }

    enum class Reason {
        /** The download didn't finish (connection lost, GitHub unreachable). */
        NETWORK,
        /** What arrived isn't a newer version of this app. */
        BAD_FILE,
        /** It's signed with another key than the installed copy, so Android won't install it over it. */
        OTHER_SIGNATURE,
    }

    private const val TAG = "AzkarUpdate"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState

    private fun dir(ctx: Context) = File(ctx.cacheDir, "updates")

    /** The checked APK of [release] when it's already downloaded. */
    fun readyFile(release: Release): File? =
        (mutableState.value as? State.Ready)?.takeIf { it.release == release && it.file.exists() }?.file

    /** Downloads [release]; [state] follows it. Does nothing while it's already downloading. */
    fun start(ctx: Context, release: Release) {
        if ((mutableState.value as? State.Downloading)?.release == release) return
        val app = ctx.applicationContext
        job?.cancel()
        mutableState.value = State.Downloading(release, -1)
        job = scope.launch {
            mutableState.value = try {
                verify(app, release, download(app, release))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                State.Failed(release, Reason.NETWORK)
            }
        }
    }

    fun cancel(ctx: Context) {
        job?.cancel()
        job = null
        mutableState.value = State.Idle
        dir(ctx).deleteRecursively()
    }

    /** Removes a downloaded APK once it's no use (e.g. the update is installed). */
    fun clean(ctx: Context) {
        if (mutableState.value !is State.Downloading) dir(ctx).deleteRecursively()
    }

    private suspend fun download(ctx: Context, release: Release): File {
        val dir = dir(ctx).apply { deleteRecursively(); mkdirs() }
        val part = File(dir, "Adhkar-${release.version}.apk.part")
        // GitHub answers with a redirect to its file host; HttpURLConnection follows it (both are https).
        val c = URL(release.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Adhkar-Android")
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Download answered ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            var done = 0L
            var shown = -2
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        currentCoroutineContext().ensureActive()   // Cancel stops it
                        out.write(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else -1
                        if (percent != shown) {
                            shown = percent
                            mutableState.value = State.Downloading(release, percent)
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Got $done of $total bytes")
        } finally {
            c.disconnect()
        }
        val apk = File(dir, "Adhkar-${release.version}.apk")
        if (!part.renameTo(apk)) throw IOException("Could not keep the download")
        return apk
    }

    /** The download as a [State]: Ready only when it's this app, newer, and signed like the installed copy. */
    @Suppress("DEPRECATION")
    private fun verify(ctx: Context, release: Release, apk: File): State {
        if (release.apkSize > 0 && apk.length() != release.apkSize) return State.Failed(release, Reason.BAD_FILE)
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        if (archive == null || archive.packageName != ctx.packageName ||
            PackageInfoCompat.getLongVersionCode(archive) < PackageInfoCompat.getLongVersionCode(installed)) {
            return State.Failed(release, Reason.BAD_FILE)
        }
        val mine = signers(installed)
        val theirs = signers(archive)
        if (mine.isNotEmpty() && theirs.isNotEmpty() && mine.intersect(theirs).isEmpty()) {
            return State.Failed(release, Reason.OTHER_SIGNATURE)
        }
        return State.Ready(release, apk)
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else info.signatures
        return certs.orEmpty().map { it.toCharsString() }.toSet()
    }
}
