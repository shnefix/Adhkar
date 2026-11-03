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
class MyAzkarFragment : Fragment(R.layout.fragment_my_azkar), HandlesOwnInsets {
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
                    azkarCount(ctx, counts[f.id] ?: 0),
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
                    getString(R.string.anytime),
                    azkarCount(ctx, anytime),
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
                getString(R.string.new_folder),
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
            resources.getQuantityString(R.plurals.folder_count, n, n)
    }

    private fun writeExport(uri: Uri) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val text = Store.exportJsonAsync()
                check(withContext(Dispatchers.Default) { ImportJson.parse(text) is ImportJson.Parsed.Ok }) {
                    getString(R.string.export_validation_failed)
                }
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw java.io.IOException(getString(R.string.export_open_failed))
                }
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.export_complete_title)
                    .setMessage(
                        getString(
                            R.string.export_complete_message,
                            count(R.plurals.folder_count, latestContent?.folders?.size ?: 0),
                            count(R.plurals.azkar_count, latestContent?.zikrs?.size ?: 0),
                            count(R.plurals.reminder_count, latestContent?.schedules?.size ?: 0)
                        )
                    )
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } catch (e: Exception) {
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.export_failed_title)
                    .setMessage(getString(R.string.export_failed_message, e.message ?: ""))
                    .setPositiveButton(android.R.string.ok, null)
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
            raw == null -> showError(getString(R.string.import_read_failed))

            raw.length > 5_000_000 ->
                showError(getString(R.string.import_too_large))

            else -> {
                when (val p = ImportJson.parse(raw)) {
                    is ImportJson.Parsed.Error -> showError(p.message(ctx))
                    is ImportJson.Parsed.Ok -> confirm(p)
                }
            }
        }
    }

    private fun showError(msg: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_error_title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.show_format) { _, _ ->
                showFormatHelp()
            }
            .show()
    }

    private fun showFormatHelp() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_json)
            .setMessage(R.string.import_format_help)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun launchImportPicker() {
        try {
            importPicker.launch(arrayOf("*/*"))   // some file managers label .json as octet-stream
        } catch (e: Exception) {
            showError(getString(R.string.no_file_picker))
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

        var msg = getString(R.string.import_confirm_summary, count(R.plurals.folder_count, nF), count(R.plurals.azkar_count, nA))

        if (p.reminders.isNotEmpty()) {
            msg += " " + getString(R.string.import_confirm_reminders, count(R.plurals.reminder_count, p.reminders.size))
        }

        if (p.settings != null) {
            msg += " " + getString(R.string.import_confirm_settings)
        }

        if (lines.isNotEmpty()) {
            msg += "\n\n$lines"
        }

        if (existing > 0) {
            msg += "\n\n" + resources.getQuantityString(R.plurals.import_confirm_existing, existing, existing)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_json)
            .setMessage(msg)
            .setPositiveButton(R.string.import_label) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val r = Store.importAllAsync(p)
                        ReminderScheduler.rescheduleAll(requireContext())
                        // A backup with tracking turns the tracking widgets on.
                        com.azkar.app.widgets.NewWidgetUpdater.syncTrackingProviders(requireContext())
                        com.azkar.app.widgets.WidgetUpdater.updateAll(requireContext())
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.import_complete_title)
                            .setMessage(getString(R.string.import_complete_message, r.created, r.merged, r.added, r.skipped, r.reminders))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    } catch (e: Exception) {
                        showError(getString(R.string.import_failed, e.message ?: ""))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun count(plural: Int, n: Int) = resources.getQuantityString(plural, n, n)
}
