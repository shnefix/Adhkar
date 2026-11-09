package com.azkar.app.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.azkar.app.R
import com.azkar.app.data.Store
import kotlinx.coroutines.launch

/** Shown when a widget is added: pick the design and folder. */
class WidgetConfigActivity : AppCompatActivity() {
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        Store.init(this)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Exported for the launcher: only configure widgets that belong to this app.
        val owner = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.packageName
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || owner != packageName) { finish(); return }
        lifecycleScope.launch {
            Store.awaitReady()
            chooseStyle(id, WidgetUpdater.kindOf(this@WidgetConfigActivity, id))
        }
    }

    private fun chooseStyle(id: Int, kind: WidgetKind) {
        val styles = WidgetStyle.values().filter { kind in it.offered }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.widget_style_title)
            .setItems(styles.map { getString(it.label) }.toTypedArray()) { _, i -> chooseFolder(id, kind, styles[i]) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun chooseFolder(id: Int, kind: WidgetKind, style: WidgetStyle) {
        val ids = ArrayList<String>()
        val names = ArrayList<String>()
        Store.readableFolders().forEach { f -> ids.add(f.id); names.add((f.icon?.let { "$it " } ?: "") + Store.folderPath(f.id)) }
        if (Store.zikrs().any { it.folderId == null }) { ids.add(Store.ANYTIME); names.add(getString(R.string.anytime)) }
        if (ids.isEmpty()) { save(id, kind, style, Store.ANYTIME); return }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.widget_folder_title)
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

    override fun onDestroy() {
        dialog?.dismiss()   // a rotation would otherwise leak the window
        super.onDestroy()
    }
}
