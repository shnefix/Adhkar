package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.Builtins
import com.azkar.app.data.Collection
import com.azkar.app.data.Folder
import com.azkar.app.data.Placement
import com.azkar.app.data.Store

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        bindNav(view, Tab.HOME)
        view.findViewById<View>(R.id.btn_customize).setOnClickListener { nav().push(CustomizeHomeFragment()) }

        val sections = view.findViewById<LinearLayout>(R.id.sections)

        fun collectionCard(c: Collection): View {
            val v = layoutInflater.inflate(R.layout.item_home_collection, null, false)
            v.findViewById<ImageView>(R.id.ic).setImageResource(c.icon)
            v.findViewById<TextView>(R.id.title).text = c.title
            v.findViewById<TextView>(R.id.sub_ar).text = c.homeArabic
            v.setOnClickListener { nav().push(ReaderFragment.of(c.key)) }
            return v
        }

        fun folderCard(f: Folder): View {
            val v = layoutInflater.inflate(R.layout.item_home_folder, null, false)
            v.findViewById<TextView>(R.id.title).text = f.name
            v.setOnClickListener {
                if (Store.zikrsIn(f.id).isEmpty()) nav().push(EditFolderFragment.of(f.id))
                else nav().push(ReaderFragment.of(f.id))
            }
            return v
        }

        val hidden = Store.hidden()
        val order = Store.homeOrder().filter { it !in hidden }
        val collections = order.mapNotNull { k -> Builtins.homeCollections.firstOrNull { it.key == k } }
        val folders = order.mapNotNull { k -> Store.folder(k) }

        fun section(label: String?, cards: List<View>): View? {
            if (cards.isEmpty()) return null
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            if (label != null) box.addView(Ui.caps(ctx, label))
            box.addView(Ui.grid(ctx, cards, 2, 12), Ui.lp(top = if (label != null) 12 else 0, ctx = ctx))
            return box
        }

        val daily = section("Daily collections", collections.map { collectionCard(it) })
        val mine = section(null, folders.map { folderCard(it) })

        val parts: List<View?> = when (Store.placement()) {
            Placement.TOP -> listOf(mine, daily)
            Placement.BOTTOM -> listOf(daily, mine)
            Placement.MIXED -> listOf(
                section("Daily collections", order.mapNotNull { k ->
                    Builtins.homeCollections.firstOrNull { it.key == k }?.let { collectionCard(it) }
                        ?: Store.folder(k)?.let { folderCard(it) }
                })
            )
        }
        parts.filterNotNull().forEachIndexed { i, v -> sections.addView(v, Ui.lp(top = if (i == 0) 16 else 20, ctx = ctx)) }
    }
}
