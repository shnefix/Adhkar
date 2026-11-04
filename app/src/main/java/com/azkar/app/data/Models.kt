package com.azkar.app.data

/**
 * A zikr the user added. [title] is optional (blank = none), shown above it, or below when [titleBelow].
 * [source] is where the text comes from (hadith collection, surah and verse); blank = unknown.
 */
data class Zikr(
    val id: String,
    val arabic: String,
    val note: String,
    val repeat: Int,
    val folderId: String?,
    val title: String = "",
    val titleBelow: Boolean = false,
    val source: String = ""
)

/** A folder the user created or one loaded from the default asset. */
data class Folder(val id: String, val name: String, val icon: String? = null, val remember: Boolean = false)

/**
 * One recitation inside the reader. [note] is the zikr's note (blank = none), shown under the text;
 * [source] is shown on the back of the card (blank = no flip button).
 */
data class ReaderItem(
    val arabic: String, val target: Int, val id: String = "",
    val title: String = "", val titleBelow: Boolean = false, val note: String = "", val source: String = ""
)

/** A reminder the user configured. [open] is the folder id to read when tapped. */
data class Schedule(
    val id: String, val title: String, val message: String,
    val hour: Int, val minute: Int, val enabled: Boolean, val open: String?
)
