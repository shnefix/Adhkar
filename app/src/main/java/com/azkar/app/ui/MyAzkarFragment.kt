package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.Store

class MyAzkarFragment : Fragment(R.layout.fragment_my_azkar) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        bindNav(view, Tab.MY)
        view.findViewById<View>(R.id.btn_add).setOnClickListener { nav().push(AddZikrFragment.of(null)) }

        // Folders
        val tiles = Store.folders().map { f ->
            val n = Store.zikrsIn(f.id).size
            layoutInflater.inflate(R.layout.item_folder_tile, null, false).apply {
                findViewById<TextView>(R.id.name).text = f.name
                findViewById<TextView>(R.id.count).text = if (n == 1) "1 zikr" else "$n azkar"
                setOnClickListener { nav().push(EditFolderFragment.of(f.id)) }
            }
        } + layoutInflater.inflate(R.layout.item_folder_new, null, false).apply {
            setOnClickListener { nav().push(AddZikrFragment.of(null)) }
        }
        view.findViewById<LinearLayout>(R.id.folders).addView(Ui.grid(ctx, tiles, 3, 8))

        // Saved azkar
        val saved = view.findViewById<LinearLayout>(R.id.saved)
        Store.zikrs().forEachIndexed { i, z ->
            val v = layoutInflater.inflate(R.layout.item_saved, saved, false)
            val chip = v.findViewById<TextView>(R.id.chip)
            chip.text = Store.folderName(z.folderId)
            chip.setTextColor(ctx.getColor(if (z.folderId == null || Store.isBuiltinFolder(z.folderId)) R.color.teal else R.color.gold))
            v.findViewById<TextView>(R.id.rep).text = "×${z.repeat}"
            v.findViewById<TextView>(R.id.ar).text = z.arabic
            v.findViewById<TextView>(R.id.note).apply {
                text = z.note
                visibility = if (z.note.isBlank()) View.GONE else View.VISIBLE
            }
            v.findViewById<View>(R.id.edit).setOnClickListener { nav().push(EditZikrFragment.of(z.id)) }
            saved.addView(v, Ui.lp(top = if (i > 0) 8 else 0, ctx = ctx))
        }
    }
}
