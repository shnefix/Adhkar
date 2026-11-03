package com.azkar.app.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.azkar.app.R

/**
 * The app draws edge to edge, so Android's own navigation bar (buttons or gesture handle) and the keyboard
 * sit on top of it. MainActivity pads the fragment container by those insets for every screen except the
 * ones marked with this interface, which handle the insets themselves (the tab screens and the reader).
 */
interface HandlesOwnInsets

/** Insets the content must stay clear of at the bottom and sides: system bars, cutout and keyboard. */
fun WindowInsetsCompat.safeBottom() = getInsets(
    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
)

/**
 * Tab screens: the bottom tab bar grows by the part of the system navigation bar that does not fit in its own
 * bottom padding (20dp, sized for the gesture handle), so its buttons are never covered; [list] gets the same
 * extra space so its last row can scroll clear of the taller bar.
 */
fun bindNavInsets(root: View, list: View?) {
    val bar = root.findViewById<View>(R.id.nav_bar)
    val row = root.findViewById<View>(R.id.nav_row)
    val d = root.resources.displayMetrics.density
    val barHeight = (88 * d).toInt()
    val rowPad = (20 * d).toInt()
    val listPad = list?.paddingBottom ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val extra = (b.bottom - rowPad).coerceAtLeast(0)
        v.updatePadding(left = b.left, right = b.right)
        bar.updateLayoutParams { height = barHeight + extra }
        row.updatePadding(bottom = rowPad + extra)
        list?.updatePadding(bottom = listPad + extra)
        insets
    }
    ViewCompat.requestApplyInsets(root)
}
