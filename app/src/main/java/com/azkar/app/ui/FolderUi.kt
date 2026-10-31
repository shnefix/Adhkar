package com.azkar.app.ui

import android.content.DialogInterface
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.azkar.app.data.Folder
import com.azkar.app.data.Store

fun Fragment.promptNewFolder(onCreated: (Folder) -> Unit) {
    val ctx = requireContext()
    val input = EditText(ctx).apply {
        hint = "Folder name"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(true)
    }
    val box = FrameLayout(ctx).apply {
        val p = Ui.pxi(ctx, 20f)
        setPadding(p, Ui.pxi(ctx, 8f), p, 0)
        addView(input)
    }
    val dlg = AlertDialog.Builder(ctx).setTitle("New folder").setView(box)
        .setPositiveButton("Create", null).setNegativeButton("Cancel", null).create()
    dlg.setOnShowListener {
        dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            when {
                name.isEmpty() -> input.error = "Enter a name"
                Store.folders().any { it.name.equals(name, ignoreCase = true) } -> input.error = "A folder with this name already exists"
                else -> { val f = Store.addFolder(name); dlg.dismiss(); onCreated(f) }
            }
        }
    }
    dlg.show()
}