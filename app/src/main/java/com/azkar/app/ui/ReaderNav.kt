package com.azkar.app.ui

/** Pure navigation rules of the reader (no Android classes), so they can be unit tested. */
class ReaderNav(private val targets: List<Int>, start: Int = 0, count: Int = 0) {
    enum class Outcome { MOVED, FINISH, BOUNCE }

    var index: Int = start.coerceIn(0, (targets.size - 1).coerceAtLeast(0)); private set
    var count: Int = count; private set

    val last get() = targets.size - 1
    val isLast get() = index == last
    /** The repetition count of the CURRENT zikr is complete. */
    val isDone get() = targets[index] <= 1 || count >= targets[index]

    /** Counter tap. Returns true if the count changed. */
    fun tapRing(): Boolean { if (isDone) return false; count++; return true }

    fun canPage(dir: Int) = if (dir > 0) index < last else index > 0
    /** Leaving the reader needs BOTH: last zikr of the collection AND its own count complete. */
    fun canFinish() = isLast && isDone

    /** Tap on the card or swipe left. */
    fun next(): Outcome = when {
        canPage(1) -> { index++; count = 0; Outcome.MOVED }
        canFinish() -> Outcome.FINISH
        else -> Outcome.BOUNCE
    }

    /** Swipe right. Never finishes. */
    fun prev(): Outcome =
        if (canPage(-1)) { index--; count = 0; Outcome.MOVED } else Outcome.BOUNCE
}