package com.azkar.app.data

import com.azkar.app.R

/** A zikr the user added. folderId is a built-in collection key, a custom folder id, or null ("Anytime"). */
data class Zikr(
    val id: String,
    val arabic: String,
    val note: String,
    val repeat: Int,
    val folderId: String?
)

/** A folder the user created. */
data class Folder(val id: String, val name: String, val icon: String? = null)

/** One recitation inside the reader. */
data class ReaderItem(val arabic: String, val target: Int)

/** Built-in collection (Home grid / Library). */
data class Collection(
    val key: String,
    val title: String,
    val homeArabic: String,
    val libraryArabic: String,
    val icon: Int,
    val items: List<ReaderItem>
)

enum class Placement(val key: String, val label: String) {
    TOP("top", "Top of Home"),
    MIXED("mixed", "Mixed with collections"),
    BOTTOM("bottom", "Bottom of Home");

    companion object {
        fun from(key: String?) = values().firstOrNull { it.key == key } ?: MIXED
    }
}

object Builtins {
    private fun r(a: String, n: Int) = ReaderItem(a, n)

    val morning = Collection(
        "morning", "Morning", "أذكار الصباح", "أذكار الصباح", R.drawable.ic_sun_1p75,
        listOf(
            r("اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ", 1),
            r("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ", 3),
            r("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", 100)
        )
    )
    val evening = Collection(
        "evening", "Evening", "أذكار المساء", "أذكار المساء", R.drawable.ic_moon_1p75,
        listOf(
            r("اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ", 1),
            r("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ", 3),
            r("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", 100)
        )
    )
    val afterPrayer = Collection(
        "afterPrayer", "After prayer", "بعد الصلاة", "أذكار بعد الصلاة", R.drawable.ic_mosque_1p75,
        listOf(
            r("سُبْحَانَ اللَّهِ", 33),
            r("الْحَمْدُ لِلَّهِ", 33),
            r("اللَّهُ أَكْبَرُ", 33)
        )
    )
    val sleep = Collection(
        "sleep", "Before sleep", "أذكار النوم", "أذكار النوم", R.drawable.ic_spark_1p75,
        listOf(r("بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا", 1))
    )
    val waking = Collection(
        "waking", "Waking up", "الاستيقاظ", "أذكار الاستيقاظ", R.drawable.ic_sunrise_1p75,
        listOf(r("الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ", 1))
    )
    val worry = Collection(
        "worry", "Worry and hardship", "دعاء الهم والحزن", "دعاء الهم والحزن", R.drawable.ic_heart_1p75,
        listOf(r("حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ", 7))
    )
    val forgiveness = Collection(
        "forgiveness", "Forgiveness", "الاستغفار", "الاستغفار", R.drawable.ic_drop_1p75,
        listOf(r("أَسْتَغْفِرُ اللَّهَ", 100))
    )

    /** Library order. */
    val library = listOf(morning, evening, afterPrayer, sleep, waking, worry, forgiveness)

    /** Collections that can be hidden/reordered on the Home grid (Morning is the hero card). */
    val homeCollections = listOf(evening, afterPrayer, sleep, waking)

    /** Built-ins offered as folders when saving a zikr. */
    val folderChoices = listOf(morning, evening, afterPrayer, sleep)

    fun byKey(key: String?): Collection? = library.firstOrNull { it.key == key }
}

/** A reminder the user configured. [open] is the folder key to read when tapped (or null = just open the app). */
data class Schedule(
    val id: String, val title: String, val message: String,
    val hour: Int, val minute: Int, val enabled: Boolean, val open: String?
)