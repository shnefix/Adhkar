package com.azkar.app.ui

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.azkar.app.ThemeMode
import com.azkar.app.R
import com.azkar.app.data.Schedule
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.Store
import com.azkar.app.data.Tracking
import com.azkar.app.reminders.Cities
import com.azkar.app.reminders.LocationUpdater
import com.azkar.app.reminders.PrayerSessions
import com.azkar.app.reminders.ReminderScheduler
import com.azkar.app.widgets.WidgetUpdater

/** [h]:[m] in the phone's own clock format (12 / 24 hour, localized AM / PM and digits). */
fun timeLabel(ctx: Context, h: Int, m: Int): String {
    val c = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, m) }
    return DateFormat.getTimeFormat(ctx).format(c.time)
}

/** The prayer a reminder follows, with its labels and the delays offered after it. */
private enum class PrayerAnchor(val atRes: Int, val afterRes: Int, val titleRes: Int, val delays: List<Int>) {
    FAJR(R.string.reminder_at_fajr, R.string.reminder_after_fajr, R.string.reminder_delay_title_fajr, listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180)),
    ASR(R.string.reminder_at_asr, R.string.reminder_after_asr, R.string.reminder_delay_title_asr, listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180)),
    ISHA(R.string.reminder_at_isha, R.string.reminder_after_isha, R.string.reminder_delay_title_isha, listOf(0, 30, 60, 90, 120, 150, 180, 240, 300));

    companion object {
        fun of(scheduleId: String) = when (scheduleId) {
            PrayerSessions.Session.MORNING.scheduleId -> FAJR
            PrayerSessions.Session.EVENING.scheduleId -> ASR
            PrayerSessions.SLEEP_SCHEDULE -> ISHA
            else -> null
        }
    }
}

/** "10 min after Fajr", or "At Fajr" for no delay. */
private fun delayLabel(ctx: Context, anchor: PrayerAnchor, minutes: Int): String =
    if (minutes == 0) ctx.getString(anchor.atRes) else ctx.getString(anchor.afterRes, minutes)

/**
 * "10 min after Fajr · 4:52 AM" for a reminder that follows prayer times, or null when it uses its
 * fixed time (another reminder, or no location yet). [delayMinutes] previews an unsaved choice.
 */
fun prayerTimeLabel(
    ctx: Context, s: Schedule,
    delayMinutes: Int = AppPrefs.sessionDelayMinutes(s.id)
): String? {
    // Any reminder the user set around a prayer: "10 min after Dhuhr · 12:48", or a hint until a location is set.
    AppPrefs.reminderRule(s.id)?.let { rule ->
        val label = timeRuleLabel(ctx, rule)
        val at = PrayerSessions.nextPrayerReminder(s.id, System.currentTimeMillis())
            ?: return ctx.getString(R.string.reminder_needs_location, label)
        val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
        return label + " · " + timeLabel(ctx, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }
    val anchor = PrayerAnchor.of(s.id) ?: return null
    val at = PrayerSessions.nextPrayerReminder(s.id, System.currentTimeMillis(), delayMinutes * 60_000L) ?: return null
    val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
    return delayLabel(ctx, anchor, delayMinutes) + " · " +
        timeLabel(ctx, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

/** Lead times offered for the "not finished yet" reminder, in minutes before Dhuhr / Maghrib. */
private val NUDGE_LEADS = listOf(15, 30, 45, 60, 90)

class RemindersFragment : Fragment(R.layout.fragment_reminders) {

    private var onPermissionResult: ((Boolean) -> Unit)? = null
    private var syncing = false

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val callback = onPermissionResult
        onPermissionResult = null
        if (!granted) showPermissionDenied()
        callback?.invoke(granted)
    }

    /** The runtime permission (Android 13+) and the app's notification switch in system settings. */
    private fun hasNotificationPermission(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()

    private fun requestNotifications(callback: (Boolean) -> Unit) {
        if (hasNotificationPermission()) {
            callback(true)
            return
        }
        val askable = Build.VERSION.SDK_INT >= 33 &&
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            // Granted, but switched off in system settings (any Android version): only Settings can turn it back on.
            !askable -> { showBlockedDialog(); callback(false) }
            // Denied once before: explain why, then ask again.
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.notif_rationale_title)
                    .setMessage(R.string.notif_rationale_message)
                    .setPositiveButton(R.string.allow) { _, _ ->
                        onPermissionResult = callback
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setNegativeButton(R.string.not_now) { _, _ -> callback(false) }
                    .setOnCancelListener { callback(false) }
                    .show()
            else -> {
                onPermissionResult = callback
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * After a refusal: a first "no" gets a short note; once Android stops showing the prompt ("don't ask again"),
     * the only way back is the app's notification settings, so offer to open them.
     */
    private fun showPermissionDenied() {
        if (Build.VERSION.SDK_INT >= 33 && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            showBlockedDialog()
        } else {
            Toast.makeText(requireContext(), R.string.notif_denied_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBlockedDialog() {
        val ctx = requireContext()
        AlertDialog.Builder(ctx)
            .setTitle(R.string.notif_denied_title)
            .setMessage(R.string.notif_denied_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val settings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                try {
                    startActivity(settings)
                } catch (e: android.content.ActivityNotFoundException) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                }
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }

        view.findViewById<ToggleSwitch>(R.id.sw_all).apply {
            on = AppPrefs.notificationsOn() && hasNotificationPermission()
            onToggle = { v ->
                if (!syncing) {
                    if (v) requestNotifications { granted ->
                        if (granted) { AppPrefs.setNotificationsOn(true); ReminderScheduler.rescheduleAll(ctx) }
                        renderList()
                    } else {
                        AppPrefs.setNotificationsOn(false)
                        ReminderScheduler.rescheduleAll(ctx)
                        renderList()
                    }
                }
            }
        }
        view.findViewById<View>(R.id.btn_add_rem).setOnClickListener { edit(null) }
        view.findViewById<View>(R.id.btn_test_notification).setOnClickListener {
            requestNotifications { granted ->
                if (!granted) return@requestNotifications
                val base = Store.schedules().firstOrNull() ?: Schedule("test", "", "", 8, 0, true, null)
                ReminderScheduler.notify(ctx, base.copy(
                    title = getString(R.string.test_notification),
                    message = getString(R.string.test_notification_message)))
                Toast.makeText(ctx, R.string.test_notification_sent, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<View>(R.id.exact_row).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31) {
                // Some devices ship without this settings screen.
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                } catch (e: android.content.ActivityNotFoundException) {
                    android.util.Log.w("Azkar", "No exact alarm settings screen", e)
                }
            }
        }

        // Tracking: off by default; covers the morning and evening folders (other folders opt in from Edit folder).
        val swTrack = view.findViewById<ToggleSwitch>(R.id.sw_track)
        val trackSub = view.findViewById<TextView>(R.id.track_sub)
        // "Not finished yet": only offered while morning / evening are tracked; tap the text to change how early.
        val nudgeRow = view.findViewById<View>(R.id.nudge_row)
        val swNudge = view.findViewById<ToggleSwitch>(R.id.sw_nudge)
        val nudgeSub = view.findViewById<TextView>(R.id.nudge_sub)
        fun renderTracking() {
            val folders = sessionFolders()
            swTrack.on = folders.isNotEmpty() && folders.all { Tracking.isTracked(it) }
            trackSub.text = trackingSummary(ctx, folders, swTrack.on)
            nudgeRow.visibility = if (swTrack.on) View.VISIBLE else View.GONE
            swNudge.on = AppPrefs.nudgeOn()
            nudgeSub.text = if (swNudge.on) getString(R.string.nudge_setting_summary, AppPrefs.nudgeLeadMinutes())
            else getString(R.string.nudge_setting_off)
        }
        renderTracking()
        swTrack.onToggle = { on -> setTracking(ctx, sessionFolders(), on); renderTracking() }
        swNudge.onToggle = { on ->
            AppPrefs.setNudgeOn(on)
            ReminderScheduler.scheduleNudges(ctx)
            renderTracking()
        }
        view.findViewById<View>(R.id.nudge_text).setOnClickListener {
            val current = AppPrefs.nudgeLeadMinutes()
            val choices = (NUDGE_LEADS + current).distinct().sorted()
            ChoiceSheet.showNudgeLead(ctx, choices) { m ->
                AppPrefs.setNudgeLeadMinutes(m)
                ReminderScheduler.scheduleNudges(ctx)
                renderTracking()
            }
        }

        renderList()
    }

    override fun onResume() {
        super.onResume()
        if (view != null) {
            ReminderScheduler.rescheduleAll(requireContext())   // picks up a newly granted exact-alarm permission
            // Notifications may have been switched on or off in system settings meanwhile.
            syncing = true
            view?.findViewById<ToggleSwitch>(R.id.sw_all)?.on = AppPrefs.notificationsOn() && hasNotificationPermission()
            syncing = false
            renderList()
        }
    }

    private fun renderList() {
        val root = view ?: return
        val ctx = requireContext()
        val list = root.findViewById<LinearLayout>(R.id.reminders)
        list.removeAllViews()
        val all = Store.schedules()
        val globalOn = AppPrefs.notificationsOn()
        list.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.rem_empty).visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        root.findViewById<TextView>(R.id.notif_sub).text =
            when {
                !hasNotificationPermission() -> getString(R.string.notif_blocked_sub)
                !globalOn -> getString(R.string.reminders_paused)
                else -> getString(R.string.reminders_on_count, all.count { it.enabled }, all.size)
            }

        all.forEachIndexed { idx, s ->
            val v = layoutInflater.inflate(R.layout.item_reminder, list, false)
            v.findViewById<View>(R.id.divider).setBackgroundColor(if (idx == 0) 0 else ctx.getColor(R.color.border))
            v.findViewById<TextView>(R.id.title).text = s.title
            v.findViewById<TextView>(R.id.time).text = prayerTimeLabel(ctx, s) ?: getString(R.string.reminder_time_daily, timeLabel(ctx, s.hour, s.minute))
            v.findViewById<View>(R.id.row).apply {
                alpha = if (globalOn) 1f else 0.5f
                setOnClickListener { edit(s) }
            }
            v.findViewById<ToggleSwitch>(R.id.sw).apply {
                contentDescription = getString(R.string.reminder_switch_description, s.title)
                on = s.enabled
                onToggle = { isOn ->
                    if (isOn) requestNotifications { granted ->
                        if (granted) {
                            val u = s.copy(enabled = true)
                            Store.upsertSchedule(u); ReminderScheduler.apply(ctx, u)
                        }
                        renderList()   // denied: switch goes back to OFF
                    } else {
                        val u = s.copy(enabled = false)
                        Store.upsertSchedule(u); ReminderScheduler.apply(ctx, u)
                        renderList()
                    }
                }
            }
            list.addView(v)
        }

        val needExact = Build.VERSION.SDK_INT >= 31 &&
            !(ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        root.findViewById<View>(R.id.exact_row).visibility =
            if (needExact && globalOn && all.any { it.enabled }) View.VISIBLE else View.GONE
    }

    private fun edit(existing: Schedule?) {
        val ctx = requireContext()
        val v = layoutInflater.inflate(R.layout.dialog_reminder, null)
        val title = v.findViewById<EditText>(R.id.r_title)
        val msg = v.findViewById<EditText>(R.id.r_msg)
        val timeTv = v.findViewById<TextView>(R.id.r_time)
        var h = existing?.hour ?: 8
        var m = existing?.minute ?: 0
        title.setText(existing?.title ?: "")
        msg.setText(existing?.message ?: "")
        // Morning / evening / before sleep follow the prayer times while a location is known: instead of a clock
        // time, the user picks how many minutes after Fajr / Asr / Isha they come (saved with the reminder).
        val anchor = existing?.let { PrayerAnchor.of(it.id) }
        var delay = existing?.let { AppPrefs.sessionDelayMinutes(it.id) } ?: AppPrefs.SESSION_DELAY_DEFAULT
        fun prayerLabel() = existing?.takeIf { anchor != null }?.let { prayerTimeLabel(ctx, it, delay) }
        // Other reminders come at a clock time, or around a prayer (e.g. 10 min after Dhuhr) once a location is set.
        var rule = if (anchor == null) existing?.let { AppPrefs.reminderRule(it.id) } else null
        fun showTime() {
            val r = rule
            timeTv.text = when {
                r != null -> {
                    val label = timeRuleLabel(ctx, r)
                    val loc = AppPrefs.location()
                    if (loc == null) getString(R.string.reminder_needs_location, label)
                    else {
                        val c = java.util.Calendar.getInstance().apply {
                            timeInMillis = com.azkar.app.reminders.PrayerClock.next(r, System.currentTimeMillis(), loc)
                        }
                        label + " · " + timeLabel(ctx, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
                    }
                }
                else -> prayerLabel() ?: timeLabel(ctx, h, m)
            }
        }
        showTime()
        fun pickClock() = TimePickerDialog(ctx, { _, hh, mm -> h = hh; m = mm; rule = null; showTime() }, h, m, DateFormat.is24HourFormat(ctx)).show()
        timeTv.setOnClickListener {
            if (anchor == null) {
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.reminder_when)
                    .setItems(arrayOf(getString(R.string.reminder_at_clock), getString(R.string.reminder_around_prayer))) { _, which ->
                        if (which == 0) pickClock()
                        else pickTimeRule(R.string.reminder_rule_title, rule, allowNever = false, allowMidnight = false) {
                            rule = it
                            showTime()
                        }
                    }
                    .show()
            } else if (prayerLabel() != null) {
                val choices = (anchor.delays + delay).distinct().sorted()
                AlertDialog.Builder(ctx)
                    .setTitle(anchor.titleRes)
                    .setSingleChoiceItems(choices.map { delayLabel(ctx, anchor, it) }.toTypedArray(), choices.indexOf(delay)) { d, which ->
                        delay = choices[which]
                        showTime()
                        d.dismiss()
                    }
                    .show()
            } else pickClock()
        }

        var folder: String? = existing?.open?.takeIf { Store.folder(it) != null }   // a deleted folder just becomes "open the app"
        val folderTv = v.findViewById<TextView>(R.id.r_folder)
        fun folderLabel(f: com.azkar.app.data.Folder) = (f.icon?.let { "$it " } ?: "") + f.name
        fun showFolder() { folderTv.text = Store.folder(folder ?: "")?.let { folderLabel(it) } ?: getString(R.string.just_open_app) }
        showFolder()
        folderTv.setOnClickListener {
            val fs = Store.readableFolders()   // a parent folder has nothing to open
            val names = arrayOf(getString(R.string.just_open_app)) + fs.map { folderLabel(it) }
            AlertDialog.Builder(ctx).setTitle(R.string.open_folder)
                .setItems(names) { _, which -> folder = if (which == 0) null else fs[which - 1].id; showFolder() }
                .show()
        }

        val b = AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) R.string.new_reminder else R.string.edit_reminder)
            .setView(v)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (existing != null) b.setNeutralButton(R.string.delete) { _, _ ->
            ReminderScheduler.cancel(ctx, existing.id)   // cancel the alarm before forgetting it
            Store.deleteSchedule(existing.id)   // also forgets its prayer rule
            renderList()
        }
        val dlg = b.create()
        dlg.setOnShowListener {
            dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val t = title.text.toString().trim()
                if (t.isEmpty()) { title.error = getString(R.string.enter_title); return@setOnClickListener }
                val text = msg.text.toString().trim().ifEmpty { getString(R.string.reminder_default_message) }
                val s = (existing ?: Schedule(Store.newScheduleId(), t, text, h, m, true, null))
                    .copy(title = t, message = text, hour = h, minute = m, open = folder)
                if (anchor != null) {
                    AppPrefs.setSessionDelayMinutes(s.id, delay)
                    WidgetUpdater.updateAll(ctx)   // countdown / today widgets show the new start time
                } else AppPrefs.setReminderRule(s.id, rule)   // before scheduling: it decides the alarm time
                Store.upsertSchedule(s)
                ReminderScheduler.cancel(ctx, s.id)   // drop the old alarm, then set the new time
                ReminderScheduler.apply(ctx, s)
                dlg.dismiss()
                if (s.enabled) requestNotifications { ok ->
                    if (ok) ReminderScheduler.apply(ctx, s) else Store.upsertSchedule(s.copy(enabled = false))
                    renderList()
                } else renderList()
            }
        }
        dlg.show()
    }
}