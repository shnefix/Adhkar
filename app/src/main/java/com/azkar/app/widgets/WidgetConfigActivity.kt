package com.azkar.app.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.azkar.app.R
import com.azkar.app.ThemeMode
import com.azkar.app.data.Store

/** Shown when a widget is added: pick the design, folder, and theme. */
class WidgetConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        Store.init(this)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        chooseStyle(id, WidgetUpdater.kindOf(this, id))
    }

    private fun chooseStyle(id: Int, kind: WidgetKind) {
        val styles = WidgetStyle.values().filter { kind in it.kinds }
        AlertDialog.Builder(this)
            .setTitle(R.string.widget_style_title)
            .setItems(styles.map { it.label }.toTypedArray()) { _, i -> chooseFolder(id, kind, styles[i]) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun chooseFolder(id: Int, kind: WidgetKind, style: WidgetStyle) {
        val ids = ArrayList<String>()
        val names = ArrayList<String>()
        Store.folders().forEach { f -> ids.add(f.id); names.add((f.icon?.let { "$it " } ?: "") + f.name) }
        if (Store.zikrs().any { it.folderId == null }) { ids.add("anytime"); names.add("Anytime") }
        if (ids.isEmpty()) { save(id, kind, style, "anytime"); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.widget_folder_title)
            .setItems(names.toTypedArray()) { _, i -> save(id, kind, style, ids[i]) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun save(id: Int, kind: WidgetKind, style: WidgetStyle, folder: String) {
        chooseTheme(id, kind, style, folder)
    }

    private fun chooseTheme(id: Int, kind: WidgetKind, style: WidgetStyle, folder: String) {
        val themes = arrayOf(
            getString(R.string.widget_theme_follow_app),
            getString(R.string.widget_theme_teal),
            getString(R.string.widget_theme_oled),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.widget_theme_title)
            .setItems(themes) { _, choice ->
                val theme = when (choice) {
                    1 -> ThemeMode.CURRENT
                    2 -> ThemeMode.OLED
                    else -> null
                }
                saveConfig(id, kind, style, folder, theme)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun saveConfig(id: Int, kind: WidgetKind, style: WidgetStyle, folder: String, theme: Int?) {
        WidgetStore.setConfig(this, id, style, folder, theme)
        WidgetUpdater.update(this, AppWidgetManager.getInstance(this), id, kind)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
