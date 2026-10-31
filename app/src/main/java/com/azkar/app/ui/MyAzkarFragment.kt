package com.azkar.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.ImportJson
import com.azkar.app.data.Store
import com.azkar.app.reminders.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyAzkarFragment : Fragment(R.layout.fragment_my_azkar) {

    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readAndConfirm(uri)
    }
    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeExport(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindNav(view, Tab.MY)
        view.findViewById<View>(R.id.btn_add).setOnClickListener { nav().push(AddZikrFragment.of(null)) }
        view.findViewById<View>(R.id.btn_import).setOnClickListener {
            importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        view.findViewById<View>(R.id.btn_export).setOnClickListener {
            exportPicker.launch("azkar-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json")
        }
        render()
    }

    private fun render() {
        val v = view ?: return
        val ctx = requireContext()
        val counts = Store.zikrCounts()

        val tiles = Store.folders().map { f ->
            val n = counts[f.id] ?: 0
            layoutInflater.inflate(R.layout.item_folder_tile, null, false).apply {
                Ui.folderIcon(findViewById(R.id.ic), findViewById(R.id.emoji), f.icon, R.drawable.ic_folder_1p75)
                findViewById<TextView>(R.id.name).text = f.name
                findViewById<TextView>(R.id.count).text = if (n == 1) "1 zikr" else "$n azkar"
                setOnClickListener { nav().push(EditFolderFragment.of(f.id)) }
            }
        } + layoutInflater.inflate(R.layout.item_folder_new, null, false).apply {
            setOnClickListener { promptNewFolder { render() } }
        }
        v.findViewById<LinearLayout>(R.id.folders).apply { removeAllViews(); addView(Ui.grid(ctx, tiles, 3, 8)) }

        val saved = v.findViewById<LinearLayout>(R.id.saved)
        saved.removeAllViews()
        Store.zikrs().forEachIndexed { i, z ->
            val row = layoutInflater.inflate(R.layout.item_saved, saved, false)
            val chip = row.findViewById<TextView>(R.id.chip)
            chip.text = Store.folderName(z.folderId)
            chip.setTextColor(ctx.getColor(if (z.folderId == null || Store.isBuiltinFolder(z.folderId)) R.color.teal else R.color.gold))
            row.findViewById<TextView>(R.id.rep).text = "×${z.repeat}"
            row.findViewById<TextView>(R.id.ar).text = z.arabic
            row.findViewById<TextView>(R.id.note).apply {
                text = z.note
                visibility = if (z.note.isBlank()) View.GONE else View.VISIBLE
            }
            row.findViewById<View>(R.id.edit).setOnClickListener { nav().push(EditZikrFragment.of(z.id)) }
            saved.addView(row, Ui.lp(top = if (i > 0) 8 else 0, ctx = ctx))
        }
}
    // ---------- Export ----------
    private fun writeExport(uri: Uri) {
        val ctx = requireContext()
        try {
            val text = Store.exportJson()   // read-only: builds the file from memory
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw java.io.IOException("Could not open the file for writing.")
            AlertDialog.Builder(ctx).setTitle("Export complete")
                .setMessage("Saved ${Store.folders().size} folders, ${Store.zikrs().size} azkar and ${Store.schedules().size} reminders.")
                .setPositiveButton("OK", null).show()
        } catch (e: Exception) {
            AlertDialog.Builder(ctx).setTitle("Export failed")
                .setMessage("The file could not be saved. " + (e.message ?: "")).setPositiveButton("OK", null).show()
        }
    }

    // ---------- Import ----------
    private fun readAndConfirm(uri: Uri) {
        val ctx = requireContext()
        val raw = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
        } catch (e: Exception) { null }
        when {
            raw == null -> showError("The file could not be read.")
            raw.length > 5_000_000 -> showError("The file is too large (over 5 MB).")
            else -> when (val p = ImportJson.parse(raw)) {
                is ImportJson.Parsed.Error -> showError(p.message)
                is ImportJson.Parsed.Ok -> confirm(p)
            }
        }
    }

    private fun showError(msg: String) {
        AlertDialog.Builder(requireContext()).setTitle("Can't import this file").setMessage(msg)
            .setPositiveButton("OK", null).show()
    }

    private fun confirm(p: ImportJson.Parsed.Ok) {
        val nF = p.folders.size
        val nA = p.folders.sumOf { it.items.size } + p.unfiled.size
        val existing = p.folders.count { f -> Store.folders().any { it.id == f.id || it.name.trim().equals(f.name.trim(), ignoreCase = true) } }
        val lines = p.folders.take(8).joinToString("\n") { "• ${it.name}: ${it.items.size}" } + if (p.folders.size > 8) "\n…" else ""
        var msg = "$nF folders and $nA azkar will be imported."
        if (p.reminders.isNotEmpty()) msg += " The file also has ${p.reminders.size} reminders."
        if (p.settings != null) msg += " Your reading and notification settings will be restored."
        if (lines.isNotEmpty()) msg += "\n\n$lines"
        if (existing > 0) msg += "\n\n$existing folder(s) already exist. Their azkar will be added to them, and exact duplicates are skipped. Nothing is overwritten."
        AlertDialog.Builder(requireContext()).setTitle("Import from JSON").setMessage(msg)
            .setPositiveButton("Import") { _, _ ->
                val r = Store.importAll(p)
                ReminderScheduler.rescheduleAll(requireContext())
                render()
                AlertDialog.Builder(requireContext()).setTitle("Import complete")
                    .setMessage("${r.created} new folders, ${r.merged} merged, ${r.added} azkar added, ${r.skipped} duplicates skipped, ${r.reminders} reminders added.")
                    .setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Cancel", null).show()
    }
}