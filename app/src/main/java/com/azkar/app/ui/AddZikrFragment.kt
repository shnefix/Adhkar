package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.azkar.app.R
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
            viewModel.add(text, form.n, form.sel, newName.text.toString())
            nav().pop()
        }
    }

    companion object {
        /** [folder]: preselect this folder; null selects "+ New folder" like the original screen. */
        fun of(folder: String?) = AddZikrFragment().apply { arguments = bundleOf("folder" to folder) }
    }
}
