package com.azkar.app.data

/** The app does not use emojis of people, faces, hands, body parts or animals. */
object EmojiRules {
    private val RESTRICTED: List<IntRange> = listOf(
        // animals
        0x1F400..0x1F43F, 0x1F54A..0x1F54A, 0x1F577..0x1F577, 0x1F980..0x1F99F, 0x1F9A1..0x1F9AE, 0x1FAB0..0x1FAB3,
        // faces, people, hands, body parts
        0x1F440..0x1F450, 0x1F466..0x1F478, 0x1F47C..0x1F47C, 0x1F481..0x1F483, 0x1F485..0x1F487, 0x1F4AA..0x1F4AA,
        0x1F574..0x1F575, 0x1F57A..0x1F57A, 0x1F590..0x1F590, 0x1F595..0x1F596, 0x1F600..0x1F64F,
        0x1F6A3..0x1F6A3, 0x1F6B4..0x1F6B6, 0x1F6C0..0x1F6C0, 0x1F6CC..0x1F6CC,
        0x1F90C..0x1F90C, 0x1F90F..0x1F93A, 0x1F93C..0x1F93E,
        0x1F9B0..0x1F9B9, 0x1F9BB..0x1F9BB, 0x1F9D0..0x1F9DF, 0x1F9E0..0x1F9E0,
        0x1FAC0..0x1FAC2, 0x1FAE0..0x1FAE8, 0x1FAF0..0x1FAF8,
        0x1F3C3..0x1F3C4, 0x1F3C7..0x1F3C7, 0x1F3CA..0x1F3CC,
        0x261D..0x261D, 0x26F9..0x26F9, 0x270A..0x270D
    )

    fun isRestricted(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (RESTRICTED.any { cp in it }) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** The folder-icon picker list: neutral symbols only. */
    val FOLDER_EMOJIS = listOf(
        "🌅", "🌙", "☀️", "🕌", "📿", "✨", "❤️", "📖", "🌿", "🌸", "⭐", "🕋",
        "💧", "🌧️", "🛏️", "🍽️", "🚗", "🏠", "💼", "🎓", "🌹", "🔥", "🌍", "☁️",
        "💚", "🛡️", "🕯️", "🌄", "🌆", "🌟", "💫", "📚", "🧭"
    )
}