package com.azkar.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.azkar.app.R
import com.azkar.app.update.Release
import com.azkar.app.update.ReleaseNotes
import com.azkar.app.update.UpdateDownload
import com.azkar.app.update.Updates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

/** "A new version is out: Install / Later", the download, and Android's installer. */
object UpdatePrompt {

    /** The check when the app opens (if it's on in Settings): offers a newer version unless it was put off. */
    fun checkOnStart(activity: AppCompatActivity) {
        if (!Updates.autoCheck(activity)) return
        activity.lifecycleScope.launch {
            when (val result = Updates.check(activity)) {
                is Updates.Result.Available ->
                    if (!Updates.postponed(activity, result.release)) activity.withResumed { offer(activity, result.release) }
                // Up to date, so an APK left from an update that has since been installed can go.
                else -> UpdateDownload.clean(activity)
            }
        }
    }

    /** Settings' "Check now": always asks GitHub, and says so when there's nothing new. */
    fun checkNow(activity: AppCompatActivity) {
        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show()
        activity.lifecycleScope.launch {
            val result = Updates.check(activity, force = true)
            activity.withResumed {
                when (result) {
                    is Updates.Result.Available -> offer(activity, result.release)
                    Updates.Result.UpToDate -> Toast.makeText(activity,
                        activity.getString(R.string.update_up_to_date, Updates.installedVersion(activity)), Toast.LENGTH_LONG).show()
                    Updates.Result.Failed -> Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Asks whether to install [release] now. Later (or closing the dialog) asks again in a day. */
    fun offer(activity: AppCompatActivity, release: Release) {
        val state = UpdateDownload.state.value
        if (state is UpdateDownload.State.Downloading && state.release == release) {
            follow(activity, release)
            return
        }
        val message = buildString {
            append(activity.getString(R.string.update_message, release.version, Updates.installedVersion(activity)))
            val notes = ReleaseNotes.summary(release.notes)
            if (notes.isNotEmpty()) {
                append("\n\n").append(activity.getString(R.string.update_whats_new))
                notes.forEach { append("\n• ").append(it) }
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_install) { _, _ -> install(activity, release) }
            .setNegativeButton(R.string.update_later) { _, _ -> Updates.later(activity, release) }
            .setOnCancelListener { Updates.later(activity, release) }
            .show()
    }

    /** Downloads [release] (unless that's done) and opens Android's installer on it. */
    private fun install(activity: AppCompatActivity, release: Release) {
        UpdateDownload.readyFile(release)?.let { openInstaller(activity, release, it); return }
        UpdateDownload.start(activity, release)
        follow(activity, release)
    }

    /** Shows the download's progress, then opens the installer or says what went wrong. */
    private fun follow(activity: AppCompatActivity, release: Release) {
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val label = TextView(activity, null, 0, R.style.T).apply {
            textSize = 14f
            setTextColor(activity.getColor(R.color.muted))
        }
        val pad = Ui.pxi(activity, 24f)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, Ui.pxi(activity, 12f), pad, 0)
            addView(bar)
            addView(label, Ui.lp(top = 8, ctx = activity))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_downloading, release.version))
            .setView(box)
            .setNegativeButton(R.string.cancel) { _, _ -> UpdateDownload.cancel(activity) }
            .setCancelable(false)
            .show()
        val job = activity.lifecycleScope.launch {
            val end = UpdateDownload.state
                .onEach { s ->
                    if (s is UpdateDownload.State.Downloading) {
                        bar.isIndeterminate = s.percent < 0
                        if (s.percent >= 0) {
                            bar.progress = s.percent
                            label.text = activity.getString(R.string.update_percent, s.percent)
                        }
                    }
                }
                .first { it !is UpdateDownload.State.Downloading }
            activity.withResumed {
                if (dialog.isShowing) dialog.dismiss()
                when (end) {
                    is UpdateDownload.State.Ready -> openInstaller(activity, release, end.file)
                    is UpdateDownload.State.Failed -> failed(activity, end)
                    else -> Unit   // cancelled
                }
            }
        }
        job.invokeOnCompletion { if (dialog.isShowing) dialog.dismiss() }
    }

    private fun failed(activity: AppCompatActivity, failure: UpdateDownload.State.Failed) {
        when (failure.reason) {
            UpdateDownload.Reason.NETWORK -> Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            UpdateDownload.Reason.BAD_FILE -> Toast.makeText(activity, R.string.update_bad_file, Toast.LENGTH_LONG).show()
            UpdateDownload.Reason.OTHER_SIGNATURE -> AlertDialog.Builder(activity)
                .setTitle(R.string.update_other_signature_title)
                .setMessage(R.string.update_other_signature_message)
                .setPositiveButton(R.string.update_open_github) { _, _ -> openPage(activity, failure.release) }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    /**
     * Android's own installer, which asks the user to confirm (and, the first time, to let Adhkar install apps).
     * The APK is shared through the app's FileProvider; nothing else can read the cache.
     */
    private fun openInstaller(activity: AppCompatActivity, release: Release, apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openPage(activity, release)
        }
    }

    private fun openPage(activity: AppCompatActivity, release: Release) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl.ifBlank { release.apkUrl })))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show()
        }
    }
}
