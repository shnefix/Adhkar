package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.azkar.app.R
import com.azkar.app.data.AppPrefs
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
        form.setTitle(z.title, z.titlePos)

        val swHide = view.findViewById<ToggleSwitch>(R.id.sw_hide)
        val hideSub = view.findViewById<TextView>(R.id.hide_sub)
        fun renderHide() { hideSub.text = getString(if (swHide.on) R.string.hide_on else R.string.hide_off) }
        swHide.on = viewModel.isHidden(z.id)
        renderHide()
        swHide.onToggle = { on -> viewModel.setHidden(z.id, on); renderHide() }

        // Only a zikr that has a source gets the switch; Settings and the folder can still hide all sources.
        if (!z.source.isEmpty) {
            view.findViewById<View>(R.id.source_row).visibility = View.VISIBLE
            val swSrc = view.findViewById<ToggleSwitch>(R.id.sw_source)
            val srcSub = view.findViewById<TextView>(R.id.source_sub)
            fun renderSrc() {
                srcSub.text = getString(when {
                    !AppPrefs.sourcesOn() -> R.string.sources_off_app
                    z.folderId != null && AppPrefs.folderSourcesHidden(z.folderId) -> R.string.sources_off_folder
                    swSrc.on -> R.string.zikr_source_on
                    else -> R.string.zikr_source_off
                })
            }
            swSrc.on = !AppPrefs.zikrSourceHidden(z.id)
            renderSrc()
            swSrc.onToggle = { on -> AppPrefs.setZikrSourceHidden(z.id, !on); renderSrc() }
        }

        view.findViewById<View>(R.id.btn_close).setOnClickListener { nav().pop() }
        view.findViewById<View>(R.id.btn_save).setOnClickListener {
            val text = form.arabic.text.toString().trim()
            if (text.isEmpty()) { form.arabic.requestFocus(); return@setOnClickListener }
            viewModel.update(z.copy(arabic = text, repeat = form.n, folderId = form.sel, title = form.title, titlePos = form.titlePos))
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
