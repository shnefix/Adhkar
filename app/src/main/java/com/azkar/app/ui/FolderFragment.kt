package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.Store

/** Lists the azkar of one folder; tapping one opens the normal reader inside that folder. */
class FolderFragment : Fragment(R.layout.fragment_folder_view) {

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

        val items = if (id == "anytime") Store.zikrs().filter { it.folderId == null } else Store.zikrsIn(id)
        val list = view.findViewById<LinearLayout>(R.id.list)
        view.findViewById<View>(R.id.empty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        items.forEachIndexed { idx, z ->
            val v = layoutInflater.inflate(R.layout.item_library_zikr, list, false)
            v.findViewById<View>(R.id.chip).visibility = View.GONE
            v.findViewById<TextView>(R.id.rep).apply { text = "×${z.repeat}"; (layoutParams as LinearLayout.LayoutParams).marginStart = 0 }
            v.findViewById<TextView>(R.id.ar).text = z.arabic
            v.findViewById<TextView>(R.id.note).apply {
                text = z.note
                visibility = if (z.note.isBlank()) View.GONE else View.VISIBLE
            }
            v.setOnClickListener { nav().push(ReaderFragment.of(id, idx)) }
            v.findViewById<View>(R.id.edit).setOnClickListener { nav().push(EditZikrFragment.of(z.id)) }
            list.addView(v, Ui.lp(top = if (idx > 0) 8 else 0, ctx = ctx))
        }
    }

    companion object {
        fun of(id: String) = FolderFragment().apply { arguments = bundleOf("id" to id) }
    }
}