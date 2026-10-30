package com.azkar.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.Builtins
import com.azkar.app.data.Store

class LibraryFragment : Fragment(R.layout.fragment_library) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        bindNav(view, Tab.LIBRARY)
        val list = view.findViewById<LinearLayout>(R.id.list)
        val search = view.findViewById<EditText>(R.id.search)

        fun render(q: String) {
            list.removeAllViews()
            val query = q.trim().lowercase()
            val shown = Builtins.library.filter {
                query.isEmpty() || it.title.lowercase().contains(query) || it.libraryArabic.contains(query)
            }
            val mine = Store.libraryZikrs().filter { z ->
                query.isEmpty() || z.arabic.contains(query) || z.note.lowercase().contains(query) ||
                    Store.folderName(z.folderId).lowercase().contains(query)
            }
            shown.forEachIndexed { i, c ->
                val v = layoutInflater.inflate(R.layout.item_library, list, false)
                v.findViewById<ImageView>(R.id.ic).setImageResource(c.icon)
                v.findViewById<TextView>(R.id.title).text = c.title
                v.findViewById<TextView>(R.id.sub_ar).text = c.libraryArabic
                v.setOnClickListener { nav().push(ReaderFragment.of(c.key)) }
                list.addView(v, Ui.lp(top = if (i > 0) 8 else 0, ctx = ctx))
            }
            if (mine.isNotEmpty()) {
                list.addView(Ui.caps(ctx, "My azkar"), Ui.lp(top = if (shown.isNotEmpty()) 20 else 0, ctx = ctx))
                mine.forEachIndexed { i, z ->
                    val v = layoutInflater.inflate(R.layout.item_library_zikr, list, false)
                    v.findViewById<TextView>(R.id.chip).apply {
                        text = Store.folderName(z.folderId)
                        setTextColor(ctx.getColor(if (z.folderId == null || Store.isBuiltinFolder(z.folderId)) R.color.teal else R.color.gold))
                    }
                    v.findViewById<TextView>(R.id.rep).text = "×${z.repeat}"
                    v.findViewById<TextView>(R.id.ar).text = z.arabic
                    v.findViewById<TextView>(R.id.note).apply {
                        text = z.note
                        visibility = if (z.note.isBlank()) View.GONE else View.VISIBLE
                    }
                    v.setOnClickListener { nav().push(ReaderFragment.of(Store.readerKey(z), Store.readerIndex(z))) }
                    v.findViewById<View>(R.id.edit).setOnClickListener { nav().push(EditZikrFragment.of(z.id)) }
                    list.addView(v, Ui.lp(top = if (i > 0) 8 else 10, ctx = ctx))
                }
            }
        }
        render("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = render(s?.toString() ?: "")
        })
    }
}
