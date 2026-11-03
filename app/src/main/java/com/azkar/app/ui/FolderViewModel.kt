package com.azkar.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azkar.app.data.AzkarRepository
import com.azkar.app.data.Folder
import com.azkar.app.data.Store
import com.azkar.app.data.Zikr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One folder ("anytime" = azkar without a folder) as shown by the folder and folder-editor screens. */
data class FolderState(
    val id: String,
    val folder: Folder?,
    val zikrs: List<Zikr>,
    val hidden: Set<String>
) {
    val name: String get() = folder?.name ?: "Anytime"

    /** False once a real folder has been deleted (e.g. from the editor while this screen was open). */
    val exists: Boolean get() = id == ANYTIME || folder != null || Store.folder(id) != null

    companion object {
        const val ANYTIME = "anytime"
    }
}

/**
 * Backs [FolderFragment] and [EditFolderFragment]. Content comes from the Room flow; writes still go
 * through [Store], which owns the write queue and widget refreshes.
 */
@HiltViewModel
class FolderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: AzkarRepository
) : ViewModel() {
    val id: String = savedState.get<String>("id") ?: FolderState.ANYTIME

    val state: StateFlow<FolderState?> = combine(repository.content, Store.hiddenZikrsFlow) { content, hidden ->
        FolderState(
            id = id,
            folder = content.folders.firstOrNull { it.id == id },
            zikrs = content.zikrs.filter { if (id == FolderState.ANYTIME) it.folderId == null else it.folderId == id },
            hidden = hidden
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Selection survives rotation.
    var selecting = false
    val selected = LinkedHashSet<String>()

    fun setHidden(ids: Collection<String>, hide: Boolean) = Store.setZikrsHidden(ids, hide)

    fun rename(name: String) = Store.renameFolder(id, name)
    fun isOnHome() = id !in Store.hidden()
    fun setOnHome(on: Boolean) = Store.setHidden(if (on) Store.hidden() - id else Store.hidden() + id)
    fun setIcon(icon: String?) = Store.setFolderIcon(id, icon)
    fun remembersPosition() = Store.rememberPosition(id)
    fun setRemember(on: Boolean) = Store.setFolderRemember(id, on)
    fun unfile(zikrId: String) = Store.unfile(zikrId)
    fun delete() = Store.deleteFolder(id)

    /**
     * Swaps [zikrId] with its neighbour [delta] places away. Reads Store's synchronous snapshot so
     * quick repeated taps build on each other instead of on a Room emission that hasn't arrived yet.
     */
    fun move(zikrId: String, delta: Int) {
        val ids = Store.zikrsIn(id).map { it.id }.toMutableList()
        val index = ids.indexOf(zikrId)
        val target = index + delta
        if (index !in ids.indices || target !in ids.indices) return
        val t = ids[index]; ids[index] = ids[target]; ids[target] = t
        Store.reorderFolder(id, ids)
    }
}
