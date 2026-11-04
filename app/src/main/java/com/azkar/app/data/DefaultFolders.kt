package com.azkar.app.data

/** Ids of the built-in folders in assets/default_folders.json. Keep them in sync with that file. */
object DefaultFolders {
    const val ISTIGHFAR = "d_1"
    const val WAKE = "d_9"
    const val EVENING = "d_11"
    const val SLEEP = "d_13"
    const val MORNING = "d_14"
    const val HOME = "d_15"          // أذكار قصيرة (was الواجهة), added in defaults version 4
    const val PRAISED = "d_42"       // إذا مُدِحت, added in defaults version 12

    /** Azkar added to the short-azkar folder ([HOME]) in defaults version 12. */
    val ADDED_TO_HOME_IN_V12 = listOf(
        "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
        "اللَّهُمَّ إِنَّكَ عَفُوٌّ تُحِبُّ الْعَفْوَ فَاعْفُ عَنِّي",
        "يَا حَيُّ يَا قَيُّومُ، بِرَحْمَتِكَ أَسْتَغِيثُ"
    )
    const val ZAD = "d_16"           // مختارات (was زَادُ المَعَادِ), added in defaults version 6

    /** d_17 .. d_41 (prayer, tasabih, jawami, Quranic duas, ... ruqyah), added in defaults version 8. */
    val ADDED_IN_V8: Set<String> = (17..41).map { "d_$it" }.toSet()

    /** Default folders shown first on Home, in this order. */
    val HOME_ORDER = listOf(HOME, MORNING, EVENING, WAKE, SLEEP, ISTIGHFAR)
}
