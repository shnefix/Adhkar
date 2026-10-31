package com.azkar.app.data

/** Pure rules for hiding azkar from the reading sequence. Hiding never changes a zikr itself. */
object Visibility {
    /** The azkar the reader shows, in their normal folder order. */
    fun visible(items: List<Zikr>, hidden: Set<String>): List<Zikr> = items.filter { it.id !in hidden }

    /** Reader position of [id] among the visible azkar, or -1 if it is hidden or missing. */
    fun readerIndex(items: List<Zikr>, hidden: Set<String>, id: String): Int =
        visible(items, hidden).indexOfFirst { it.id == id }

    fun apply(hidden: Set<String>, ids: kotlin.collections.Collection<String>, hide: Boolean): Set<String> =
        if (hide) hidden + ids else hidden - ids.toSet()
}