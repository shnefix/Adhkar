package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.azkar.app.R
import com.azkar.app.data.Store
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AddZikrFragment : Fragment(R.layout.fragment_add_zikr) {
    private val viewModel: ZikrEditorViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val newBox = view.findViewById<View>(R.id.newname_box)
        val newName = view.findViewById<EditText>(R.id.newname)
        val form = ZikrForm(view, allowNew = true) { newBox.visibility = if (it == "new") View.VISIBLE else View.GONE }
        form.n = 1
        form.sel = arguments?.getString("folder") ?: "new"

        view.findViewById<View>(R.id.btn_close).setOnClickListener { nav().pop() }
        view.findViewById<View>(R.id.btn_save).setOnClickListener {
            val text = form.arabic.text.toString().trim()
            if (text.isEmpty()) { form.arabic.requestFocus(); return@setOnClickListener }
            val save = {
                viewModel.add(text, form.n, form.sel, newName.text.toString(), form.title, form.titlePos, form.titleBelow)
                nav().pop()
            }
            // Same text in the same folder updates that zikr instead of adding a copy: say so first.
            val dup = viewModel.existing(text, form.sel, newName.text.toString())
            if (dup == null) save()
            else AlertDialog.Builder(requireContext())
                .setTitle(R.string.zikr_exists_title)
                .setMessage(getString(R.string.zikr_exists_message, Store.folderName(dup.folderId), dup.repeat))
                .setPositiveButton(R.string.update) { _, _ -> save() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    companion object {
        /** [folder]: preselect this folder; null selects "+ New folder" like the original screen. */
        fun of(folder: String?) = AddZikrFragment().apply { arguments = Bundle().apply { putString("folder", folder) } }
    }
}
