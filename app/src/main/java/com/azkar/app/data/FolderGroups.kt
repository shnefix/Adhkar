package com.azkar.app.data

/**
 * The ten parent folders the built-in folders are sorted into (ids from assets/default_folders.json). They are created
 * once as ordinary folders (defaults version 18), after which the user can rename, move or delete them like any other.
 */
object FolderGroups {
    data class Group(val id: String, val name: String, val folders: List<String>)

    private fun ids(vararg n: Int) = n.map { "d_$it" }

    val ALL: List<Group> = listOf(
        Group("g_day", "اليوم والليلة", ids(14, 11, 49, 9, 50, 13, 52, 51, 140)),
        Group("g_prayer", "الطهارة والصلاة", ids(25, 54, 23, 22, 63, 12, 65, 67, 66, 68, 69, 3, 17, 7)),
        Group("g_home", "البيت والحياة اليومية", ids(26, 57, 37, 24, 40, 153, 38, 149, 150, 151, 39, 42, 152, 155, 154)),
        Group("g_travel", "السفر والحج", ids(32, 120, 121, 123, 122, 27, 125, 126, 127, 128, 129, 130, 131, 132)),
        Group("g_seasons", "المواسم والعبادات", ids(134, 135, 137, 136, 138, 139, 146, 141, 28)),
        Group("g_nature", "الظواهر الكونية", ids(36, 142, 147, 148)),
        Group("g_dua", "الأدعية", ids(19, 20, 21, 6, 8, 158, 159, 5, 82, 78, 79, 80, 81, 83, 84, 85, 86, 87, 88, 89, 90, 91,
            92, 93, 94, 95, 156, 73, 74, 75, 76, 77, 109, 110, 111)),
        Group("g_protect", "الحفظ والشفاء والكرب", ids(41, 97, 98, 35, 33, 99, 100, 101, 112, 113, 103, 104, 105, 106, 107,
            108, 34, 115, 116)),
        Group("g_death", "الموت والجنازة", ids(119, 117, 118, 30)),
        Group("g_dhikr", "الذكر المطلق", ids(18, 46, 45, 47, 44, 1, 48, 29, 43, 4, 16, 157, 31)),
    )

    /**
     * Defaults version 16: twin folders folded into one (kept folder <- absorbed ones). The absorbed folder's azkar move
     * into the kept one (a text it already has is dropped), then the absorbed folder is deleted. The Home sessions
     * (wake-up, sleep) keep their own short folders, so their longer twins stay separate.
     */
    val MERGES: Map<String, List<String>> = mapOf(
        "d_12" to ids(64, 62),       // المسجد، الخروج من المسجد
        "d_23" to ids(55),           // الوضوء
        "d_22" to ids(61),           // الأذان
        "d_7" to ids(72),            // بعد الصلاة
        "d_17" to ids(71),           // الصلاة
        "d_3" to ids(70),            // القنوت
        "d_26" to ids(58),           // الطعام
        "d_37" to ids(56),           // اللباس
        "d_25" to ids(53),           // قضاء الحاجة
        "d_24" to ids(59, 60),       // الخروج من البيت، دخول البيت
        "d_32" to ids(124),          // السفر
        "d_27" to ids(133),          // الحج
        "d_34" to ids(114),          // الاستخارة
        "d_33" to ids(102),          // الكرب
        "d_41" to ids(96),           // الرقية
        "d_36" to ids(143, 144, 145) // المطر، الريح، الرعد
    )
}
