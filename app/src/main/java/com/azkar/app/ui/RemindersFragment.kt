package com.azkar.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.Store
import com.azkar.app.reminders.ReminderScheduler

class RemindersFragment : Fragment(R.layout.fragment_reminders) {

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(requireContext(), "Notifications are off for Azkar in system settings.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        bindNav(view, Tab.REMINDERS)

        // Daily reminders
        val list = view.findViewById<LinearLayout>(R.id.reminders)
        ReminderScheduler.defs.forEachIndexed { idx, d ->
            val v = layoutInflater.inflate(R.layout.item_reminder, list, false)
            v.findViewById<View>(R.id.divider).setBackgroundColor(if (idx == 0) 0 else ctx.getColor(R.color.border))
            v.findViewById<TextView>(R.id.title).text = d.title
            v.findViewById<TextView>(R.id.time).text = d.label
            v.findViewById<ToggleSwitch>(R.id.sw).apply {
                contentDescription = d.title + " reminder"
                on = Store.reminderOn(d.key, d.default)
                onToggle = { isOn ->
                    Store.setReminderOn(d.key, isOn)
                    if (isOn) { ensureNotificationPermission(); ReminderScheduler.schedule(ctx, d) }
                    else ReminderScheduler.cancel(ctx, d)
                }
            }
            list.addView(v)
        }

        // Show translation
        view.findViewById<ToggleSwitch>(R.id.sw_tr).apply {
            on = Store.showTranslation()
            onToggle = { Store.setShowTranslation(it) }
        }

        // Arabic text size
        val sizes = view.findViewById<LinearLayout>(R.id.sizes)
        val px = intArrayOf(20, 26, 32)
        val aria = arrayOf("Small Arabic text", "Medium Arabic text", "Large Arabic text")
        fun renderSizes() {
            sizes.removeAllViews()
            val z = Store.arabicSize()
            px.forEachIndexed { idx, size ->
                val on = idx == z
                val b = TextView(ctx, null, 0, R.style.ArB).apply {
                    text = "أب"
                    textSize = size.toFloat()
                    gravity = Gravity.CENTER
                    contentDescription = aria[idx]
                    setTextColor(ctx.getColor(if (on) R.color.bg else R.color.text))
                    val line = if (on) ctx.getColor(R.color.gold) else ctx.getColor(R.color.border2)
                    background = Ui.shape(ctx, if (on) ctx.getColor(R.color.gold) else null, line, 14f)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { Store.setArabicSize(idx); renderSizes() }
                }
                val p = LinearLayout.LayoutParams(0, Ui.pxi(ctx, 44f), 1f)
                if (idx > 0) p.marginStart = Ui.pxi(ctx, 8f)
                sizes.addView(b, p)
            }
        }
        renderSizes()
    }
}
