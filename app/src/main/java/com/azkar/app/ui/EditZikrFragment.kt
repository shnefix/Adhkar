package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.azkar.app.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditZikrFragment : Fragment(R.layout.fragment_edit_zikr) {
    private val viewModel: ZikrEditorViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val z = viewModel.zikr(requireArguments().getString("id") ?: "")
        if (z == null) { nav().pop(); return }
        val form = ZikrForm(view, allowNew = false)
        form.arabic.setText(z.arabic)
        form.n = z.repeat
        form.sel = z.folderId
        form.setTitle(z.title, z.titleBelow)

        val swHide = view.findViewById<ToggleSwitch>(R.id.sw_hide)
        val hideSub = view.findViewById<TextView>(R.id.hide_sub)
        fun renderHide() { hideSub.text = getString(if (swHide.on) R.string.hide_on else R.string.hide_off) }
        swHide.on = viewModel.isHidden(z.id)
        renderHide()
        swHide.onToggle = { on -> viewModel.setHidden(z.id, on); renderHide() }

        view.findViewById<View>(R.id.btn_close).setOnClickListener { nav().pop() }
        view.findViewById<View>(R.id.btn_save).setOnClickListener {
            val text = form.arabic.text.toString().trim()
            if (text.isEmpty()) { form.arabic.requestFocus(); return@setOnClickListener }
            viewModel.update(z.copy(arabic = text, repeat = form.n, folderId = form.sel, title = form.title, titleBelow = form.titleBelow))
            nav().pop()
        }
        view.findViewById<View>(R.id.btn_delete).setOnClickListener {
            viewModel.delete(z.id)
            nav().pop()
        }
    }

    companion object {
        fun of(id: String) = EditZikrFragment().apply { arguments = Bundle().apply { putString("id", id) } }
    }
}
