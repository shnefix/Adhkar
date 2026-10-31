package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.Store

/** Lists the azkar of one folder; tapping one opens the normal reader inside that folder. */
class FolderFragment : Fragment(R.layout.fragment_folder_view) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val id = requireArguments().getString("id") ?: "anytime"
        if (id != "anytime" && Store.folder(id) == null) { nav().pop(); return }   // deleted from the editor

        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }
        view.findViewById<TextView>(R.id.title).text = Store.folderName(id)
        view.findViewById<View>(R.id.btn_edit).apply {
            if (id == "anytime") visibility = View.INVISIBLE
            else setOnClickListener { nav().push(EditFolderFragment.of(id)) }
        }

        val items = if (id == "anytime") Store.zikrs().filter { it.folderId == null } else Store.zikrsIn(id)
        view.findViewById<View>(R.id.empty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        val adapter = ZikrAdapter(
            onOpen = { idx -> nav().push(ReaderFragment.of(id, idx)) },
            onEdit = { zid -> nav().push(EditZikrFragment.of(zid)) }
        )
        view.findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
            setHasFixedSize(true)
            addItemDecoration(Gap(8))
            itemAnimator = null
        }
        adapter.submitList(items.map { ZRow(it.id, it.arabic, it.repeat, it.note) })
    }

    companion object {
        fun of(id: String) = FolderFragment().apply { arguments = bundleOf("id" to id) }
    }
}