package com.azkar.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.Builtins
import com.azkar.app.data.Store

class LibraryFragment : Fragment(R.layout.fragment_library) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        bindNav(view, Tab.LIBRARY)
        val search = view.findViewById<EditText>(R.id.search)
        val adapter = LibraryAdapter()
        view.findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(ctx)
            this.adapter = adapter
            setHasFixedSize(true)
            addItemDecoration(Gap(8))
            itemAnimator = null
        }
        val teal = ctx.getColor(R.color.teal)
        val gold = ctx.getColor(R.color.gold)

        fun count(n: Int) = if (n == 1) "1 zikr" else "$n azkar"

        fun build(q: String): List<LibRow> {
            val query = q.trim().lowercase()
            val counts = Store.zikrCounts()          // one pass, from memory
            val rows = ArrayList<LibRow>()
            Store.folders().forEach { f ->
                val c = Builtins.byKey(f.id)
                val unchanged = c != null && f.name == c.title
                val match = query.isEmpty() || f.name.lowercase().contains(query) || (unchanged && c!!.libraryArabic.contains(query))
                if (!match) return@forEach
                rows.add(LibRow(
                    f.id, f.name, if (unchanged) c!!.libraryArabic else count(counts[f.id] ?: 0), unchanged,
                    f.icon, c?.icon ?: R.drawable.ic_folder_1p75, if (c != null) teal else gold
                ) { nav().push(FolderFragment.of(f.id)) })
            }
            val anytimeN = counts[null] ?: 0
            if (anytimeN > 0 && (query.isEmpty() || "anytime".contains(query))) {
                rows.add(LibRow("anytime", "Anytime", count(anytimeN), false, null, R.drawable.ic_folder_1p75, gold) {
                    nav().push(FolderFragment.of("anytime"))
                })
            }
            if (query.isEmpty()) {
                rows.add(LibRow("new", "New folder", "Create a folder", false, null, R.drawable.ic_plus_2, gold) {
                    promptNewFolder { _ -> adapter.submitList(build(search.text.toString())) }
                })
            }
            return rows
        }

        adapter.submitList(build(""))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { adapter.submitList(build(s?.toString() ?: "")) }
        })
    }
}