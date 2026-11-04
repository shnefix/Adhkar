package com.azkar.app.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.azkar.app.R
import com.azkar.app.data.Folder
import com.azkar.app.data.Store

/**
 * Customize, in two tabs. Home: the order of the folders on Home and which show at all. Library: the folder order of the Library and My azkar. Both show a live preview above a grid of tiles that
 * you press and hold to drag; on Home the eye hides a folder.
 */
class CustomizeHomeFragment : Fragment(R.layout.fragment_customize_home) {
    private var libraryTab = false

    /** Folder ids in the order being edited (the Home order, or the Library order). */
    private val ids = ArrayList<String>()
    private lateinit var adapter: TileAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        libraryTab = savedInstanceState?.getBoolean("library") ?: false
        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }
        view.findViewById<View>(R.id.btn_done).setOnClickListener { nav().pop() }
        view.findViewById<View>(R.id.btn_reset).setOnClickListener { Store.resetHome(); load(view) }

        adapter = TileAdapter()
        view.findViewById<RecyclerView>(R.id.tiles).apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = this@CustomizeHomeFragment.adapter
            itemAnimator?.changeDuration = 0
            dragHelper.attachToRecyclerView(this)
        }
        load(view)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("library", libraryTab)
    }

    /** Reads the order for the current tab and redraws everything. */
    private fun load(view: View) {
        ids.clear()
        ids += if (libraryTab) Store.folders().map { it.id } else Store.homeOrder()
        renderTabs(view)
        view.findViewById<TextView>(R.id.hint).setText(if (libraryTab) R.string.customize_hint_library else R.string.customize_hint_home)
        view.findViewById<View>(R.id.btn_reset).visibility = if (libraryTab) View.GONE else View.VISIBLE
        adapter.notifyDataSetChanged()
        renderPreview(view)
    }

    /** Saves the order after a drag, then refreshes the preview. */
    private fun saveOrder() {
        if (libraryTab) Store.setFolderOrder(ids) else Store.setHomeOrder(ids.toList())
        view?.let { renderPreview(it) }
    }

    // ---------- Tabs (segmented pills) ----------
    private fun pills(row: LinearLayout, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val ctx = requireContext()
        row.removeAllViews()
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val b = TextView(ctx, null, 0, R.style.TB).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(ctx.getColor(if (on) R.color.bg else R.color.light))
                background = if (on) Ui.shape(ctx, ctx.getColor(R.color.gold), null, 16f) else null
                isClickable = true
                isFocusable = true
                isSelected = on
                setOnClickListener { if (!on) onPick(i) }
            }
            row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun renderTabs(view: View) = pills(
        view.findViewById(R.id.tabs), listOf(getString(R.string.home), getString(R.string.library)), if (libraryTab) 1 else 0
    ) { libraryTab = it == 1; load(view) }

    // ---------- Live preview ----------
    /** A small copy of Home (shown folders) or of the Library, two per row like the real screens. */
    private fun renderPreview(view: View) {
        val ctx = requireContext()
        val box = view.findViewById<LinearLayout>(R.id.preview)
        box.removeAllViews()
        val folders = ids.mapNotNull { Store.folder(it) }
        val shown: List<Folder> = if (libraryTab) folders else Store.hidden().let { hidden -> folders.filter { it.id !in hidden } }
        val max = 8
        shown.take(max).chunked(2).forEachIndexed { r, pair ->
            val line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, f ->
                val mine = !Store.isDefaultFolder(f.id)
                val chip = TextView(ctx, null, 0, R.style.TB).apply {
                    text = (f.icon?.let { "$it " } ?: "") + f.name
                    textSize = 11f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setTextColor(ctx.getColor(R.color.text))
                    val p = Ui.pxi(ctx, 8f)
                    setPadding(p, 0, p, 0)
                    // My own folders are outlined in gold, the built-in collections plain.
                    background = Ui.shape(ctx, ctx.getColor(R.color.card), ctx.getColor(if (mine) R.color.gold else R.color.border), 8f, mine)
                }
                line.addView(chip, LinearLayout.LayoutParams(0, Ui.pxi(ctx, 28f), 1f).also { if (i > 0) it.marginStart = Ui.pxi(ctx, 6f) })
            }
            if (pair.size == 1) line.addView(View(ctx), LinearLayout.LayoutParams(0, 0, 1f).also { it.marginStart = Ui.pxi(ctx, 6f) })
            box.addView(line, Ui.lp(top = if (r > 0) 6 else 0, ctx = ctx))
        }
        if (shown.size > max) {
            box.addView(TextView(ctx, null, 0, R.style.T).apply {
                text = getString(R.string.preview_more, shown.size - max)
                textSize = 11f
                setTextColor(ctx.getColor(R.color.muted))
                gravity = Gravity.CENTER
            }, Ui.lp(top = 6, ctx = ctx))
        }
    }

    // ---------- Tiles ----------
    private inner class TileHolder(v: View) : RecyclerView.ViewHolder(v) {
        val ic: ImageView = v.findViewById(R.id.ic)
        val emoji: TextView = v.findViewById(R.id.emoji)
        val name: TextView = v.findViewById(R.id.name)
        val toggle: View = v.findViewById(R.id.toggle)
        val eye: ImageView = v.findViewById(R.id.eye)
    }

    private inner class TileAdapter : RecyclerView.Adapter<TileHolder>() {
        override fun getItemCount() = ids.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
            TileHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_customize_tile, parent, false))

        override fun onBindViewHolder(h: TileHolder, position: Int) {
            val ctx = h.itemView.context
            val id = ids[position]
            val f = Store.folder(id)
            val builtin = Store.isDefaultFolder(id)
            Ui.folderIcon(h.ic, h.emoji, f?.icon, if (builtin) R.drawable.ic_book_1p75 else R.drawable.ic_folder_1p75)
            h.ic.imageTintList = ColorStateList.valueOf(ctx.getColor(if (builtin) R.color.teal else R.color.gold))
            h.name.text = f?.name ?: ""
            val hidden = !libraryTab && id in Store.hidden()
            h.itemView.alpha = if (hidden) 0.45f else 1f
            h.toggle.visibility = if (libraryTab) View.GONE else View.VISIBLE
            h.eye.setImageResource(if (hidden) R.drawable.ic_eyeoff_1p75 else R.drawable.ic_eye_1p75)
            h.eye.imageTintList = ColorStateList.valueOf(ctx.getColor(if (hidden) R.color.muted else R.color.gold))
            h.toggle.setOnClickListener {
                val now = Store.hidden()
                Store.setHidden(if (id in now) now - id else now + id)
                notifyItemChanged(h.bindingAdapterPosition)
                view?.let { renderPreview(it) }
            }
            // Screen readers can't drag: offer "Move up" / "Move down" on each tile instead.
            h.itemView.contentDescription = h.name.text
            ViewCompat.replaceAccessibilityAction(h.itemView, AccessibilityActionCompat(R.id.action_move_earlier, null),
                ctx.getString(R.string.move_up)) { _, _ -> moveBy(h.bindingAdapterPosition, -1) }
            ViewCompat.replaceAccessibilityAction(h.itemView, AccessibilityActionCompat(R.id.action_move_later, null),
                ctx.getString(R.string.move_down)) { _, _ -> moveBy(h.bindingAdapterPosition, 1) }
        }
    }

    private fun moveBy(from: Int, d: Int): Boolean {
        val to = from + d
        if (from !in ids.indices || to !in ids.indices) return false
        java.util.Collections.swap(ids, from, to)
        adapter.notifyItemMoved(from, to)
        saveOrder()
        return true
    }

    /** Press and hold a tile, then drag it anywhere in the grid; the order is saved when it is dropped. */
    private val dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
    ) {
        override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val a = from.bindingAdapterPosition
            val b = to.bindingAdapterPosition
            if (a < 0 || b < 0) return false
            ids.add(b, ids.removeAt(a))
            adapter.notifyItemMoved(a, b)
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, state: Int) {
            super.onSelectedChanged(vh, state)
            if (state == ItemTouchHelper.ACTION_STATE_DRAG) vh?.itemView?.apply { scaleX = 1.06f; scaleY = 1.06f }
        }

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            vh.itemView.scaleX = 1f
            vh.itemView.scaleY = 1f
            saveOrder()
        }
    })
}
