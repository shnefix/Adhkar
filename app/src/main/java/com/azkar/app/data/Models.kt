package com.azkar.app.data

/**
 * Where a zikr's title shows in the reader. [code] is what the database stores (the old "titleBelow" column:
 * 0 above, 1 below, so existing rows keep their meaning) and [key] is the "titlePosition" value in JSON files.
 */
enum class TitlePos(val code: Int, val key: String) {
    ABOVE(0, "above"), BELOW(1, "below"), BOTH(2, "both"), HIDDEN(3, "hidden");

    val showsAbove get() = this == ABOVE || this == BOTH
    val showsBelow get() = this == BELOW || this == BOTH

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: ABOVE
        fun ofKey(key: String?) = entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: ABOVE
    }
}

/**
 * A zikr the user added. [title] is optional (blank = none), shown where [titlePos] says (or kept but hidden).
 * [source] is where the text comes from (hadith collection, surah and verse); blank = unknown.
 */
data class Zikr(
    val id: String,
    val arabic: String,
    val note: String,
    val repeat: Int,
    val folderId: String?,
    val title: String = "",
    val titlePos: TitlePos = TitlePos.ABOVE,
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
    val title: String = "", val titlePos: TitlePos = TitlePos.ABOVE, val note: String = "", val source: String = ""
)

/** A reminder the user configured. [open] is the folder id to read when tapped. */
data class Schedule(
    val id: String, val title: String, val message: String,
    val hour: Int, val minute: Int, val enabled: Boolean, val open: String?
)
