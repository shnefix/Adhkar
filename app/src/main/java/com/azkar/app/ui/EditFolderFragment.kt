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

        fun renderRows(state: FolderState) {
            val list = state.zikrs
            label.text = getString(R.string.order_count, azkarCount(requireContext(), list.size))
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            rows.removeAllViews()
            val last = list.size - 1
            list.forEachIndexed { idx, z ->
                val hid = z.id in state.hidden
                val v = layoutInflater.inflate(R.layout.item_folder_zikr, rows, false)
                v.findViewById<TextView>(R.id.ar).text = z.arabic
                v.findViewById<TextView>(R.id.line).text =
                    "×${z.repeat}" + (if (z.note.isBlank()) "" else " · ${z.note}") + (if (hid) " · " + getString(R.string.hidden_label) else "")
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
                rows.addView(v, Ui.lp(top = if (idx > 0) 8 else 0, ctx = ctx))
            }
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
        fun of(id: String) = EditFolderFragment().apply { arguments = Bundle().apply { putString("id", id) } }
    }
}
