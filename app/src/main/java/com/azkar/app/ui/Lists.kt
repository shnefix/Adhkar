package com.azkar.app.ui

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R

/** Vertical gap between list rows. */
class Gap(private val dp: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(out: Rect, v: View, parent: RecyclerView, s: RecyclerView.State) {
        if (parent.getChildAdapterPosition(v) > 0) out.top = (dp * v.resources.displayMetrics.density).toInt()
    }
}

// ---------- Library: folder rows ----------
data class LibRow(
    val id: String, val title: String, val sub: String, val arabic: Boolean,
    val emoji: String?, val vector: Int, val tint: Int, val onClick: () -> Unit
)

class LibraryAdapter : ListAdapter<LibRow, LibraryAdapter.VH>(object : DiffUtil.ItemCallback<LibRow>() {
    override fun areItemsTheSame(a: LibRow, b: LibRow) = a.id == b.id
    override fun areContentsTheSame(a: LibRow, b: LibRow) =
        a.title == b.title && a.sub == b.sub && a.arabic == b.arabic && a.emoji == b.emoji && a.vector == b.vector && a.tint == b.tint
}) {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ic: ImageView = v.findViewById(R.id.ic)
        val emoji: TextView = v.findViewById(R.id.emoji)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub_ar)
        val amiri = Ui.font(v.context, R.font.amiri_regular)
        val dmSans = Ui.font(v.context, R.font.dm_sans_regular)
        init {
            v.setOnClickListener { val p = bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) getItem(p).onClick() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, t: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_library, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = getItem(position)
        Ui.folderIcon(h.ic, h.emoji, r.emoji, r.vector)
        ImageViewCompat.setImageTintList(h.ic, ColorStateList.valueOf(r.tint))
        h.title.text = r.title
        h.sub.text = r.sub
        // Views are recycled, so both branches set every property.
        if (r.arabic) { h.sub.textSize = 18f; h.sub.typeface = h.amiri; h.sub.textDirection = View.TEXT_DIRECTION_RTL }
        else { h.sub.textSize = 14f; h.sub.typeface = h.dmSans; h.sub.textDirection = View.TEXT_DIRECTION_LTR }
    }
}

// ---------- Folder view: azkar rows ----------
data class ZRow(
    val id: String, val arabic: String, val repeat: Int, val note: String,
    val hidden: Boolean = false, val selecting: Boolean = false, val selected: Boolean = false
)

class ZikrAdapter(
    private val onTap: (ZRow) -> Unit,
    private val onLong: (ZRow) -> Unit,
    private val onEdit: (String) -> Unit
) : ListAdapter<ZRow, ZikrAdapter.VH>(object : DiffUtil.ItemCallback<ZRow>() {
    override fun areItemsTheSame(a: ZRow, b: ZRow) = a.id == b.id
    override fun areContentsTheSame(a: ZRow, b: ZRow) = a == b
}) {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val chip: TextView = v.findViewById(R.id.chip)
        val rep: TextView = v.findViewById(R.id.rep)
        val ar: TextView = v.findViewById(R.id.ar)
        val note: TextView = v.findViewById(R.id.note)
        val editIc: ImageView = v.findViewById(R.id.edit_ic)
        init {
            v.setOnClickListener { val p = bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onTap(getItem(p)) }
            v.setOnLongClickListener { val p = bindingAdapterPosition; if (p != RecyclerView.NO_POSITION) onLong(getItem(p)); true }
            v.findViewById<View>(R.id.edit).setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) { val r = getItem(p); if (r.selecting) onTap(r) else onEdit(r.id) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, t: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_library_zikr, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = getItem(position)
        val ctx = h.itemView.context
        val d = ctx.resources.displayMetrics.density
        h.ar.text = r.arabic
        h.rep.text = "×${r.repeat}"
        h.note.text = r.note
        h.note.visibility = if (r.note.isBlank()) View.GONE else View.VISIBLE
        // Hidden: dimmed, with a "Hidden" tag
        h.chip.visibility = if (r.hidden) View.VISIBLE else View.GONE
        h.chip.setText(R.string.hidden_label)
        h.chip.setTextColor(ctx.getColor(R.color.muted))
        (h.rep.layoutParams as ViewGroup.MarginLayoutParams).marginStart = if (r.hidden) (8 * d).toInt() else 0
        h.rep.requestLayout()
        h.itemView.alpha = if (r.hidden) 0.5f else 1f
        h.itemView.setBackgroundResource(if (r.selected) R.drawable.bg_card18_sel else R.drawable.bg_card18)
        // Right-hand icon: pencil normally, a check while selecting
        if (r.selecting) {
            h.editIc.setImageResource(R.drawable.ic_check_2p25)
            ImageViewCompat.setImageTintList(h.editIc, ColorStateList.valueOf(ctx.getColor(R.color.gold)))
            h.editIc.alpha = if (r.selected) 1f else 0f
        } else {
            h.editIc.setImageResource(R.drawable.ic_pencil_1p75)
            ImageViewCompat.setImageTintList(h.editIc, ColorStateList.valueOf(ctx.getColor(R.color.muted)))
            h.editIc.alpha = 1f
        }
    }
}