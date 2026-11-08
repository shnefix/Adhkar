package com.azkar.app.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the source-page highlight against every zikr in the built-in folders: when a zikr's words all appear
 * in its source in the same order, the whole of it must be one unbroken gold part (no grey comma, word or mark
 * in the middle), and nothing may be colored when not a single phrase of the zikr is in the source.
 */
class SourceHighlightAssetTest {
    private val items by lazy {
        val f = listOf("src/main/assets/default_folders.json", "app/src/main/assets/default_folders.json").map(::File).first { it.exists() }
        (ImportJson.parse(f.readText(Charsets.UTF_8)) as ImportJson.Parsed.Ok).folders.flatMap { fo -> fo.items.map { fo.name to it } }
    }

    // Same word comparison as the highlighter, kept simple here: no marks, folded letters, no alif.
    private fun key(w: String) = w.filter { it !in 'ً'..'ٟ' && it != 'ٰ' && it != 'ـ' && it !in 'ۖ'..'ۭ' }
        .map { when (it) { 'أ', 'إ', 'آ', 'ٱ', 'ء' -> 'ا'; 'ؤ' -> 'و'; 'ئ', 'ى' -> 'ي'; 'ة' -> 'ه'; else -> it } }
        .filter { it != 'ا' }.joinToString("")
    private fun words(s: String) = Regex("[\\u0621-\\u064A\\u0671-\\u06D3][\\u0621-\\u065F\\u0670-\\u06D3\\u06D6-\\u06ED]*")
        .findAll(s).map { key(it.value) }.filter { it.isNotEmpty() }.toList()

    @Test
    fun aZikrFoundWholeInItsSourceIsColoredWholeInOnePiece() {
        var checked = 0
        val bad = ArrayList<String>()
        for ((folder, z) in items) {
            val src = z.source.detail
            if (src.isBlank()) continue
            val zw = words(z.text); val sw = words(src)
            if (zw.isEmpty()) continue
            val at = (0..sw.size - zw.size).firstOrNull { s -> zw.indices.all { sw[s + it] == zw[it] } } ?: continue
            checked++
            val rs = SourceHighlight.ranges(src, z.text)
            // The part of the source that holds the zikr, from its first word to its last.
            val starts = Regex("[\\u0621-\\u064A\\u0671-\\u06D3][\\u0621-\\u065F\\u0670-\\u06D3\\u06D6-\\u06ED]*").findAll(src).filter { key(it.value).isNotEmpty() }.toList()
            val first = starts[at].range.first; val last = starts[at + zw.size - 1].range.last
            if (rs.none { it.first <= first && it.last >= last }) bad.add("$folder: ${z.text.take(50)}")
        }
        assertTrue("checked only $checked", checked > 150)
        assertTrue("not colored in one piece (${bad.size}):\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
