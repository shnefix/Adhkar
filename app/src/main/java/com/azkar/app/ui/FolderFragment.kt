package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.Visibility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Lists a folder's azkar. Tap = read from there. Long-press or Select = hide or show several at once. */
@AndroidEntryPoint
class FolderFragment : Fragment(R.layout.fragment_folder_view) {
    private val viewModel: FolderViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val id = viewModel.id
        val selected = viewModel.selected
        var current: FolderState? = null

        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }
        val title = view.findViewById<TextView>(R.id.title)
        view.findViewById<View>(R.id.btn_edit).apply {
            if (id == FolderState.ANYTIME) visibility = View.INVISIBLE
            else setOnClickListener { nav().push(EditFolderFragment.of(id)) }
        }

        val empty = view.findViewById<View>(R.id.empty)
        val info = view.findViewById<TextView>(R.id.hidden_info)
        val btnSelect = view.findViewById<TextView>(R.id.btn_select)
        val bar = view.findViewById<View>(R.id.action_bar)
        val selCount = view.findViewById<TextView>(R.id.sel_count)
        val btnAll = view.findViewById<View>(R.id.btn_all)
        val btnHide = view.findViewById<View>(R.id.btn_hide)
        val btnShow = view.findViewById<View>(R.id.btn_show)
        val btnShowHidden = view.findViewById<View>(R.id.btn_show_hidden)
        val iconShowHidden = view.findViewById<android.widget.ImageView>(R.id.icon_show_hidden)
        var shown: List<com.azkar.app.data.Zikr> = emptyList()

        var refresh: () -> Unit = {}
        // While selecting, Back leaves selection mode instead of leaving the folder.
        val backCb = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { viewModel.selecting = false; selected.clear(); refresh() }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCb)

        val adapter = ZikrAdapter(
            onTap = { row ->
                val state = current
                if (viewModel.selecting) { if (!selected.remove(row.id)) selected.add(row.id); refresh() }
                else if (row.hidden) Toast.makeText(ctx, R.string.hidden_skipped_toast, Toast.LENGTH_SHORT).show()
                else if (state != null) nav().push(ReaderFragment.of(id, Visibility.readerIndex(state.zikrs, state.hidden, row.id).coerceAtLeast(0)))
            },
            onLong = { row -> if (!viewModel.selecting) { viewModel.selecting = true; selected.add(row.id); refresh() } },
            onEdit = { zid -> nav().push(EditZikrFragment.of(zid)) }
        )

        refresh = refresh@{
            val state = current ?: return@refresh
            val selecting = viewModel.selecting
            val hidden = state.hidden
            val list = if (viewModel.showHidden) state.zikrs else state.zikrs.filter { it.id !in hidden }
            shown = list
            selected.retainAll(list.map { it.id }.toSet())
            val nHidden = state.zikrs.count { it.id in hidden }
            btnShowHidden.visibility = if (nHidden == 0) View.GONE else View.VISIBLE
            btnShowHidden.setBackgroundResource(if (viewModel.showHidden) R.drawable.bg_gold22 else R.drawable.bg_card22)
            iconShowHidden.setImageResource(if (viewModel.showHidden) R.drawable.ic_eyeoff_1p75 else R.drawable.ic_eye_1p75)
            iconShowHidden.imageTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(if (viewModel.showHidden) R.color.bg else R.color.text))
            title.text = state.name
            empty.visibility = if (state.zikrs.isEmpty()) View.VISIBLE else View.GONE
            info.text = if (nHidden == 0) "" else resources.getQuantityString(R.plurals.hidden_count, nHidden, nHidden)
            btnSelect.text = getString(if (selecting) R.string.cancel else R.string.select)
            btnSelect.visibility = if (list.isEmpty()) View.INVISIBLE else View.VISIBLE
            bar.visibility = if (selecting) View.VISIBLE else View.GONE
            selCount.text = resources.getQuantityString(R.plurals.selected_count, selected.size, selected.size)
            btnHide.alpha = if (selected.isEmpty()) 0.4f else 1f
            btnShow.alpha = if (selected.isEmpty()) 0.4f else 1f
            backCb.isEnabled = selecting
            adapter.submitList(list.map { ZRow(it.id, it.arabic, it.repeat, it.note, it.id in hidden, selecting, it.id in selected, if (it.titlePos == com.azkar.app.data.TitlePos.HIDDEN) "" else it.title) })
        }

        btnShowHidden.setOnClickListener { viewModel.showHidden = !viewModel.showHidden; refresh() }
        fun endSelection() { viewModel.selecting = false; selected.clear(); refresh() }
        btnSelect.setOnClickListener { viewModel.selecting = !viewModel.selecting; selected.clear(); refresh() }
        btnAll.setOnClickListener {
            val ids = shown.map { it.id }
            val all = selected.size == ids.size
            selected.clear()
            if (!all) selected.addAll(ids)
            refresh()
        }
        btnHide.setOnClickListener {
            if (selected.isNotEmpty()) { viewModel.setHidden(selected.toList(), true); endSelection() }
        }
        btnShow.setOnClickListener {
            if (selected.isNotEmpty()) { viewModel.setHidden(selected.toList(), false); endSelection() }
        }

        view.findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(ctx)
            this.adapter = adapter
            setHasFixedSize(true)
            addItemDecoration(Gap(8))
            itemAnimator = null
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state == null || current?.exists == false) return@collect
                    if (!state.exists) { current = state; nav().pop(); return@collect }   // deleted from the editor
                    current = state
                    refresh()
                }
            }
        }
    }

    companion object {
        fun of(id: String) = FolderFragment().apply { arguments = Bundle().apply { putString("id", id) } }
    }
}
