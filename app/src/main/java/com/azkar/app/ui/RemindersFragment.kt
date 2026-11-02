package com.azkar.app.ui

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
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
import androidx.fragment.app.Fragment
import com.azkar.app.ThemeMode
import com.azkar.app.R
import com.azkar.app.data.Schedule
import com.azkar.app.data.Store
import com.azkar.app.reminders.ReminderScheduler
import com.azkar.app.widgets.WidgetUpdater

fun timeLabel(ctx: Context, h: Int, m: Int): String =
    if (DateFormat.is24HourFormat(ctx)) "%02d:%02d".format(h, m)
    else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")

class RemindersFragment : Fragment(R.layout.fragment_reminders) {

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(requireContext(), "Notifications are off for Azkar in system settings.", Toast.LENGTH_SHORT).show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }

        view.findViewById<ToggleSwitch>(R.id.sw_all).apply {
            on = Store.notificationsOn()
            onToggle = { v ->
                Store.setNotificationsOn(v)
                if (v) ensureNotificationPermission()
                ReminderScheduler.rescheduleAll(ctx)   // cancels everything when off
                renderList()
            }
        }
        view.findViewById<View>(R.id.btn_add_rem).setOnClickListener { edit(null) }
        view.findViewById<View>(R.id.btn_test_notification).setOnClickListener {
            val sample = Store.schedules().firstOrNull()
            if (sample != null) {
                ReminderScheduler.notify(ctx, sample.copy(title = getString(R.string.test_notification)))
                Toast.makeText(ctx, R.string.test_notification_sent, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<View>(R.id.exact_row).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
            }
        }

// Reading switches
        view.findViewById<ToggleSwitch>(R.id.sw_tap).apply { on = Store.tapAdvance(); onToggle = { Store.setTapAdvance(it) } }
        view.findViewById<ToggleSwitch>(R.id.sw_awake).apply { on = Store.keepScreenOn(); onToggle = { Store.setKeepScreenOn(it) } }

        // Arabic text size slider (the preview updates while you drag; the reader uses it on its next open)
        val slider = view.findViewById<android.widget.SeekBar>(R.id.size_slider)
        val sizeValue = view.findViewById<TextView>(R.id.size_value)
        val preview = view.findViewById<TextView>(R.id.size_preview)
        fun showSize(sp: Int) {
            sizeValue.text = "$sp sp"
            preview.textSize = sp.toFloat()
            val lh = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp * 1.85f, resources.displayMetrics)
            androidx.core.widget.TextViewCompat.setLineHeight(preview, lh.toInt())   // same 1.85 line height as the reader
        }
        slider.max = com.azkar.app.data.ReaderPrefs.FONT_MAX - com.azkar.app.data.ReaderPrefs.FONT_MIN
        slider.progress = Store.arabicSp() - com.azkar.app.data.ReaderPrefs.FONT_MIN
        showSize(Store.arabicSp())
        slider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                val sp = com.azkar.app.data.ReaderPrefs.FONT_MIN + p
                showSize(sp)
                if (fromUser) Store.setArabicSp(sp)
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        // Theme (same pill style as the Arabic text size selector)
        val themes = view.findViewById<LinearLayout>(R.id.themes)
        val themeNames = arrayOf("Teal", "OLED black")
        fun renderThemes() {
            themes.removeAllViews()
            val sel = Store.theme()
            themeNames.forEachIndexed { idx, name ->
                val on = idx == sel
                val b = TextView(ctx, null, 0, R.style.TB).apply {
                    text = name
                    textSize = 15f
                    gravity = Gravity.CENTER
                    contentDescription = name + " theme"
                    setTextColor(ctx.getColor(if (on) R.color.bg else R.color.text))
                    val line = if (on) ctx.getColor(R.color.gold) else ctx.getColor(R.color.border2)
                    background = Ui.shape(ctx, if (on) ctx.getColor(R.color.gold) else null, line, 14f)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (Store.theme() != idx) {
                            Store.setTheme(idx)
                            WidgetUpdater.updateAll(ctx)
                            ThemeMode.apply(idx)   // recreates the activity; this screen is restored from the back stack
                        }
                        renderThemes()
                    }
                }
                val p = LinearLayout.LayoutParams(0, Ui.pxi(ctx, 44f), 1f)
                if (idx > 0) p.marginStart = Ui.pxi(ctx, 8f)
                themes.addView(b, p)
            }
        }
        renderThemes()
        renderList()
    }

    override fun onResume() {
        super.onResume()
        if (view != null) {
            ReminderScheduler.rescheduleAll(requireContext())   // picks up a newly granted exact-alarm permission
            renderList()
        }
    }

    private fun renderList() {
        val root = view ?: return
        val ctx = requireContext()
        val list = root.findViewById<LinearLayout>(R.id.reminders)
        list.removeAllViews()
        val all = Store.schedules()
        val globalOn = Store.notificationsOn()
        list.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.rem_empty).visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        root.findViewById<TextView>(R.id.notif_sub).text =
            if (!globalOn) "All reminders are paused" else "${all.count { it.enabled }} of ${all.size} reminders on"

        all.forEachIndexed { idx, s ->
            val v = layoutInflater.inflate(R.layout.item_reminder, list, false)
            v.findViewById<View>(R.id.divider).setBackgroundColor(if (idx == 0) 0 else ctx.getColor(R.color.border))
            v.findViewById<TextView>(R.id.title).text = s.title
            v.findViewById<TextView>(R.id.time).text = timeLabel(ctx, s.hour, s.minute) + " · Every day"
            v.findViewById<View>(R.id.row).apply {
                alpha = if (globalOn) 1f else 0.5f
                setOnClickListener { edit(s) }
            }
            v.findViewById<ToggleSwitch>(R.id.sw).apply {
                contentDescription = s.title + " reminder"
                on = s.enabled
                onToggle = { isOn ->
                    val updated = s.copy(enabled = isOn)
                    Store.upsertSchedule(updated)
                    if (isOn) ensureNotificationPermission()
                    ReminderScheduler.apply(ctx, updated)
                    renderList()
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
        fun showTime() { timeTv.text = timeLabel(ctx, h, m) }
        showTime()
        timeTv.setOnClickListener {
            TimePickerDialog(ctx, { _, hh, mm -> h = hh; m = mm; showTime() }, h, m, DateFormat.is24HourFormat(ctx)).show()
        }

        var folder: String? = existing?.open?.takeIf { Store.folder(it) != null }   // a deleted folder just becomes "open the app"
        val folderTv = v.findViewById<TextView>(R.id.r_folder)
        fun folderLabel(f: com.azkar.app.data.Folder) = (f.icon?.let { "$it " } ?: "") + f.name
        fun showFolder() { folderTv.text = Store.folder(folder ?: "")?.let { folderLabel(it) } ?: "Just open the app" }
        showFolder()
        folderTv.setOnClickListener {
            val fs = Store.folders()
            val names = arrayOf("Just open the app") + fs.map { folderLabel(it) }
            AlertDialog.Builder(ctx).setTitle("Open folder")
                .setItems(names) { _, which -> folder = if (which == 0) null else fs[which - 1].id; showFolder() }
                .show()
        }

        val b = AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) "New reminder" else "Edit reminder")
            .setView(v)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
        if (existing != null) b.setNeutralButton("Delete") { _, _ ->
            ReminderScheduler.cancel(ctx, existing.id)   // cancel the alarm before forgetting it
            Store.deleteSchedule(existing.id)
            renderList()
        }
        val dlg = b.create()
        dlg.setOnShowListener {
            dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val t = title.text.toString().trim()
                if (t.isEmpty()) { title.error = "Enter a title"; return@setOnClickListener }
                val text = msg.text.toString().trim().ifEmpty { "Time for your azkar · tap to read" }
                val s = (existing ?: Schedule(Store.newScheduleId(), t, text, h, m, true, null))
                    .copy(title = t, message = text, hour = h, minute = m, open = folder)
                Store.upsertSchedule(s)
                ReminderScheduler.cancel(ctx, s.id)   // drop the old alarm, then set the new time
                ReminderScheduler.apply(ctx, s)
                if (s.enabled) ensureNotificationPermission()
                renderList()
                dlg.dismiss()
            }
        }
        dlg.show()
    }
}