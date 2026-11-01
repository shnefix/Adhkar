package com.azkar.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.Builtins
import com.azkar.app.data.Store

class LibraryFragment : Fragment(R.layout.fragment_library) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        bindNav(view, Tab.LIBRARY)
        val search = view.findViewById<EditText>(R.id.search)
        val adapter = CardAdapter()
        view.findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = GridLayoutManager(ctx, 2)
            this.adapter = adapter
            addItemDecoration(GridGap(12))
            setHasFixedSize(true)
            itemAnimator = null
        }
        val teal = ctx.getColor(R.color.teal)
        val gold = ctx.getColor(R.color.gold)

        fun build(q: String): List<Card> {
            val query = q.trim().lowercase()
            val counts = Store.zikrCounts()          // one pass, from memory
            val out = ArrayList<Card>()
            Store.folders().forEach { f ->
                if (query.isNotEmpty() && !f.name.lowercase().contains(query)) return@forEach
                val b = Builtins.byKey(f.id)
                out.add(Card(f.id, f.name, azkarCount(counts[f.id] ?: 0), f.icon, b?.icon ?: R.drawable.ic_folder_1p75,
                    if (b != null) teal else gold, false) { nav().push(FolderFragment.of(f.id)) })
            }
            val anytime = counts[null] ?: 0
            if (anytime > 0 && (query.isEmpty() || "anytime".contains(query))) {
                out.add(Card("anytime", "Anytime", azkarCount(anytime), null, R.drawable.ic_folder_1p75, gold, false) {
                    nav().push(FolderFragment.of("anytime"))
                })
            }
            if (query.isEmpty()) {
                out.add(Card("new", "New folder", "", null, R.drawable.ic_plus_2, gold, true) {
                    promptNewFolder { _ -> adapter.submitList(build(search.text.toString())) }
                })
            }
            return out
        }

        adapter.submitList(build(""))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { adapter.submitList(build(s?.toString() ?: "")) }
        })
    }
}