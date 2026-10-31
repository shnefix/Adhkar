package com.azkar.app.ui

import android.content.res.ColorStateList
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

        fun row(i: Int, icon: Int, tint: Int, title: String, sub: String, arabic: Boolean, onClick: () -> Unit) {
            val v = layoutInflater.inflate(R.layout.item_library, list, false)
            v.findViewById<ImageView>(R.id.ic).apply {
                setImageResource(icon)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tint))
            }
            v.findViewById<TextView>(R.id.title).text = title
            v.findViewById<TextView>(R.id.sub_ar).apply {
                text = sub
                if (!arabic) {
                    textSize = 14f
                    typeface = Ui.font(ctx, R.font.dm_sans_regular)
                    textDirection = View.TEXT_DIRECTION_LTR
                }
            }
            v.setOnClickListener { onClick() }
            list.addView(v, Ui.lp(top = if (i > 0) 8 else 0, ctx = ctx))
        }

        fun render(q: String) {
            list.removeAllViews()
            val query = q.trim().lowercase()
            var i = 0
            Store.folders().forEach { f ->
                val c = Builtins.byKey(f.id)
                val unchanged = c != null && f.name == c.title
                val match = query.isEmpty() || f.name.lowercase().contains(query) || (unchanged && c!!.libraryArabic.contains(query))
                if (!match) return@forEach
                row(
                    i++, c?.icon ?: R.drawable.ic_folder_1p75,
                    ctx.getColor(if (c != null) R.color.teal else R.color.gold), f.name,
                    if (unchanged) c!!.libraryArabic else count(Store.zikrsIn(f.id).size), unchanged
                ) { nav().push(FolderFragment.of(f.id)) }
            }
            val anytimeN = Store.zikrs().count { it.folderId == null }
            if (anytimeN > 0 && (query.isEmpty() || "anytime".contains(query))) {
                row(i++, R.drawable.ic_folder_1p75, ctx.getColor(R.color.gold), "Anytime", count(anytimeN), false) {
                    nav().push(FolderFragment.of("anytime"))
                }
            }
            if (query.isEmpty()) {
                row(i++, R.drawable.ic_plus_2, ctx.getColor(R.color.gold), "New folder", "Create a folder", false) {
                    promptNewFolder { _ -> render("") }
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