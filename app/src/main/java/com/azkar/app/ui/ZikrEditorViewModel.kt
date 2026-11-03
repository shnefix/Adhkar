package com.azkar.app.ui

import androidx.lifecycle.ViewModel
import com.azkar.app.data.Store
import com.azkar.app.data.Zikr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Writes for [AddZikrFragment] and [EditZikrFragment]; keeps the screens free of direct Store calls. */
@HiltViewModel
class ZikrEditorViewModel @Inject constructor() : ViewModel() {
    fun zikr(id: String): Zikr? = Store.zikr(id)
    fun isHidden(id: String) = id in Store.hiddenZikrs()
    fun setHidden(id: String, hide: Boolean) = Store.setZikrsHidden(listOf(id), hide)
    fun update(zikr: Zikr) = Store.updateZikr(zikr)
    fun delete(id: String) = Store.deleteZikr(id)

    /**
     * Adds a zikr. [folderSel] is a folder id, or "new" to file it under [newFolderName]
     * (reusing a folder of that name if one exists; a blank name leaves it unfiled).
     */
    /** The zikr with this exact text already in the folder [add] would save to (an existing folder only), if any. */
    fun existing(arabic: String, folderSel: String?, newFolderName: String): Zikr? {
        val folderId: String? = if (folderSel == "new") {
            val name = newFolderName.trim()
            if (name.isEmpty()) null else Store.folders().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id ?: return null
        } else folderSel
        return Store.zikrs().firstOrNull { it.arabic == arabic && it.folderId == folderId }
    }

    fun add(arabic: String, repeat: Int, folderSel: String?, newFolderName: String, title: String = "", titleBelow: Boolean = false) {
        val folderId: String? = if (folderSel == "new") {
            val name = newFolderName.trim()
            if (name.isEmpty()) null
            else (Store.folders().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Store.addFolder(name)).id
        } else folderSel
        Store.addZikr(arabic, "", repeat, folderId, title, titleBelow)
    }
}
