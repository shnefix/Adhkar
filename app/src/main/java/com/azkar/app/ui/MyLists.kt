package com.azkar.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R

data class Tile(val id: String, val name: String, val icon: String?, val count: Int)

sealed class MyItem {
    abstract val key: String
    object Header : MyItem() { override val key = "header" }
    data class Folders(val tiles: List<Tile>) : MyItem() { override val key = "folders" }
    object SavedLabel : MyItem() { override val key = "label" }
    data class Saved(val id: String, val arabic: String, val repeat: Int, val note: String, val folder: String, val gold: Boolean) : MyItem() {
        override val key: String get() = "z$id"
    }
}

/** My azkar as one lazy list: header, folder grid, then only the saved rows on screen. */
class MyAdapter(
    private val onAdd: () -> Unit, private val onImport: () -> Unit, private val onExport: () -> Unit,
    private val onFolder: (String) -> Unit, private val onNewFolder: () -> Unit, private val onEdit: (String) -> Unit
) : ListAdapter<MyItem, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<MyItem>() {
    override fun areItemsTheSame(a: MyItem, b: MyItem) = a.key == b.key
    override fun areContentsTheSame(a: MyItem, b: MyItem) = a == b
}) {
    private class Plain(v: View) : RecyclerView.ViewHolder(v)
    private class SavedVH(v: View) : RecyclerView.ViewHolder(v) {
        val chip: TextView = v.findViewById(R.id.chip)
        val rep: TextView = v.findViewById(R.id.rep)
        val ar: TextView = v.findViewById(R.id.ar)
        val note: TextView = v.findViewById(R.id.note)
        val edit: View = v.findViewById(R.id.edit)
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is MyItem.Header -> 0
        is MyItem.Folders -> 1
        is MyItem.SavedLabel -> 2
        is MyItem.Saved -> 3
    }

    private fun wide() = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val d = ctx.resources.displayMetrics.density
        return when (type) {
            0 -> {
                val v = LayoutInflater.from(ctx).inflate(R.layout.item_my_header, parent, false)
                v.findViewById<View>(R.id.btn_add).setOnClickListener { onAdd() }
                v.findViewById<View>(R.id.btn_import).setOnClickListener { onImport() }
                v.findViewById<View>(R.id.btn_export).setOnClickListener { onExport() }
                Plain(v)
            }
            1 -> Plain(FrameLayout(ctx).apply { layoutParams = wide(); setPadding(0, (10 * d).toInt(), 0, 0) })
            2 -> Plain(Ui.caps(ctx, "Saved").apply { layoutParams = wide(); setPadding(0, (20 * d).toInt(), 0, (2 * d).toInt()) })
            else -> {
                val v = LayoutInflater.from(ctx).inflate(R.layout.item_saved, parent, false)
                (v.layoutParams as RecyclerView.LayoutParams).topMargin = (8 * d).toInt()
                SavedVH(v)
            }
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MyItem.Folders -> {
                val box = h.itemView as FrameLayout
                val ctx = box.context
                val inf = LayoutInflater.from(ctx)
                box.removeAllViews()
                val cells = item.tiles.map { t ->
                    inf.inflate(R.layout.item_folder_tile, null, false).apply {
                        Ui.folderIcon(findViewById(R.id.ic), findViewById(R.id.emoji), t.icon, R.drawable.ic_folder_1p75)
                        findViewById<TextView>(R.id.name).text = t.name
                        findViewById<TextView>(R.id.count).text = if (t.count == 1) "1 zikr" else "${t.count} azkar"
                        setOnClickListener { onFolder(t.id) }
                    }
                } + inf.inflate(R.layout.item_folder_new, null, false).apply { setOnClickListener { onNewFolder() } }
                box.addView(Ui.grid(ctx, cells, 3, 8))
            }
            is MyItem.Saved -> {
                val s = h as SavedVH
                s.chip.text = item.folder
                s.chip.setTextColor(s.itemView.context.getColor(if (item.gold) R.color.gold else R.color.teal))
                s.rep.text = "×${item.repeat}"
                s.ar.text = item.arabic
                s.note.text = item.note
                s.note.visibility = if (item.note.isBlank()) View.GONE else View.VISIBLE
                s.edit.setOnClickListener { onEdit(item.id) }
            }
            else -> {}
        }
    }
}