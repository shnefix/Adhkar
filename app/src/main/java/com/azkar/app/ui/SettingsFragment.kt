package com.azkar.app.ui

import android.Manifest
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.ThemeMode
import com.azkar.app.data.AppPrefs
import com.azkar.app.reminders.Cities
import com.azkar.app.reminders.LocationUpdater
import com.azkar.app.reminders.ReminderScheduler
import com.azkar.app.widgets.WidgetUpdater

/** Prayer-time methods offered in Settings: adhan CalculationMethod names and their labels. */
private val CALC_METHODS = listOf(
    "EGYPTIAN" to R.string.calc_egyptian,
    "UMM_AL_QURA" to R.string.calc_umm_al_qura,
    "MUSLIM_WORLD_LEAGUE" to R.string.calc_mwl,
    "KARACHI" to R.string.calc_karachi,
    "NORTH_AMERICA" to R.string.calc_isna,
    "DUBAI" to R.string.calc_dubai,
    "KUWAIT" to R.string.calc_kuwait,
    "QATAR" to R.string.calc_qatar,
    "SINGAPORE" to R.string.calc_singapore,
    "MOON_SIGHTING_COMMITTEE" to R.string.calc_moonsighting
)

/** App settings: reading, appearance and prayer times. Notifications and reminders have their own page. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var onLocationResult: (() -> Unit)? = null
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) LocationUpdater.refresh(requireContext())
        else Toast.makeText(requireContext(), R.string.location_permission_denied, Toast.LENGTH_LONG).show()
        onLocationResult?.invoke()
        onLocationResult = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }

        // Reading switches
        view.findViewById<ToggleSwitch>(R.id.sw_sources).apply { on = AppPrefs.sourcesOn(); onToggle = { AppPrefs.setSourcesOn(it) } }
        view.findViewById<ToggleSwitch>(R.id.sw_tap).apply { on = AppPrefs.tapAdvance(); onToggle = { AppPrefs.setTapAdvance(it) } }
        view.findViewById<ToggleSwitch>(R.id.sw_tap_back).apply { on = AppPrefs.tapLeftBack(); onToggle = { AppPrefs.setTapLeftBack(it) } }
        view.findViewById<ToggleSwitch>(R.id.sw_slider).apply { on = AppPrefs.readerSlider(); onToggle = { AppPrefs.setReaderSlider(it) } }
        view.findViewById<ToggleSwitch>(R.id.sw_focus).apply { on = AppPrefs.focusMode(); onToggle = { AppPrefs.setFocusMode(it) } }
        view.findViewById<ToggleSwitch>(R.id.sw_awake).apply { on = AppPrefs.keepScreenOn(); onToggle = { AppPrefs.setKeepScreenOn(it) } }

        // Arabic text size slider (the preview updates while you drag; the reader uses it on its next open)
        val slider = view.findViewById<android.widget.SeekBar>(R.id.size_slider)
        val sizeValue = view.findViewById<TextView>(R.id.size_value)
        val preview = view.findViewById<TextView>(R.id.size_preview)
        fun showSize(sp: Int) {
            sizeValue.text = getString(R.string.text_size_sp, sp)
            preview.textSize = sp.toFloat()
            val lh = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sp * 1.85f, resources.displayMetrics)
            androidx.core.widget.TextViewCompat.setLineHeight(preview, lh.toInt())   // same 1.85 line height as the reader
        }
        slider.max = com.azkar.app.data.ReaderPrefs.FONT_MAX - com.azkar.app.data.ReaderPrefs.FONT_MIN
        slider.progress = AppPrefs.arabicSp() - com.azkar.app.data.ReaderPrefs.FONT_MIN
        showSize(AppPrefs.arabicSp())
        slider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                val sp = com.azkar.app.data.ReaderPrefs.FONT_MIN + p
                showSize(sp)
                if (fromUser) AppPrefs.setArabicSp(sp)
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        // Theme (same pill style as the Arabic text size selector)
        val themes = view.findViewById<LinearLayout>(R.id.themes)
        val themeNames = arrayOf(getString(R.string.theme_teal), getString(R.string.theme_oled))
        fun renderThemes() {
            themes.removeAllViews()
            val sel = AppPrefs.theme()
            themeNames.forEachIndexed { idx, name ->
                val on = idx == sel
                val b = TextView(ctx, null, 0, R.style.TB).apply {
                    text = name
                    textSize = 15f
                    gravity = Gravity.CENTER
                    contentDescription = getString(R.string.theme_description, name)
                    setTextColor(ctx.getColor(if (on) R.color.bg else R.color.text))
                    val line = if (on) ctx.getColor(R.color.gold) else ctx.getColor(R.color.border2)
                    background = Ui.shape(ctx, if (on) ctx.getColor(R.color.gold) else null, line, 14f)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (AppPrefs.theme() != idx) {
                            AppPrefs.setTheme(idx)
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
        // Prayer times: location, method and Asr school. A change moves the prayer-based reminders and the widgets' times.
        val locSub = view.findViewById<TextView>(R.id.loc_sub)
        val calcSub = view.findViewById<TextView>(R.id.calc_sub)
        val asrSub = view.findViewById<TextView>(R.id.asr_sub)
        val arabicUi = resources.configuration.locales[0].language == "ar"
        fun renderPrayer() {
            val loc = AppPrefs.location()
            locSub.text = when {
                AppPrefs.locationManual() && loc != null -> getString(
                    R.string.location_manual,
                    Cities.byId(AppPrefs.locationCity())?.name(arabicUi)
                        ?: String.format(java.util.Locale.US, "%.4f, %.4f", loc.first, loc.second)
                )
                loc != null -> getString(R.string.location_auto)
                else -> getString(R.string.location_none)
            }
            calcSub.text = getString(CALC_METHODS.firstOrNull { it.first == AppPrefs.calcMethod() }?.second ?: R.string.calc_egyptian)
            asrSub.text = getString(if (AppPrefs.asrHanafi()) R.string.asr_hanafi else R.string.asr_standard)
        }
        fun prayerChanged() {
            ReminderScheduler.rescheduleAll(ctx)
            WidgetUpdater.updateAll(ctx)
            renderPrayer()
        }
        renderPrayer()
        view.findViewById<View>(R.id.loc_row).setOnClickListener {
            val choices = arrayOf(
                getString(R.string.location_use_phone), getString(R.string.location_enter_coords)
            )
            AlertDialog.Builder(ctx)
                .setTitle(R.string.location)
                .setItems(choices) { _, which ->
                    when (which) {
                        0 -> {
                            AppPrefs.useAutomaticLocation()
                            prayerChanged()
                            if (LocationUpdater.hasPermission(ctx)) LocationUpdater.refresh(ctx)   // the fix reschedules on arrival
                            else {
                                onLocationResult = { prayerChanged() }
                                locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        }
                        else -> enterCoordinates { prayerChanged() }
                    }
                }
                .show()
        }
        view.findViewById<View>(R.id.calc_row).setOnClickListener {
            val current = CALC_METHODS.indexOfFirst { it.first == AppPrefs.calcMethod() }.coerceAtLeast(0)
            AlertDialog.Builder(ctx)
                .setTitle(R.string.calc_method)
                .setSingleChoiceItems(CALC_METHODS.map { getString(it.second) }.toTypedArray(), current) { d, which ->
                    AppPrefs.setCalcMethod(CALC_METHODS[which].first)
                    prayerChanged()
                    d.dismiss()
                }
                .show()
        }
        view.findViewById<View>(R.id.asr_row).setOnClickListener {
            val names = arrayOf(getString(R.string.asr_standard), getString(R.string.asr_hanafi))
            AlertDialog.Builder(ctx)
                .setTitle(R.string.asr_time)
                .setSingleChoiceItems(names, if (AppPrefs.asrHanafi()) 1 else 0) { d, which ->
                    AppPrefs.setAsrHanafi(which == 1)
                    prayerChanged()
                    d.dismiss()
                }
                .show()
        }

    }

    /** Latitude / longitude typed by hand, when the phone's location is off. */
    private fun enterCoordinates(onSet: () -> Unit) {
        val ctx = requireContext()
        fun field(hint: Int, value: Double?) = EditText(ctx).apply {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setHint(hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            textDirection = View.TEXT_DIRECTION_LTR
            value?.let { setText(String.format(java.util.Locale.US, "%.4f", it)) }
        }
        val loc = AppPrefs.location().takeIf { AppPrefs.locationManual() }
        val lat = field(R.string.latitude_hint, loc?.first)
        val lng = field(R.string.longitude_hint, loc?.second)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.pxi(ctx, 20f)
            setPadding(p, Ui.pxi(ctx, 8f), p, 0)
            addView(lat)
            addView(lng, Ui.lp(top = 8, ctx = ctx))
        }
        val dlg = AlertDialog.Builder(ctx)
            .setTitle(R.string.location_enter_coords)
            .setView(box)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                // Accept a comma as the decimal mark too, as some keyboards type one.
                val a = lat.text.toString().trim().replace(',', '.').toDoubleOrNull()
                val b = lng.text.toString().trim().replace(',', '.').toDoubleOrNull()
                when {
                    a == null || a !in -90.0..90.0 -> lat.error = getString(R.string.location_out_of_range)
                    b == null || b !in -180.0..180.0 -> lng.error = getString(R.string.location_out_of_range)
                    else -> {
                        AppPrefs.setManualLocation(a, b, null)
                        dlg.dismiss()
                        onSet()
                    }
                }
            }
        }
        dlg.show()
    }
}
