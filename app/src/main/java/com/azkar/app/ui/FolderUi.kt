package com.azkar.app.ui

import android.content.DialogInterface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.EmojiRules
import com.azkar.app.data.Folder
import com.azkar.app.data.Store

/** Emoji chooser. [onPicked] gets the emoji, or null for "Default" (the vector icon). */
fun Fragment.pickEmoji(current: String?, onPicked: (String?) -> Unit) {
    val ctx = requireContext()
    var dlg: AlertDialog? = null
    val flow = FlowLayout(ctx)
    flow.addView(Ui.chip(ctx, ctx.getString(R.string.default_label), current == null, false) { dlg?.dismiss(); onPicked(null) })
    EmojiRules.FOLDER_EMOJIS.forEach { e ->
        val cell = TextView(ctx).apply {
            text = e
            textSize = 24f
            gravity = Gravity.CENTER
            background = Ui.shape(ctx, null, ctx.getColor(if (e == current) R.color.gold else R.color.border2), 14f)
            isClickable = true
            isFocusable = true
            setOnClickListener { dlg?.dismiss(); onPicked(e) }
        }
        flow.addView(cell, LinearLayout.LayoutParams(Ui.pxi(ctx, 48f), Ui.pxi(ctx, 48f)))
    }
    val p = Ui.pxi(ctx, 20f)
    val scroll = ScrollView(ctx).apply { setPadding(p, Ui.pxi(ctx, 8f), p, 0); addView(flow) }
    dlg = AlertDialog.Builder(ctx).setTitle(R.string.choose_icon).setView(scroll)
        .setNegativeButton(R.string.cancel, null).create()
    dlg.show()
}

/** "New folder" dialog with a name and an optional icon; the folder is made inside [parent] (null = top level). */
fun Fragment.promptNewFolder(parent: String? = null, onCreated: (Folder) -> Unit) {
    val ctx = requireContext()
    var icon: String? = null
    val input = EditText(ctx).apply {
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        hint = ctx.getString(R.string.folder_name_hint)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(true)
    }
    val iconBtn = TextView(ctx, null, 0, R.style.TB).apply {
        textSize = 16f
        setTextColor(ctx.getColor(R.color.gold))
        gravity = Gravity.CENTER
        minWidth = Ui.pxi(ctx, 96f)
        minHeight = Ui.pxi(ctx, 44f)
        setPadding(Ui.pxi(ctx, 14f), 0, Ui.pxi(ctx, 14f), 0)
        background = Ui.shape(ctx, null, ctx.getColor(R.color.border2), 14f)
        isClickable = true
        isFocusable = true
    }
    fun showIcon() { iconBtn.text = icon ?: ctx.getString(R.string.default_icon) }
    showIcon()
    iconBtn.setOnClickListener { pickEmoji(icon) { icon = it; showIcon() } }
    val intro = TextView(ctx, null, 0, R.style.T).apply {
        text = ctx.getString(R.string.new_folder_intro)
        textSize = 14f
        setTextColor(ctx.getColor(R.color.light))
        setLineSpacing(0f, 1.2f)
    }
    val example = TextView(ctx, null, 0, R.style.T).apply {
        text = ctx.getString(R.string.new_folder_example)
        textSize = 13f
        setTextColor(ctx.getColor(R.color.muted))
        setLineSpacing(0f, 1.25f)
        val q = Ui.pxi(ctx, 14f)
        setPadding(q, q, q, q)
        background = Ui.shape(ctx, ctx.getColor(R.color.card2), null, 14f)
    }
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = Ui.pxi(ctx, 20f)
        setPadding(p, Ui.pxi(ctx, 8f), p, 0)
        addView(intro)
        addView(example, Ui.lp(top = 12, ctx = ctx))
        addView(input, Ui.lp(top = 16, ctx = ctx))
        addView(iconBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .also { it.topMargin = Ui.pxi(ctx, 12f) })
    }
    val dlg = AlertDialog.Builder(ctx).setTitle(R.string.new_folder).setView(ScrollView(ctx).apply { addView(box) })
        .setPositiveButton(R.string.create, null).setNegativeButton(R.string.cancel, null).create()
    dlg.setOnShowListener {
        dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            when {
                name.isEmpty() -> input.error = ctx.getString(R.string.enter_name)
                Store.folders().any { it.name.equals(name, ignoreCase = true) } -> input.error = ctx.getString(R.string.folder_exists)
                else -> { val f = Store.addFolder(name, icon, parent); dlg.dismiss(); onCreated(f) }
            }
        }
    }
    dlg.show()
}