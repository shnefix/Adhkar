package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.Builtins
import com.azkar.app.data.Folder
import com.azkar.app.data.Placement
import com.azkar.app.data.Store

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        bindNav(view, Tab.HOME)
        view.findViewById<View>(R.id.btn_notifications).setOnClickListener { nav().push(RemindersFragment()) }
        view.findViewById<View>(R.id.btn_customize).setOnClickListener { nav().push(CustomizeHomeFragment()) }
        val sections = view.findViewById<LinearLayout>(R.id.sections)

        fun open(f: Folder) {
            when {
                Store.zikrsIn(f.id).isEmpty() -> nav().push(EditFolderFragment.of(f.id))
                Store.readerItems(f.id).isEmpty() -> {
                    Toast.makeText(ctx, "All azkar in this folder are hidden.", Toast.LENGTH_SHORT).show()
                    nav().push(FolderFragment.of(f.id))
                }
                else -> nav().push(ReaderFragment.of(f.id))
            }
        }

        fun card(f: Folder): View {
            val c = Builtins.byKey(f.id)
            return if (c != null) {
                layoutInflater.inflate(R.layout.item_home_collection, null, false).apply {
                    Ui.folderIcon(findViewById(R.id.ic), findViewById(R.id.emoji), f.icon, c.icon)
                    findViewById<TextView>(R.id.title).text = f.name
                    findViewById<TextView>(R.id.sub_ar).text = if (f.name == c.title) c.homeArabic else ""
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

        val hidden = Store.hidden()
        val shown = Store.homeOrder().filter { it !in hidden }.mapNotNull { Store.folder(it) }
        val collections = shown.filter { Builtins.byKey(it.id) != null }
        val mine = shown.filter { Builtins.byKey(it.id) == null }

        fun section(label: String?, cards: List<View>): View? {
            if (cards.isEmpty()) return null
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            if (label != null) box.addView(Ui.caps(ctx, label))
            box.addView(Ui.grid(ctx, cards, 2, 12), Ui.lp(top = if (label != null) 12 else 0, ctx = ctx))
            return box
        }

        val daily = section("Daily collections", collections.map { card(it) })
        val own = section(null, mine.map { card(it) })
        val parts: List<View?> = when (Store.placement()) {
            Placement.TOP -> listOf(own, daily)
            Placement.BOTTOM -> listOf(daily, own)
            Placement.MIXED -> listOf(section("Daily collections", shown.map { card(it) }))
        }
        parts.filterNotNull().forEachIndexed { i, v -> sections.addView(v, Ui.lp(top = if (i == 0) 16 else 20, ctx = ctx)) }
    }
}