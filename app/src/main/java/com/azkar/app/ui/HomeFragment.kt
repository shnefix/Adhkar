package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.azkar.app.R
import com.azkar.app.data.AzkarContent
import com.azkar.app.data.Folder
import com.azkar.app.data.Store
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home), HandlesOwnInsets {
    private val viewModel: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        bindNav(view, Tab.HOME)
        view.findViewById<View>(R.id.btn_widgets).setOnClickListener { nav().push(WidgetEditorFragment()) }
        view.findViewById<View>(R.id.btn_notifications).setOnClickListener { nav().push(RemindersFragment()) }
        view.findViewById<View>(R.id.btn_settings).setOnClickListener { nav().push(SettingsFragment()) }
        view.findViewById<View>(R.id.btn_customize).setOnClickListener { nav().push(CustomizeHomeFragment()) }
        val sections = view.findViewById<LinearLayout>(R.id.sections)

        fun render(content: AzkarContent) {
            sections.removeAllViews()

            fun open(f: Folder) {
                when {
                    Store.zikrsIn(f.id).isEmpty() -> nav().push(EditFolderFragment.of(f.id))
                    Store.readerItems(f.id).isEmpty() -> {
                        Toast.makeText(ctx, R.string.all_hidden_toast, Toast.LENGTH_SHORT).show()
                        nav().push(FolderFragment.of(f.id))
                    }
                    else -> nav().push(ReaderFragment.of(f.id))
                }
            }

            fun card(f: Folder): View {
                return if (Store.isDefaultFolder(f.id)) {
                    layoutInflater.inflate(R.layout.item_home_collection, null, false).apply {
                        Ui.folderIcon(findViewById(R.id.ic), findViewById(R.id.emoji), f.icon, R.drawable.ic_book_1p75)
                        findViewById<TextView>(R.id.title).text = f.name
                        findViewById<TextView>(R.id.sub_ar).text = ""
                        setOnClickListener { open(f) }
                    }
                } else {
                    layoutInflater.inflate(R.layout.item_home_folder, null, false).apply {
                        Ui.folderIcon(findViewById(R.id.ic), findViewById(R.id.emoji), f.icon, R.drawable.ic_folder_1p75)
                        findViewById<TextView>(R.id.title).text = f.name
                        setOnClickListener { open(f) }
                    }
                }
            }

            val foldersById = content.folders.associateBy { it.id }
            val hidden = Store.hidden()
            val shown = Store.homeOrder().filter { it !in hidden }.mapNotNull { foldersById[it] }

            fun section(label: String?, cards: List<View>): View? {
                if (cards.isEmpty()) return null
                val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                if (label != null) box.addView(Ui.caps(ctx, label))
                box.addView(Ui.grid(ctx, cards, 2, 12), Ui.lp(top = if (label != null) 12 else 0, ctx = ctx))
                return box
            }

            // One section in the order set in Customize (drag); built-in and own folders mix freely.
            val parts: List<View?> = listOf(section(getString(R.string.daily_collections), shown.map { card(it) }))
            parts.filterNotNull().forEachIndexed { i, v ->
                sections.addView(v, Ui.lp(top = if (i == 0) 16 else 20, ctx = ctx))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.content.collect { content ->
                    if (content != null) render(content)
                }
            }
        }
    }
}