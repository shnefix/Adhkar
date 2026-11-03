package com.azkar.app.data

/** A zikr the user added. [title] is optional (blank = none), shown above it, or below when [titleBelow]. */
data class Zikr(
    val id: String,
    val arabic: String,
    val note: String,
    val repeat: Int,
    val folderId: String?,
    val title: String = "",
    val titleBelow: Boolean = false
)

/** A folder the user created or one loaded from the default asset. */
data class Folder(val id: String, val name: String, val icon: String? = null, val remember: Boolean = false)

/** One recitation inside the reader. [note] is the zikr's note (blank = none), shown under the text. */
data class ReaderItem(
    val arabic: String, val target: Int, val id: String = "",
    val title: String = "", val titleBelow: Boolean = false, val note: String = ""
)

enum class Placement(val key: String, @androidx.annotation.StringRes val label: Int) {
    TOP("top", com.azkar.app.R.string.placement_top),
    MIXED("mixed", com.azkar.app.R.string.placement_mixed),
    BOTTOM("bottom", com.azkar.app.R.string.placement_bottom);

    companion object {
        fun from(key: String?) = values().firstOrNull { it.key == key } ?: MIXED
    }
}

/** A reminder the user configured. [open] is the folder id to read when tapped. */
data class Schedule(
    val id: String, val title: String, val message: String,
    val hour: Int, val minute: Int, val enabled: Boolean, val open: String?
)
