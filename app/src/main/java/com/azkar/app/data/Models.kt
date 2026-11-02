package com.azkar.app.data

/** A zikr the user added. */
data class Zikr(
    val id: String,
    val arabic: String,
    val note: String,
    val repeat: Int,
    val folderId: String?
)

/** A folder the user created or one loaded from the default asset. */
data class Folder(val id: String, val name: String, val icon: String? = null, val remember: Boolean = false)

/** One recitation inside the reader. */
data class ReaderItem(val arabic: String, val target: Int, val id: String = "")

enum class Placement(val key: String, val label: String) {
    TOP("top", "Top of Home"),
    MIXED("mixed", "Mixed with collections"),
    BOTTOM("bottom", "Bottom of Home");

    companion object {
        fun from(key: String?) = values().firstOrNull { it.key == key } ?: MIXED
    }
}

/** A reminder the user configured. [open] is the folder id to read when tapped. */
data class Schedule(
    val id: String, val title: String, val message: String,
    val hour: Int, val minute: Int, val enabled: Boolean, val open: String?
)
