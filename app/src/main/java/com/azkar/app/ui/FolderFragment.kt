package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.Store
import com.azkar.app.data.Visibility

/** Lists a folder's azkar. Tap = read from there. Long-press or Select = hide or show several at once. */
class FolderFragment : Fragment(R.layout.fragment_folder_view) {
    private var selecting = false
    private val selected = LinkedHashSet<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val id = requireArguments().getString("id") ?: "anytime"
        if (id != "anytime" && Store.folder(id) == null) { nav().pop(); return }   // deleted from the editor

        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }
        view.findViewById<TextView>(R.id.title).text = Store.folderName(id)
        view.findViewById<View>(R.id.btn_edit).apply {
            if (id == "anytime") visibility = View.INVISIBLE
            else setOnClickListener { nav().push(EditFolderFragment.of(id)) }
        }

        val empty = view.findViewById<View>(R.id.empty)
        val info = view.findViewById<TextView>(R.id.hidden_info)
        val btnSelect = view.findViewById<TextView>(R.id.btn_select)
        val bar = view.findViewById<View>(R.id.action_bar)
        val selCount = view.findViewById<TextView>(R.id.sel_count)
        val btnAll = view.findViewById<View>(R.id.btn_all)
        val btnHide = view.findViewById<View>(R.id.btn_hide)
        val btnShow = view.findViewById<View>(R.id.btn_show)

        fun items() = if (id == "anytime") Store.zikrs().filter { it.folderId == null } else Store.zikrsIn(id)

        var refresh: () -> Unit = {}
        // While selecting, Back leaves selection mode instead of leaving the folder.
        val backCb = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { selecting = false; selected.clear(); refresh() }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCb)

        val adapter = ZikrAdapter(
            onTap = { row ->
                if (selecting) { if (!selected.remove(row.id)) selected.add(row.id); refresh() }
                else if (row.hidden) Toast.makeText(ctx, "Hidden azkar are skipped while reading. Tap Select, then Show.", Toast.LENGTH_SHORT).show()
                else nav().push(ReaderFragment.of(id, Visibility.readerIndex(items(), Store.hiddenZikrs(), row.id).coerceAtLeast(0)))
            },
            onLong = { row -> if (!selecting) { selecting = true; selected.add(row.id); refresh() } },
            onEdit = { zid -> nav().push(EditZikrFragment.of(zid)) }
        )

        refresh = {
            val hidden = Store.hiddenZikrs()
            val list = items()
            selected.retainAll(list.map { it.id }.toSet())
            val nHidden = list.count { it.id in hidden }
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            info.text = when (nHidden) { 0 -> ""; 1 -> "1 hidden"; else -> "$nHidden hidden" }
            btnSelect.text = if (selecting) "Cancel" else "Select"
            btnSelect.visibility = if (list.isEmpty()) View.INVISIBLE else View.VISIBLE
            bar.visibility = if (selecting) View.VISIBLE else View.GONE
            selCount.text = "${selected.size} selected"
            btnHide.alpha = if (selected.isEmpty()) 0.4f else 1f
            btnShow.alpha = if (selected.isEmpty()) 0.4f else 1f
            backCb.isEnabled = selecting
            adapter.submitList(list.map { ZRow(it.id, it.arabic, it.repeat, it.note, it.id in hidden, selecting, it.id in selected) })
        }

        btnSelect.setOnClickListener { selecting = !selecting; selected.clear(); refresh() }
        btnAll.setOnClickListener {
            val ids = items().map { it.id }
            val all = selected.size == ids.size
            selected.clear()
            if (!all) selected.addAll(ids)
            refresh()
        }
        btnHide.setOnClickListener {
            if (selected.isNotEmpty()) { Store.setZikrsHidden(selected.toList(), true); selecting = false; selected.clear(); refresh() }
        }
        btnShow.setOnClickListener {
            if (selected.isNotEmpty()) { Store.setZikrsHidden(selected.toList(), false); selecting = false; selected.clear(); refresh() }
        }

        view.findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(ctx)
            this.adapter = adapter
            setHasFixedSize(true)
            addItemDecoration(Gap(8))
            itemAnimator = null
        }
        refresh()
    }

    companion object {
        fun of(id: String) = FolderFragment().apply { arguments = bundleOf("id" to id) }
    }
}