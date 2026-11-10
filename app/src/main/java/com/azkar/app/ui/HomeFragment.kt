package com.azkar.app.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.azkar.app.R
import com.azkar.app.data.AzkarContent
import com.azkar.app.data.Folder
import com.azkar.app.data.HomePrefs
import com.azkar.app.data.Store
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home), HandlesOwnInsets {
    private val viewModel: HomeViewModel by viewModels()

    private enum class Kind { SMALL, HERO, WIDE }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        bindNav(view, Tab.HOME)
        view.findViewById<View>(R.id.btn_notifications).setOnClickListener { nav().push(RemindersFragment()) }
        view.findViewById<View>(R.id.btn_settings).setOnClickListener { nav().push(SettingsFragment()) }
        view.findViewById<View>(R.id.btn_customize).setOnClickListener { nav().push(CustomizeHomeFragment()) }
        val sections = view.findViewById<LinearLayout>(R.id.sections)

        fun render(content: AzkarContent) {
            sections.removeAllViews()
            val tinted = Store.homeStyle() != HomePrefs.STYLE_PLAIN

            fun open(f: Folder) {
                when {
                    Store.zikrsIn(f.id).isEmpty() && Store.childFolders(f.id).isNotEmpty() -> nav().push(LibraryFragment.of(f.id))
                    Store.zikrsIn(f.id).isEmpty() -> nav().push(EditFolderFragment.of(f.id))
                    Store.readerItems(f.id).isEmpty() -> {
                        Toast.makeText(ctx, R.string.all_hidden_toast, Toast.LENGTH_SHORT).show()
                        nav().push(FolderFragment.of(f.id))
                    }
                    else -> nav().push(ReaderFragment.of(f.id))
                }
            }

            fun card(f: Folder, kind: Kind): View {
                val v = layoutInflater.inflate(if (kind == Kind.WIDE) R.layout.item_home_wide else R.layout.item_home_card, null, false)
                val builtIn = Store.isDefaultFolder(f.id)
                Ui.folderIcon(v.findViewById(R.id.ic), v.findViewById(R.id.emoji), f.icon, if (builtIn) R.drawable.ic_book_1p75 else R.drawable.ic_folder_1p75)
                Ui.folderName(
                    v.findViewById(R.id.title), f.name,
                    latinSp = when (kind) { Kind.HERO -> 24f; Kind.WIDE -> 18f; Kind.SMALL -> 16f },
                    arabicSp = when (kind) { Kind.HERO -> 32f; Kind.WIDE -> 24f; Kind.SMALL -> 22f }
                )
                val sub = v.findViewById<TextView>(R.id.sub)
                val n = Store.zikrsIn(f.id).size
                val subText = when {
                    n > 0 -> getString(R.string.azkar_count, n)
                    !builtIn -> getString(R.string.your_folder)
                    else -> ""
                }
                sub.text = subText
                sub.visibility = if (subText.isEmpty()) View.GONE else View.VISIBLE

                val base = ctx.getColor(R.color.card)
                val border = ctx.getColor(R.color.border)
                val iconBg = ctx.getColor(R.color.card2)
                if (tinted) {
                    val tint = TINTS[(f.id.hashCode() and 0x7fffffff) % TINTS.size]
                    v.background = Ui.shape(ctx, ColorUtils.blendARGB(base, tint, 0.10f), ColorUtils.blendARGB(border, tint, 0.25f), 24f)
                    v.findViewById<View>(R.id.ic_wrap).background = Ui.shape(ctx, ColorUtils.blendARGB(iconBg, tint, 0.22f), null, 13f)
                    v.findViewById<android.widget.ImageView>(R.id.ic).imageTintList = ColorStateList.valueOf(tint)
                    v.findViewById<View>(R.id.mark).apply {
                        visibility = View.VISIBLE
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ColorUtils.setAlphaComponent(tint, 36)) }
                    }
                } else {
                    v.background = Ui.shape(ctx, base, border, 24f)
                    v.findViewById<View>(R.id.ic_wrap).background = Ui.shape(ctx, iconBg, null, 13f)
                }
                v.clipToOutline = true
                v.setOnClickListener { open(f) }
                return v
            }

            val foldersById = content.folders.associateBy { it.id }
            val hidden = Store.hidden()
            val shown = Store.homeOrder().filter { it !in hidden }.mapNotNull { foldersById[it] }
            if (shown.isEmpty()) return

            val gap = Ui.pxi(ctx, 10f)
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            fun addRow(v: View) {
                box.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                    if (box.childCount > 0) it.topMargin = gap
                })
            }
            fun pair(a: Folder, b: Folder?): View = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(card(a, Kind.SMALL), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(b?.let { card(it, Kind.SMALL) } ?: View(ctx), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = gap })
            }

            // First folder in your order is the big card, beside the next two; then one wide row, then pairs.
            var i = 0
            if (shown.size >= 3) {
                val left = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(card(shown[1], Kind.SMALL), Ui.lp())
                    addView(card(shown[2], Kind.SMALL), Ui.lp(top = 10, ctx = ctx))
                }
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(card(shown[0], Kind.HERO), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).also { it.marginStart = gap })
                }
                addRow(row)
                i = 3
            }
            var wideNext = shown.size >= 3
            while (i < shown.size) {
                if (wideNext || shown.size - i == 1) {
                    addRow(card(shown[i], Kind.WIDE)); i++; wideNext = false
                } else {
                    addRow(pair(shown[i], shown[i + 1])); i += 2
                }
            }

            val section = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.caps(ctx, getString(R.string.daily_collections)))
                addView(box, Ui.lp(top = 12, ctx = ctx))
            }
            sections.addView(section, Ui.lp(top = 16, ctx = ctx))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.content.collect { content ->
                    if (content != null) render(content)
                }
            }
        }
    }

    private companion object {
        val TINTS = intArrayOf(0xFFE8B44A.toInt(), 0xFF9B8CFF.toInt(), 0xFF7FD1B9.toInt(), 0xFFF29B8B.toInt(), 0xFF6FB6F0.toInt())
    }
}
