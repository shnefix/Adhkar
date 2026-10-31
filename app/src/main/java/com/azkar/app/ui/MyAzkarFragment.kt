package com.azkar.app.ui

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.ImportJson
import com.azkar.app.data.Store
import com.azkar.app.reminders.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyAzkarFragment : Fragment(R.layout.fragment_my_azkar) {
    private var t0 = 0L
    private lateinit var myAdapter: MyAdapter

    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readAndConfirm(uri)
    }
    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeExport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        t0 = SystemClock.elapsedRealtime()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindNav(view, Tab.MY)
        myAdapter = MyAdapter(
            onAdd = { nav().push(AddZikrFragment.of(null)) },
            onImport = { importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onExport = { exportPicker.launch("azkar-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json") },
            onFolder = { nav().push(EditFolderFragment.of(it)) },
            onNewFolder = { promptNewFolder { refresh() } },
            onEdit = { nav().push(EditZikrFragment.of(it)) }
        )
        view.findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = myAdapter
            itemAnimator = null
        }
        refresh()

        view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                view.viewTreeObserver.removeOnPreDrawListener(this)
                android.util.Log.d("AzkarPerf", "My azkar first frame: ${SystemClock.elapsedRealtime() - t0} ms")
                return true
            }
        })
    }

    private fun refresh() {
        val counts = Store.zikrCounts()
        val names = Store.folders().associate { it.id to it.name }
        val tiles = Store.folders().map { Tile(it.id, it.name, it.icon, counts[it.id] ?: 0) }
        val saved = Store.zikrs()
            .filter { !it.id.startsWith("b_") }
            .map {
                MyItem.Saved(
                    it.id, it.arabic, it.repeat, it.note, names[it.folderId] ?: "Anytime",
                    !(it.folderId == null || Store.isBuiltinFolder(it.folderId))
                )
            }
        val rows = ArrayList<MyItem>()
        rows.add(MyItem.Header)
        rows.add(MyItem.Folders(tiles))
        if (saved.isNotEmpty()) { rows.add(MyItem.SavedLabel); rows.addAll(saved) }
        myAdapter.submitList(rows)
    }

    private fun writeExport(uri: Uri) {
        val ctx = requireContext()
        try {
            val text = Store.exportJson()
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
                refresh()
                AlertDialog.Builder(requireContext()).setTitle("Import complete")
                    .setMessage("${r.created} new folders, ${r.merged} merged, ${r.added} azkar added, ${r.skipped} duplicates skipped, ${r.reminders} reminders added.")
                    .setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Cancel", null).show()
    }
}