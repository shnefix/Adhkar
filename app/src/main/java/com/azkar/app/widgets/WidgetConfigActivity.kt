package com.azkar.app.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.azkar.app.data.Store

/** Shown when a widget is added: pick the design, then the folder. Two plain dialogs, no layout needed. */
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
            .setTitle("Widget style")
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
            .setTitle("Which folder?")
            .setItems(names.toTypedArray()) { _, i -> save(id, kind, style, ids[i]) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun save(id: Int, kind: WidgetKind, style: WidgetStyle, folder: String) {
        WidgetStore.setConfig(this, id, style, folder)
        WidgetUpdater.update(this, AppWidgetManager.getInstance(this), id, kind)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
