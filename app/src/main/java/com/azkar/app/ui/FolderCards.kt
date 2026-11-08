package com.azkar.app.ui

import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R

/** "1 zikr" / "5 azkar", with Arabic's own plural forms on an Arabic phone. */
fun azkarCount(ctx: android.content.Context, n: Int): String = ctx.resources.getQuantityString(R.plurals.azkar_count, n, n)

/** One folder card (Library). [isNew] draws the dashed "New folder" card. */
data class Card(
    val id: String, val name: String, val count: String, val emoji: String?, val vector: Int,
    val tint: Int, val isNew: Boolean, val onMenu: (() -> Unit)? = null, val onClick: () -> Unit
)

class CardAdapter : ListAdapter<Card, CardAdapter.VH>(object : DiffUtil.ItemCallback<Card>() {
    override fun areItemsTheSame(a: Card, b: Card) = a.id == b.id
    override fun areContentsTheSame(a: Card, b: Card) =
        a.name == b.name && a.count == b.count && a.emoji == b.emoji && a.vector == b.vector && a.tint == b.tint && a.isNew == b.isNew && (a.onMenu == null) == (b.onMenu == null)
}) {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ic: ImageView = v.findViewById(R.id.ic)
        val emoji: TextView = v.findViewById(R.id.emoji)
        val name: TextView = v.findViewById(R.id.name)
        val count: TextView = v.findViewById(R.id.count)
        val menu: View = v.findViewById(R.id.menu)
        init {
            v.setOnClickListener { val p = bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) getItem(p).onClick() }
            menu.setOnClickListener { val p = bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) getItem(p).onMenu?.invoke() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, t: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_folder_card, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = getItem(position)
        val ctx = h.itemView.context
        Ui.folderIcon(h.ic, h.emoji, c.emoji, c.vector)
        ImageViewCompat.setImageTintList(h.ic, ColorStateList.valueOf(c.tint))
        h.itemView.setBackgroundResource(if (c.isNew) R.drawable.bg_dashgold24 else R.drawable.bg_card24)
        Ui.folderName(h.name, c.name, arabic = !c.isNew)
        h.name.setTextColor(ctx.getColor(if (c.isNew) R.color.gold else R.color.text))
        h.count.text = c.count
        h.menu.visibility = if (c.onMenu != null) View.VISIBLE else View.GONE
        h.count.visibility = if (c.count.isEmpty()) View.GONE else View.VISIBLE
    }
}

/** 12dp gaps between cards in a 2-column grid. */
class GridGap(private val dp: Int, private val span: Int = 2) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(out: Rect, v: View, parent: RecyclerView, s: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(v)
        if (pos == RecyclerView.NO_POSITION) return
        val px = (dp * v.resources.displayMetrics.density).toInt()
        val col = pos % span
        out.left = if (col == 0) 0 else px / 2
        out.right = if (col == 0) px / 2 else 0
        out.top = if (pos >= span) px else 0
    }
}