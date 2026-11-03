package com.azkar.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.AzkarContent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LibraryFragment : Fragment(R.layout.fragment_library), HandlesOwnInsets {
    private val viewModel: AzkarContentViewModel by viewModels()

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

        fun build(q: String, content: AzkarContent): List<Card> {
            val query = q.trim().lowercase()
            val counts = content.zikrs.groupingBy { it.folderId }.eachCount()
            val out = ArrayList<Card>()
            content.folders.forEach { f ->
                if (query.isNotEmpty() && !f.name.lowercase().contains(query)) return@forEach
                val isDefault = com.azkar.app.data.Store.isDefaultFolder(f.id)
                out.add(Card(f.id, f.name, azkarCount(ctx, counts[f.id] ?: 0), f.icon, if (isDefault) R.drawable.ic_book_1p75 else R.drawable.ic_folder_1p75,
                    if (isDefault) teal else gold, false) { nav().push(FolderFragment.of(f.id)) })
            }
            val anytime = counts[null] ?: 0
            if (anytime > 0 && (query.isEmpty() || "anytime".contains(query))) {
                out.add(Card("anytime", ctx.getString(R.string.anytime), azkarCount(ctx, anytime), null, R.drawable.ic_folder_1p75, gold, false) {
                    nav().push(FolderFragment.of("anytime"))
                })
            }
            if (query.isEmpty()) {
                out.add(Card("new", ctx.getString(R.string.new_folder), "", null, R.drawable.ic_plus_2, gold, true) {
                    promptNewFolder { _ ->
                        viewModel.content.value?.let { adapter.submitList(build(search.text.toString(), it)) }
                    }
                })
            }
            return out
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.content.collect { content ->
                    if (content != null) adapter.submitList(build(search.text.toString(), content))
                }
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.content.value?.let { adapter.submitList(build(s?.toString() ?: "", it)) }
            }
        })
    }
}