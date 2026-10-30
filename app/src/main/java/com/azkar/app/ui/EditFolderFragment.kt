package com.azkar.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.Store

class EditFolderFragment : Fragment(R.layout.fragment_edit_folder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val id = requireArguments().getString("id") ?: ""
        val folder = Store.folder(id)
        if (folder == null) { nav().pop(); return }

        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }

        // Name (saved as you type; an empty name is ignored)
        val name = view.findViewById<EditText>(R.id.fname)
        name.setText(folder.name)
        name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString()?.trim().orEmpty()
                if (t.isNotEmpty()) Store.renameFolder(id, t)
            }
        })

        // Show on Home
        val sw = view.findViewById<ToggleSwitch>(R.id.sw_home)
        val homeSub = view.findViewById<TextView>(R.id.home_sub)
        fun renderHome() {
            sw.on = id !in Store.hidden()
            homeSub.text = if (sw.on) "Appears on your Home screen" else "Hidden from Home"
        }
        renderHome()
        sw.onToggle = { on ->
            Store.setHidden(if (on) Store.hidden() - id else Store.hidden() + id)
            renderHome()
        }

        // Order list
        val rows = view.findViewById<LinearLayout>(R.id.rows)
        val empty = view.findViewById<View>(R.id.empty)
        val label = view.findViewById<TextView>(R.id.order_label)

        fun renderRows() {
            val list = Store.zikrsIn(id)
            label.text = "Order · " + list.size + if (list.size == 1) " zikr" else " azkar"
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            rows.removeAllViews()
            val last = list.size - 1
            list.forEachIndexed { idx, z ->
                val v = layoutInflater.inflate(R.layout.item_folder_zikr, rows, false)
                v.findViewById<TextView>(R.id.ar).text = z.arabic
                v.findViewById<TextView>(R.id.line).text = "×${z.repeat}" + if (z.note.isBlank()) "" else " · ${z.note}"
                val up = v.findViewById<View>(R.id.up)
                val down = v.findViewById<View>(R.id.down)
                up.alpha = if (idx == 0) 0.35f else 1f
                down.alpha = if (idx == last) 0.35f else 1f
                fun move(d: Int) {
                    val ids = list.map { it.id }.toMutableList()
                    val t = ids[idx]; ids[idx] = ids[idx + d]; ids[idx + d] = t
                    Store.reorderFolder(id, ids)
                    renderRows()
                }
                up.setOnClickListener { if (idx > 0) move(-1) }
                down.setOnClickListener { if (idx < last) move(1) }
                v.findViewById<View>(R.id.edit).setOnClickListener { nav().push(EditZikrFragment.of(z.id)) }
                v.findViewById<View>(R.id.remove).setOnClickListener { Store.unfile(z.id); renderRows() }
                rows.addView(v, Ui.lp(top = if (idx > 0) 8 else 0, ctx = ctx))
            }
        }
        renderRows()

        view.findViewById<View>(R.id.btn_add).setOnClickListener { nav().push(AddZikrFragment.of(id)) }
        view.findViewById<View>(R.id.btn_delete).setOnClickListener {
            Store.deleteFolder(id)
            nav().pop()
        }
    }

    companion object {
        fun of(id: String) = EditFolderFragment().apply { arguments = bundleOf("id" to id) }
    }
}
