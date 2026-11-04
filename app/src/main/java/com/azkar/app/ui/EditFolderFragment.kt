package com.azkar.app.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.azkar.app.R
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.Store
import com.azkar.app.data.Tracking
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditFolderFragment : Fragment(R.layout.fragment_edit_folder) {
    private val viewModel: FolderViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        // The Store snapshot is updated synchronously, so it can seed the form before Room emits.
        val folder = Store.folder(viewModel.id)
        if (folder == null) { nav().pop(); return }
        var current: FolderState? = null

        view.findViewById<View>(R.id.btn_back).setOnClickListener { nav().pop() }

        // Name (saved as you type; an empty name is ignored)
        val name = view.findViewById<EditText>(R.id.fname)
        name.setText(folder.name)
        name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString()?.trim().orEmpty()
                if (t.isNotEmpty()) viewModel.rename(t)
            }
        })

        // Show on Home
        val sw = view.findViewById<ToggleSwitch>(R.id.sw_home)
        sw.on = viewModel.isOnHome()
        sw.onToggle = { on -> viewModel.setOnHome(on) }

        val iconBtn = view.findViewById<TextView>(R.id.icon_btn)
        fun renderIcon(icon: String?) {
            iconBtn.text = icon ?: getString(R.string.default_label)
            iconBtn.textSize = if (icon == null) 14f else 24f
        }
        iconBtn.setOnClickListener {
            pickEmoji(Store.folder(viewModel.id)?.icon) { viewModel.setIcon(it); renderIcon(it) }
        }
        renderIcon(folder.icon)

        val swRem = view.findViewById<ToggleSwitch>(R.id.sw_remember)
        val remSub = view.findViewById<TextView>(R.id.remember_sub)
        // While it remembers, the folder can start over at a set moment: before / after a prayer, or at midnight.
        val resetRow = view.findViewById<View>(R.id.reset_row)
        val resetSub = view.findViewById<TextView>(R.id.reset_sub)
        fun renderRem() {
            remSub.text = getString(if (swRem.on) R.string.remember_on else R.string.remember_off)
            resetRow.visibility = if (swRem.on) View.VISIBLE else View.GONE
            val rule = viewModel.resetRule()
            resetSub.text = if (rule == null) timeRuleLabel(requireContext(), null)
            else getString(R.string.start_over_summary, timeRuleLabel(requireContext(), rule))
        }
        resetRow.setOnClickListener {
            pickTimeRule(R.string.start_over_title, viewModel.resetRule(), allowNever = true, allowMidnight = true) {
                viewModel.setResetRule(it)
                renderRem()
            }
        }
        swRem.on = viewModel.remembersPosition()
        renderRem()
        swRem.onToggle = { on -> viewModel.setRemember(on); renderRem() }

        // Sources: the reader's source button for this folder's azkar (on by default; Settings can turn all off).
        val swSrc = view.findViewById<ToggleSwitch>(R.id.sw_sources)
        val srcSub = view.findViewById<TextView>(R.id.sources_sub)
        fun renderSrc() {
            srcSub.text = getString(when {
                !AppPrefs.sourcesOn() -> R.string.sources_off_app
                swSrc.on -> R.string.folder_sources_on
                else -> R.string.folder_sources_off
            })
        }
        swSrc.on = !AppPrefs.folderSourcesHidden(viewModel.id)
        renderSrc()
        swSrc.onToggle = { on -> AppPrefs.setFolderSourcesHidden(viewModel.id, !on); renderSrc() }

        // Track this folder: counts the days it is finished (off by default).
        val swTrack = view.findViewById<ToggleSwitch>(R.id.sw_track)
        val trackSub = view.findViewById<TextView>(R.id.track_sub)
        fun renderTrack() { trackSub.text = trackingSummary(ctx, listOf(viewModel.id), swTrack.on) }
        swTrack.on = Tracking.isTracked(viewModel.id)
        renderTrack()
        swTrack.onToggle = { on -> setTracking(ctx, listOf(viewModel.id), on); renderTrack() }

        // Order list
        val rows = view.findViewById<LinearLayout>(R.id.rows)
        val empty = view.findViewById<View>(R.id.empty)
        val label = view.findViewById<TextView>(R.id.order_label)

        /** Fills one row for [z] at position [idx] of [last] + 1 (a new row or a reused one). */
        fun bindRow(v: View, z: com.azkar.app.data.Zikr, idx: Int, last: Int, hid: Boolean) {
            v.findViewById<TextView>(R.id.ar).text = z.arabic
            // The book shows or hides this zikr's source in the reader; only azkar that have a source get it.
            val srcHidden = !z.source.isEmpty && AppPrefs.zikrSourceHidden(z.id)
            v.findViewById<TextView>(R.id.line).text =
                "×${z.repeat}" + (if (z.note.isBlank()) "" else " · ${z.note}") + (if (hid) " · " + getString(R.string.hidden_label) else "") +
                    (if (srcHidden) " · " + getString(R.string.source_hidden_label) else "")
            v.findViewById<View>(R.id.src).apply {
                visibility = if (z.source.isEmpty) View.INVISIBLE else View.VISIBLE
                alpha = if (srcHidden) 0.4f else 1f
                setOnClickListener {
                    AppPrefs.setZikrSourceHidden(z.id, !srcHidden)
                    bindRow(v, z, idx, last, hid)
                }
            }
            v.findViewById<ImageView>(R.id.src_ic).imageTintList =
                ColorStateList.valueOf(ctx.getColor(if (srcHidden) R.color.muted else R.color.gold))
            v.findViewById<ImageView>(R.id.hide_ic).apply {
                setImageResource(if (hid) R.drawable.ic_eyeoff_1p75 else R.drawable.ic_eye_1p75)
                imageTintList = ColorStateList.valueOf(ctx.getColor(if (hid) R.color.muted else R.color.gold))
            }
            v.alpha = if (hid) 0.55f else 1f
            v.findViewById<View>(R.id.hide).setOnClickListener { viewModel.setHidden(listOf(z.id), !hid) }
            val up = v.findViewById<View>(R.id.up)
            val down = v.findViewById<View>(R.id.down)
            up.alpha = if (idx == 0) 0.35f else 1f
            down.alpha = if (idx == last) 0.35f else 1f
            up.setOnClickListener { viewModel.move(z.id, -1) }
            down.setOnClickListener { viewModel.move(z.id, 1) }
            v.findViewById<View>(R.id.edit).setOnClickListener { nav().push(EditZikrFragment.of(z.id)) }
            v.findViewById<View>(R.id.remove).setOnClickListener { viewModel.unfile(z.id) }
        }

        // Rows are kept per zikr and reused, so a hide / move / edit only updates them instead of re-inflating the
        // whole list. The first build goes in small batches: the screen shows at once and the rest follows.
        val rowViews = HashMap<String, View>()
        var renderGen = 0

        fun renderRows(state: FolderState) {
            val gen = ++renderGen
            val list = state.zikrs
            label.text = getString(R.string.order_count, azkarCount(requireContext(), list.size))
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            rowViews.keys.retainAll(list.mapTo(HashSet()) { it.id })
            val last = list.size - 1

            fun place(from: Int) {
                if (gen != renderGen || view == null) return   // a newer state is being drawn, or the screen closed
                var idx = from
                var inflated = 0
                while (idx < list.size && inflated < ROW_BATCH) {
                    val z = list[idx]
                    val v = rowViews[z.id] ?: layoutInflater.inflate(R.layout.item_folder_zikr, rows, false)
                        .also { rowViews[z.id] = it; inflated++ }
                    bindRow(v, z, idx, last, z.id in state.hidden)
                    val lp = Ui.lp(top = if (idx > 0) 8 else 0, ctx = ctx)
                    if (rows.getChildAt(idx) !== v) {
                        (v.parent as? android.view.ViewGroup)?.removeView(v)
                        rows.addView(v, idx, lp)
                    } else {
                        v.layoutParams = lp
                    }
                    idx++
                }
                if (idx < list.size) rows.post { place(idx) }
                else while (rows.childCount > list.size) rows.removeViewAt(rows.childCount - 1)   // removed azkar
            }
            place(0)
        }

        view.findViewById<View>(R.id.btn_add).setOnClickListener { nav().push(AddZikrFragment.of(viewModel.id)) }
        view.findViewById<View>(R.id.btn_delete).setOnClickListener {
            val n = current?.zikrs?.size ?: Store.zikrsIn(viewModel.id).size
            val doDelete = { viewModel.delete(); nav().pop() }
            if (n == 0) doDelete()
            else AlertDialog.Builder(ctx)
                .setTitle(getString(R.string.delete_folder_title, Store.folderName(viewModel.id)))
                .setMessage(getString(R.string.delete_folder_message, azkarCount(ctx, n)))
                .setPositiveButton(R.string.delete) { _, _ -> doDelete() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state == null || !state.exists) return@collect
                    current = state
                    renderRows(state)
                }
            }
        }
    }

    companion object {
        /** Rows inflated per frame while the list is first built. */
        private const val ROW_BATCH = 6

        fun of(id: String) = EditFolderFragment().apply { arguments = Bundle().apply { putString("id", id) } }
    }
}
