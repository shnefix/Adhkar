package com.azkar.app.data

/** Ids of the built-in folders in assets/default_folders.json. Keep them in sync with that file. */
object DefaultFolders {
    const val ISTIGHFAR = "d_1"
    const val WAKE = "d_9"
    const val EVENING = "d_11"
    const val SLEEP = "d_13"
    const val MORNING = "d_14"
    const val HOME = "d_15"          // الواجهة, added in defaults version 4
    const val ZAD = "d_16"           // زَادُ المَعَادِ, added in defaults version 6

    /** Default folders shown first on Home, in this order. */
    val HOME_ORDER = listOf(HOME, MORNING, EVENING, WAKE, SLEEP, ISTIGHFAR)
}
