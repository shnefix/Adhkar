package com.azkar.app.ui

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.azkar.app.R
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.Store

/**
 * Bottom sheet for "Save to folder": a search box, the folders used last, then every folder.
 * [onPick] gets a folder id, "new" for a new folder, or null for no folder ("Anytime").
 */
object FolderSheet {
    fun show(ctx: Context, selected: String?, allowNew: Boolean, onPick: (String?) -> Unit) {
        val folders = Store.folders()
        open(
            ctx, ctx.getString(R.string.save_to_folder), allowNew,
            pinned = listOf(null to ctx.getString(R.string.anytime_no_folder)),
            folders = folders.map { f -> f.id to (f.icon?.let { "$it " } ?: "") + f.name },
            selected = selected, showCounts = true, onCancel = null, onPick = onPick,
        )
    }

    /**
     * The same sheet for any folder choice: [pinned] rows always come first (e.g. "Just open the app"),
     * then recently used and all [folders] (id to label), searchable. [onCancel] runs when it is dismissed without a pick.
     */
    fun pick(
        ctx: Context, title: String, pinned: List<Pair<String?, String>>, folders: List<Pair<String, String>>,
        selected: String?, onCancel: (() -> Unit)? = null, onPick: (String?) -> Unit,
    ) = open(ctx, title, false, pinned, folders, selected, false, onCancel, onPick)

    /** Folders with azkar of their own, labelled with icon and full path, for pickers. */
    fun readableOptions(): List<Pair<String, String>> =
        Store.readableFolders().map { f -> f.id to (f.icon?.let { "$it " } ?: "") + Store.folderPath(f.id) }

    private fun open(
        ctx: Context, title: String, allowNew: Boolean, pinned: List<Pair<String?, String>>, folders: List<Pair<String, String>>,
        selected: String?, showCounts: Boolean, onCancel: (() -> Unit)?, onPick: (String?) -> Unit,
    ) {
        val dialog = Dialog(ctx)
        var picked = false
        val gold = ctx.getColor(R.color.gold)
        val text = ctx.getColor(R.color.text)
        val muted = ctx.getColor(R.color.muted)
        val border = ctx.getColor(R.color.border)
        fun dp(v: Float) = Ui.pxi(ctx, v)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_sheet_top)
            setPadding(dp(20f), dp(12f), dp(20f), dp(16f))
        }
        root.addView(View(ctx).apply { background = Ui.shape(ctx, ctx.getColor(R.color.border2), null, 2f) },
            LinearLayout.LayoutParams(dp(40f), dp(4f)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(14f) })

        val header = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(ctx, null, 0, R.style.TB).apply {
            setText(title); textSize = 18f; setTextColor(text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (allowNew) header.addView(TextView(ctx, null, 0, R.style.TB).apply {
            setText(R.string.new_folder_chip); textSize = 13f; setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(dp(14f), 0, dp(14f), 0)
            background = Ui.shape(ctx, null, gold, 18f, dashed = true)
            isClickable = true; isFocusable = true
            setOnClickListener { dialog.dismiss(); onPick("new") }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40f)))
        root.addView(header)

        val search = EditText(ctx, null, 0, R.style.T).apply {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            hint = ctx.getString(R.string.search_n_folders, folders.size)
            setHintTextColor(ctx.getColor(R.color.hint)); setTextColor(text); textSize = 15f
            setSingleLine(true)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            includeFontPadding = false
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            background = Ui.shape(ctx, ctx.getColor(R.color.bg), border, 24f)
            setPadding(dp(18f), 0, dp(18f), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search_1p75, 0, 0, 0)
            compoundDrawablePadding = dp(10f)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(muted)
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply { topMargin = dp(14f) })

        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx).apply { addView(list); isVerticalScrollBarEnabled = false }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(6f) })

        val counts = Store.zikrCounts()
        fun label(t: Int) = TextView(ctx, null, 0, R.style.Caps).apply {
            setText(t); textSize = 11f; setTextColor(muted)
            setPadding(0, dp(16f), 0, dp(4f))
        }
        fun row(id: String?, name: String) = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52f)
            isClickable = true; isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            val on = id == selected
            addView(TextView(ctx, null, 0, R.style.T).apply {
                this.text = name; textSize = 17f; setTextColor(if (on) gold else text)
                textDirection = View.TEXT_DIRECTION_ANY_RTL; textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (showCounts) addView(TextView(ctx, null, 0, R.style.T).apply {
                this.text = (counts[id] ?: 0).toString(); textSize = 12f; setTextColor(muted)
                setPadding(dp(8f), 0, dp(8f), 0)
            })
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_check_2p25)
                imageTintList = android.content.res.ColorStateList.valueOf(gold)
                visibility = if (on) View.VISIBLE else View.INVISIBLE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(20f), dp(20f)))
            contentDescription = name
            isSelected = on
            setOnClickListener { picked = true; dialog.dismiss(); onPick(id) }
        }
        fun divider() = View(ctx).apply { setBackgroundColor(border) }

        fun render(q: String) {
            list.removeAllViews()
            val query = q.trim().lowercase()
            val matches = folders.filter { query.isEmpty() || it.second.lowercase().contains(query) }
            val recentIds = if (query.isEmpty()) AppPrefs.recentFolders().filter { id -> folders.any { it.first == id } }.take(3) else emptyList()
            fun section(title: Int, items: List<Pair<String, String>>) {
                if (items.isEmpty()) return
                list.addView(label(title))
                items.forEachIndexed { i, (id, name) ->
                    if (i > 0) list.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
                    list.addView(row(id, name))
                }
            }
            // Pinned rows (no folder, just open the app, ...): always offered while not searching.
            if (query.isEmpty()) pinned.forEach { (id, name) -> list.addView(row(id, name)) }
            section(R.string.recent, recentIds.mapNotNull { id -> folders.firstOrNull { it.first == id } })
            section(R.string.all_folders, matches)
            if (matches.isEmpty()) list.addView(TextView(ctx, null, 0, R.style.T).apply {
                setText(R.string.no_folders_match); textSize = 14f; setTextColor(muted); setPadding(0, dp(20f), 0, 0)
            })
        }
        render("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { render(s?.toString().orEmpty()) }
        })

        dialog.setOnDismissListener { if (!picked) onCancel?.invoke() }
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            val h = (ctx.resources.displayMetrics.heightPixels * 0.7f).toInt()
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, h)
            setWindowAnimations(android.R.style.Animation_InputMethod)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
    }
}
