package com.azkar.app.ui

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.AzkarContent
import com.azkar.app.data.ImportJson
import com.azkar.app.data.Store
import com.azkar.app.reminders.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MyAzkarFragment : Fragment(R.layout.fragment_my_azkar) {
    private val viewModel: AzkarContentViewModel by viewModels()
    private var latestContent: AzkarContent? = null

    private var t0 = 0L
    private val cards = CardAdapter()

    private val importPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readAndConfirm(uri)
        }

    private val exportPicker =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) writeExport(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        t0 = SystemClock.elapsedRealtime()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindNav(view, Tab.MY)

        view.findViewById<View>(R.id.btn_add).setOnClickListener {
            nav().push(AddZikrFragment.of(null))
        }

        view.findViewById<View>(R.id.btn_import).setOnClickListener {
            launchImportPicker()
        }

        view.findViewById<View>(R.id.btn_export).setOnClickListener {
            exportPicker.launch(
                "azkar-backup-" +
                    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) +
                    ".json"
            )
        }

        view.findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = cards
            addItemDecoration(GridGap(12))
            setHasFixedSize(true)
            itemAnimator = null
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.content.collect { content ->
                    latestContent = content
                    if (content != null) refresh(content)
                }
            }
        }

        view.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                    android.util.Log.d(
                        "AzkarPerf",
                        "My azkar first frame: ${SystemClock.elapsedRealtime() - t0} ms"
                    )
                    return true
                }
            }
        )
    }

    private fun refresh(content: AzkarContent? = latestContent) {
        val current = content ?: return
        val ctx = requireContext()
        val counts = current.zikrs.groupingBy { it.folderId }.eachCount()
        val teal = ctx.getColor(R.color.teal)
        val gold = ctx.getColor(R.color.gold)
        val list = ArrayList<Card>()

        current.folders.forEach { f ->
            val isDefault = Store.isDefaultFolder(f.id)

            list.add(
                Card(
                    f.id,
                    f.name,
                    azkarCount(counts[f.id] ?: 0),
                    f.icon,
                    if (isDefault) R.drawable.ic_book_1p75 else R.drawable.ic_folder_1p75,
                    if (isDefault) teal else gold,
                    false
                ) {
                    nav().push(EditFolderFragment.of(f.id))
                }
            )
        }

        val anytime = counts[null] ?: 0

        if (anytime > 0) {
            list.add(
                Card(
                    "anytime",
                    "Anytime",
                    azkarCount(anytime),
                    null,
                    R.drawable.ic_folder_1p75,
                    gold,
                    false
                ) {
                    nav().push(FolderFragment.of("anytime"))
                }
            )
        }

        list.add(
            Card(
                "new",
                "New folder",
                "",
                null,
                R.drawable.ic_plus_2,
                gold,
                true
            ) {
                promptNewFolder { latestContent?.let(::refresh) }
            }
        )

        cards.submitList(list)

        val n = current.folders.size
        view?.findViewById<TextView>(R.id.folder_count)?.text =
            if (n == 1) "1 folder" else "$n folders"
    }

    private fun writeExport(uri: Uri) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val text = Store.exportJsonAsync()
                check(withContext(Dispatchers.Default) { ImportJson.parse(text) is ImportJson.Parsed.Ok }) {
                    "Generated backup failed validation."
                }
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw java.io.IOException("Could not open the file for writing.")
                }
                AlertDialog.Builder(ctx)
                    .setTitle("Export complete")
                    .setMessage(
                        "Saved ${latestContent?.folders?.size ?: 0} folders, " +
                            "${latestContent?.zikrs?.size ?: 0} azkar and " +
                            "${latestContent?.schedules?.size ?: 0} reminders."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                AlertDialog.Builder(ctx)
                    .setTitle("Export failed")
                    .setMessage("The file could not be saved. " + (e.message ?: ""))
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun readAndConfirm(uri: Uri) {
        val ctx = requireContext()

        val raw = try {
            ctx.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            null
        }

        when {
            raw == null -> showError("The file could not be read.")

            raw.length > 5_000_000 ->
                showError("The file is too large (over 5 MB).")

            else -> {
                when (val p = ImportJson.parse(raw)) {
                    is ImportJson.Parsed.Error -> showError(p.message)
                    is ImportJson.Parsed.Ok -> confirm(p)
                }
            }
        }
    }

    private fun showImportHelp() {
        AlertDialog.Builder(requireContext())
            .setTitle("Import Format")
            .setMessage(
                "The JSON file should contain a \"folders\" array. " +
                    "Each folder needs a \"name\" and an \"azkar\" list.\n\n" +
                    "• text: the Arabic dhikr. Diacritics are kept exactly as written.\n" +
                    "• count: how many times, 1 to 999. Leave it out for 1.\n" +
                    "• A folder with the same name gets the new azkar added to it. " +
                    "Exact duplicates are skipped and nothing is overwritten."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showError(msg: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Can't import this file")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .setNeutralButton("Show format") { _, _ ->
                showFormatHelp()
            }
            .show()
    }

    private fun showFormatHelp() {
        AlertDialog.Builder(requireContext())
            .setTitle("Import from JSON")
            .setMessage(
                "The JSON file should contain a \"folders\" list. " +
                    "Each folder needs a \"name\" and an \"azkar\" list.\n\n" +
                "• text: the Arabic dhikr. Diacritics are kept exactly as written.\n" +
                "• count: how many times, 1 to 999. Leave it out for 1.\n" +
                "• A folder with the same name gets the new azkar added to it. " +
                "Exact duplicates are skipped and nothing is overwritten."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun launchImportPicker() {
        try {
            importPicker.launch(arrayOf("*/*"))   // some file managers label .json as octet-stream
        } catch (e: Exception) {
            showError("No file picker is available on this device.")
        }
    }

    private fun confirm(p: ImportJson.Parsed.Ok) {
        val nF = p.folders.size
        val nA = p.folders.sumOf { it.items.size } + p.unfiled.size

        val existing = p.folders.count { f ->
            Store.folders().any {
                it.id == f.id ||
                    it.name.trim().equals(f.name.trim(), ignoreCase = true)
            }
        }

        val lines = p.folders.take(8).joinToString("\n") {
            "• ${it.name}: ${it.items.size}"
        } + if (p.folders.size > 8) "\n…" else ""

        var msg = "$nF folders and $nA azkar will be imported."

        if (p.reminders.isNotEmpty()) {
            msg += " The file also has ${p.reminders.size} reminders."
        }

        if (p.settings != null) {
            msg += " Your reading and notification settings will be restored."
        }

        if (lines.isNotEmpty()) {
            msg += "\n\n$lines"
        }

        if (existing > 0) {
            msg += "\n\n$existing folder(s) already exist. Their azkar will be " +
                "added to them, and exact duplicates are skipped. Nothing is overwritten."
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Import from JSON")
            .setMessage(msg)
            .setPositiveButton("Import") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val r = Store.importAllAsync(p)
                        ReminderScheduler.rescheduleAll(requireContext())
                        AlertDialog.Builder(requireContext())
                            .setTitle("Import complete")
                            .setMessage(
                                "${r.created} new folders, ${r.merged} merged, " +
                                    "${r.added} azkar added, ${r.skipped} duplicates skipped, " +
                                    "${r.reminders} reminders added."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    } catch (e: Exception) {
                        showError("Import failed: " + (e.message ?: "database write failed"))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private companion object {
        const val EXAMPLE = """{
  "folders": [
    {
      "name": "Morning",
      "azkar": [
        { "text": "Example zikr", "count": 33 },
        { "text": "Another zikr", "count": 33 },
        { "text": "A third zikr", "count": 34 }
      ]
    },
    {
      "name": "Duas",
      "azkar": [
        { "text": "Example dua", "count": 1 }
      ]
    }
  ]
}"""

        const val NOTES =
            "• folders: a list. Each folder needs a name and a list of azkar.\n" +
                "• text: the Arabic dhikr. Diacritics are kept exactly as written.\n" +
                "• count: how many times, 1 to 999. Leave it out for 1.\n" +
                "• A folder with the same name gets the new azkar added to it. " +
                "Exact duplicates are skipped and nothing is overwritten."
    }
}
