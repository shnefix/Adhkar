package com.azkar.app.data

/** Reader constants and pure rules that do not need Android. */
object ReaderPrefs {
    const val FONT_MIN = 18
    const val FONT_MAX = 40
    const val FONT_DEFAULT = 26            // the old "Medium"
    private val OLD_SIZES = intArrayOf(20, 26, 32)   // old Small / Medium / Large

    fun spFromOldIndex(i: Int) = OLD_SIZES[i.coerceIn(0, 2)]
    fun clampSp(sp: Int) = sp.coerceIn(FONT_MIN, FONT_MAX)
}

/** Where a folder should start reading when it is opened. */
object ReaderResume {
    /** (index, count). Falls back to the beginning if the saved zikr is gone, hidden or the id is unknown. */
    fun start(ids: List<String>, targets: List<Int>, savedId: String?, savedCount: Int): Pair<Int, Int> {
        if (savedId == null) return 0 to 0
        val i = ids.indexOf(savedId)
        if (i < 0 || i >= targets.size) return 0 to 0
        return i to savedCount.coerceIn(0, targets[i])
    }
}