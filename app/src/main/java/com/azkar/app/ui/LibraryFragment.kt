package com.azkar.app.ui

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
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
import com.azkar.app.data.Folder
import com.azkar.app.data.FolderTree
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

private const val K_EXPORT_FOLDER = "export_folder_id"
private const val ARG_PARENT = "parent"

@AndroidEntryPoint
class LibraryFragment : Fragment(R.layout.fragment_library), HandlesOwnInsets {
    private val viewModel: AzkarContentViewModel by viewModels()
    private var latestContent: AzkarContent? = null

    private var t0 = 0L
    // Search over everything: the index is rebuilt off the main thread when the content changes.
    private var searchIndex: com.azkar.app.data.LibrarySearch.Index? = null
    private var indexedContent: AzkarContent? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    private var searchFilter = SearchResults.Filter.ALL
    private var searchShown = SearchResults.pageSize
    private val cards = CardAdapter()
    /** The folder whose folders are shown, or null for the Library's top level. */
    private val parentId: String? get() = arguments?.getString(ARG_PARENT)

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
        // The file picker may outlive this fragment (rotation, low memory): keep which folder was being exported.
        exportFolderId = savedInstanceState?.getString(K_EXPORT_FOLDER)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(K_EXPORT_FOLDER, exportFolderId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindNav(view, Tab.LIBRARY)
        parentId?.let { id ->
            view.findViewById<TextView>(R.id.title).apply { text = Store.folderName(id); textSize = 22f }
            view.findViewById<View>(R.id.btn_back).apply { visibility = View.VISIBLE; setOnClickListener { nav().pop() } }
            listOf(R.id.btn_add, R.id.btn_import, R.id.btn_export).forEach { view.findViewById<View>(it).visibility = View.GONE }
        }

        view.findViewById<View>(R.id.btn_add).setOnClickListener {
            nav().push(AddZikrFragment.of(null))
        }

        view.findViewById<View>(R.id.btn_import).setOnClickListener {
            launchImportPicker()
        }

        view.findViewById<View>(R.id.btn_export).setOnClickListener { chooseExport() }

        view.findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { searchShown = SearchResults.pageSize; refresh() }
        })

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
                    // Debug builds only: release builds shouldn't spam logcat.
                    if (view.context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                        android.util.Log.d(
                            "AzkarPerf",
                            "Library first frame: ${SystemClock.elapsedRealtime() - t0} ms"
                        )
                    }
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
        val query = view?.findViewById<EditText>(R.id.search)?.text?.toString()?.trim().orEmpty()
        showSearch(current, counts, query)
        if (query.isNotEmpty()) return

        val parent = parentId
        val shown = FolderTree.children(current.folders, parent)
        val kids = current.folders.groupingBy { it.parent }.eachCount()
        view?.findViewById<TextView>(R.id.title)?.let { t -> parent?.let { t.text = Store.folderName(it) } }

        // Inside a folder that also has azkar of its own: one card for them, first.
        val own = parent?.let { counts[it] } ?: 0
        if (parent != null && own > 0) list.add(
            Card("own:$parent", getString(R.string.this_folder_azkar), azkarCount(ctx, own), null,
                R.drawable.ic_book_1p75, teal, false) { nav().push(FolderFragment.of(parent)) }
        )

        shown.forEach { f ->
            val isDefault = Store.isDefaultFolder(f.id)
            val sub = kids[f.id] ?: 0
            val n = counts[f.id] ?: 0
            val countText = when {
                sub == 0 -> azkarCount(ctx, n)
                n == 0 -> resources.getQuantityString(R.plurals.folder_count, sub, sub)
                else -> getString(R.string.folders_and_azkar, resources.getQuantityString(R.plurals.folder_count, sub, sub), azkarCount(ctx, n))
            }
            val book = isDefault && sub == 0
            list.add(
                Card(
                    f.id, f.name, countText, f.icon,
                    if (book) R.drawable.ic_book_1p75 else R.drawable.ic_folder_1p75,
                    if (book) teal else gold,
                    false,
                    onMenu = { folderMenu(f) }
                ) { openFolder(f.id) }
            )
        }

        val anytime = if (parent == null) counts[null] ?: 0 else 0

        if (anytime > 0) {
            list.add(
                Card(
                    Store.ANYTIME,
                    getString(R.string.anytime),
                    azkarCount(ctx, anytime),
                    null,
                    R.drawable.ic_folder_1p75,
                    gold,
                    false
                ) {
                    nav().push(FolderFragment.of(Store.ANYTIME))
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
                promptNewFolder(parent) { latestContent?.let(::refresh) }
            }
        )

        cards.submitList(list)

        val n = if (parent == null) current.folders.size else shown.size
        view?.findViewById<TextView>(R.id.folder_count)?.text =
            resources.getQuantityString(R.plurals.folder_count, n, n)
    }

    /**
     * With a query: hide the folder grid and show what matches it (folders, azkar by text, title, note, hadith or
     * source), searched off the main thread. Without one: the grid as usual.
     */
    private fun showSearch(content: AzkarContent, counts: Map<String?, Int>, query: String) {
        val v = view ?: return
        val searching = query.isNotEmpty()
        v.findViewById<View>(R.id.list).visibility = if (searching) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.folders_header).visibility = if (searching) View.GONE else View.VISIBLE
        val results = v.findViewById<android.widget.ScrollView>(R.id.results)
        results.visibility = if (searching) View.VISIBLE else View.GONE
        searchJob?.cancel()
        if (!searching) { searchFilter = SearchResults.Filter.ALL; searchShown = SearchResults.pageSize; return }
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(120)   // typing fast: search once the user pauses
            val result = withContext(Dispatchers.Default) {
                val index = searchIndex.takeIf { indexedContent === content }
                    ?: com.azkar.app.data.LibrarySearch.Index(content).also { searchIndex = it; indexedContent = content }
                index.search(query)
            }
            val ctx = context ?: return@launch
            SearchResults.render(
                ctx, v.findViewById(R.id.results_body), query, result, searchFilter, searchShown, counts,
                onFilter = { searchFilter = it; searchShown = SearchResults.pageSize; results.scrollTo(0, 0); refresh() },
                onMore = { searchShown += SearchResults.pageSize; refresh() },
                onFolder = { openFolder(it.id) },
                onZikr = { z -> nav().push(ReaderFragment.of(Store.readerKey(z), Store.readerIndex(z))) }
            )
        }
    }

    /** null = everything (folders, reminders, settings); otherwise just that folder. */
    private var exportFolderId: String? = null

    /** The user picks: a full backup, or one folder to share. */
    private fun chooseExport() {
        val ctx = requireContext()
        AlertDialog.Builder(ctx)
            .setTitle(R.string.export_label)
            .setItems(arrayOf(getString(R.string.export_all), getString(R.string.export_one))) { _, which ->
                val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                if (which == 0) {
                    exportFolderId = null
                    exportPicker.launch("azkar-backup-$day.json")
                } else {
                    val folders = latestContent?.folders.orEmpty()
                    if (folders.isEmpty()) return@setItems
                    AlertDialog.Builder(ctx)
                        .setTitle(R.string.export_pick_folder)
                        .setItems(folders.map { it.name }.toTypedArray()) { _, i ->
                            val f = folders[i]
                            exportFolderId = f.id
                            val safe = f.name.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifEmpty { "folder" }
                            exportPicker.launch("azkar-$safe-$day.json")
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun writeExport(uri: Uri) {
        val ctx = requireContext()
        val folderId = exportFolderId
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val text = if (folderId != null) Store.exportFolderJsonAsync(folderId) else Store.exportJsonAsync()
                check(withContext(Dispatchers.Default) { ImportJson.parse(text) is ImportJson.Parsed.Ok }) {
                    getString(R.string.export_validation_failed)
                }
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw java.io.IOException(getString(R.string.export_open_failed))
                }
                if (folderId != null) {
                    val n = latestContent?.zikrs?.count { it.folderId == folderId } ?: 0
                    AlertDialog.Builder(ctx)
                        .setTitle(R.string.export_complete_title)
                        .setMessage(getString(R.string.export_folder_complete, latestContent?.folders?.firstOrNull { it.id == folderId }?.name ?: "", count(R.plurals.azkar_count, n)))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@launch
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
                val app = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Not cancellable: leaving the screen mid-import must not stop it between the database
                        // write and the settings / alarms / widgets that follow it.
                        val r = withContext(kotlinx.coroutines.NonCancellable) {
                            val sum = Store.importAllAsync(p)
                            ReminderScheduler.rescheduleAll(app)
                            // A backup with tracking turns the tracking widgets on.
                            com.azkar.app.widgets.NewWidgetUpdater.syncTrackingProviders(app)
                            com.azkar.app.widgets.WidgetUpdater.updateAll(app)
                            sum
                        }
                        if (!isAdded) return@launch
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.import_complete_title)
                            .setMessage(getString(R.string.import_complete_message, r.created, r.merged, r.added, r.skipped, r.reminders))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (isAdded) showError(getString(R.string.import_failed, e.message ?: ""))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun count(plural: Int, n: Int) = resources.getQuantityString(plural, n, n)

    /** A folder holding other folders opens as a grid of them; any other folder opens its azkar. */
    private fun openFolder(id: String) {
        if (Store.childFolders(id).isNotEmpty()) nav().push(of(id)) else nav().push(FolderFragment.of(id))
    }

    /** The ⋮ on a folder card: open it, make a folder inside it, move it, or edit it (rename, icon, delete). */
    private fun folderMenu(f: Folder) {
        val actions = listOf<Pair<Int, () -> Unit>>(
            R.string.folder_open to { openFolder(f.id) },
            R.string.new_folder_inside to { promptNewFolder(f.id) { nav().push(of(f.id)) } },
            R.string.move_to to { pickMoveTarget(f) },
            R.string.edit_folder to { nav().push(EditFolderFragment.of(f.id)) },
        )
        AlertDialog.Builder(requireContext())
            .setTitle(f.name)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, i -> actions[i].second() }
            .show()
    }

    /** Where to move [f]: the top level, or any folder that isn't [f] or inside it (indented to show the tree). */
    private fun pickMoveTarget(f: Folder) {
        val ctx = requireContext()
        val tree = Store.moveTargets(f.id)
        val targets: List<String?> = listOf<String?>(null) + tree.map { it.first.id }
        val names = listOf(getString(R.string.top_level)) + tree.map { it.first.name }
        // The LRM keeps the indent on the left even for Arabic names (an RTL line would put leading spaces on the right).
        val labels = listOf(names[0]) + tree.map { (t, depth) -> if (depth == 0) t.name else "\u200e" + "\u2003\u2003".repeat(depth) + "\u2067" + t.name + "\u2069" }
        val current = targets.indexOf(f.parent).coerceAtLeast(0)
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.move_folder_title, f.name))
            .setSingleChoiceItems(labels.toTypedArray(), current) { d, i ->
                d.dismiss()
                if (targets[i] != f.parent && Store.moveFolderTo(f.id, targets[i])) {
                    android.widget.Toast.makeText(ctx, getString(R.string.moved_to, names[i]), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        fun of(parentId: String) = LibraryFragment().apply { arguments = Bundle().apply { putString(ARG_PARENT, parentId) } }
    }
}
