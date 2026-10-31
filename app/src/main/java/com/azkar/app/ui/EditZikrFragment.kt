package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.Store

class EditZikrFragment : Fragment(R.layout.fragment_edit_zikr) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val z = Store.zikr(requireArguments().getString("id") ?: "")
        if (z == null) { nav().pop(); return }
        val form = ZikrForm(view, allowNew = false)
        form.arabic.setText(z.arabic)
        form.note?.setText(z.note)
        form.n = z.repeat
        form.sel = z.folderId

        view.findViewById<View>(R.id.btn_close).setOnClickListener { nav().pop() }
        view.findViewById<View>(R.id.btn_save).setOnClickListener {
            val text = form.arabic.text.toString().trim()
            if (text.isEmpty()) { form.arabic.requestFocus(); return@setOnClickListener }
            Store.updateZikr(z.copy(arabic = text, note = form.note?.text?.toString()?.trim() ?: z.note, repeat = form.n, folderId = form.sel))
            nav().pop()
        }
        view.findViewById<View>(R.id.btn_delete).setOnClickListener {
            Store.deleteZikr(z.id)
            nav().pop()
        }
    }

    companion object {
        fun of(id: String) = EditZikrFragment().apply { arguments = bundleOf("id" to id) }
    }
}
