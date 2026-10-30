package com.azkar.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import android.content.res.ColorStateList
import com.azkar.app.R
import com.azkar.app.data.Builtins
import com.azkar.app.data.Store

class LibraryFragment : Fragment(R.layout.fragment_library) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        bindNav(view, Tab.LIBRARY)
        val list = view.findViewById<LinearLayout>(R.id.list)
        val search = view.findViewById<EditText>(R.id.search)

        fun count(n: Int) = if (n == 1) "1 zikr" else "$n azkar"

        fun render(q: String) {
            list.removeAllViews()
            val query = q.trim().lowercase()
            val shown = Builtins.library.filter {
                query.isEmpty() || it.title.lowercase().contains(query) || it.libraryArabic.contains(query)
            }
            shown.forEachIndexed { i, c ->
                val v = layoutInflater.inflate(R.layout.item_library, list, false)
                v.findViewById<ImageView>(R.id.ic).setImageResource(c.icon)
                v.findViewById<TextView>(R.id.title).text = c.title
                v.findViewById<TextView>(R.id.sub_ar).text = c.libraryArabic
                v.setOnClickListener { nav().push(ReaderFragment.of(c.key)) }
                list.addView(v, Ui.lp(top = if (i > 0) 8 else 0, ctx = ctx))
            }

            // Folders: yours (Family, Friday, ...) plus "Anytime" for azkar with no folder.
            val anytimeN = Store.zikrs().count { it.folderId == null }
            val folderRows = Store.folders().map { Triple(it.id, it.name, Store.zikrsIn(it.id).size) } +
                (if (anytimeN > 0) listOf(Triple("anytime", "Anytime", anytimeN)) else emptyList())
            val folders = folderRows.filter { query.isEmpty() || it.second.lowercase().contains(query) }
            if (folders.isNotEmpty()) {
                list.addView(Ui.caps(ctx, "My folders"), Ui.lp(top = if (shown.isNotEmpty()) 20 else 0, ctx = ctx))
                folders.forEachIndexed { i, (id, name, n) ->
                    val v = layoutInflater.inflate(R.layout.item_library, list, false)
                    v.findViewById<ImageView>(R.id.ic).apply {
                        setImageResource(R.drawable.ic_folder_1p75)
                        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(ctx.getColor(R.color.gold)))
                    }
                    v.findViewById<TextView>(R.id.title).text = name
                    v.findViewById<TextView>(R.id.sub_ar).apply {
                        text = count(n)
                        textSize = 14f
                        typeface = Ui.font(ctx, R.font.dm_sans_regular)
                        textDirection = View.TEXT_DIRECTION_LTR
                    }
                    v.setOnClickListener { nav().push(FolderFragment.of(id)) }
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
