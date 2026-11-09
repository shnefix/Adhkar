package com.azkar.app.data

/**
 * Library search over everything: folder names, and each zikr's text, title, note, hadith text and reference.
 * Arabic is compared without tashkeel, tatweel or hamza / alef / ya / ta-marbuta differences, so «استغفر» finds
 * «أَسْتَغْفِرُ». Build an [Index] once per content and query it as the user types.
 */
object LibrarySearch {
    /** Where a zikr matched, best first: results are ranked in this order. */
    enum class Where { TEXT, TITLE, NOTE, HADITH, SOURCE }

    data class FolderHit(val folder: Folder, val ranges: List<IntRange>)
    /** [ranges] are in the zikr's own text (only for [Where.TEXT]); [snippet] is the matched part of a hadith. */
    data class ZikrHit(val zikr: Zikr, val where: Where, val ranges: List<IntRange>, val snippet: String = "")
    data class Result(val folders: List<FolderHit>, val zikrs: List<ZikrHit>)

    /** Text with its normalized form and, for each normalized char, where it came from in the original. */
    private class Norm(val original: String) {
        val text: String
        val map: IntArray
        init {
            val sb = StringBuilder(original.length)
            val m = IntArray(original.length)
            var lastSpace = true
            original.forEachIndexed { i, c ->
                val n = fold(c) ?: return@forEachIndexed
                if (n == ' ') { if (lastSpace) return@forEachIndexed; lastSpace = true } else lastSpace = false
                m[sb.length] = i; sb.append(n)
            }
            text = sb.toString(); map = m.copyOf(text.length)
        }

        /** Every place [q] occurs, as ranges in the original text. */
        fun find(q: String, limit: Int = 8): List<IntRange> {
            if (q.isEmpty()) return emptyList()
            val out = ArrayList<IntRange>()
            var from = 0
            while (out.size < limit) {
                val at = text.indexOf(q, from)
                if (at < 0) break
                out += map[at]..map[at + q.length - 1]
                from = at + q.length
            }
            return out
        }
    }

    /** One char folded for comparison; null drops it (tashkeel, tatweel, punctuation as space handled separately). */
    private fun fold(c: Char): Char? = when (c) {
        in 'ً'..'ٟ', 'ٰ', 'ـ', in 'ؐ'..'ؚ', in 'ۖ'..'ۭ' -> null
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
        'ى' -> 'ي'
        'ة' -> 'ه'
        'ؤ' -> 'و'
        'ئ' -> 'ي'
        else -> if (c.isLetterOrDigit()) c.lowercaseChar() else ' '
    }

    fun normalize(s: String): String = Norm(s).text.trim()

    private class Entry(val zikr: Zikr, val text: Norm, val title: Norm, val note: Norm, val hadith: Norm, val source: Norm)

    class Index(content: AzkarContent) {
        private val folders = content.folders.map { it to Norm(it.name) }
        private val entries = content.zikrs.map { z ->
            Entry(z, Norm(z.arabic), Norm(listOf(z.title, z.titleBelow).filter { it.isNotBlank() }.joinToString(" · ")),
                Norm(z.note), Norm(z.source.detail), Norm(listOf(z.source.reference, z.source.narrator, z.source.grade).joinToString(" ")))
        }

        fun search(query: String, maxZikrs: Int = 200): Result {
            val q = normalize(query)
            if (q.isEmpty()) return Result(emptyList(), emptyList())
            val fh = folders.mapNotNull { (f, n) -> n.find(q).takeIf { it.isNotEmpty() }?.let { FolderHit(f, it) } }
                .sortedBy { if (it.ranges.first().first == 0) 0 else 1 }
            val zh = ArrayList<Pair<Int, ZikrHit>>()
            entries.forEachIndexed { order, e ->
                val text = e.text.find(q)
                val hit = when {
                    text.isNotEmpty() -> ZikrHit(e.zikr, Where.TEXT, text)
                    e.title.find(q, 1).isNotEmpty() -> ZikrHit(e.zikr, Where.TITLE, emptyList())
                    e.note.find(q, 1).isNotEmpty() -> ZikrHit(e.zikr, Where.NOTE, emptyList())
                    e.hadith.find(q, 1).isNotEmpty() -> ZikrHit(e.zikr, Where.HADITH, emptyList(), snippet(e.hadith, q))
                    e.source.find(q, 1).isNotEmpty() -> ZikrHit(e.zikr, Where.SOURCE, emptyList())
                    else -> null
                } ?: return@forEachIndexed
                // Within one kind, a zikr that starts with the words comes first, then library order.
                val starts = hit.where == Where.TEXT && e.text.text.startsWith(q)
                zh += (hit.where.ordinal * 2 + (if (starts) 0 else 1)) * 100_000 + order to hit
            }
            return Result(fh, zh.sortedBy { it.first }.take(maxZikrs).map { it.second })
        }

        /** About 70 chars of the hadith around the first match, with «…» where it was cut. */
        private fun snippet(n: Norm, q: String): String {
            val r = n.find(q, 1).firstOrNull() ?: return ""
            val s = n.original
            var a = (r.first - 35).coerceAtLeast(0)
            var b = (r.last + 36).coerceAtMost(s.length)
            while (a > 0 && !s[a - 1].isWhitespace()) a--
            while (b < s.length && !s[b].isWhitespace()) b++
            return (if (a > 0) "…" else "") + s.substring(a, b).trim() + (if (b < s.length) "…" else "")
        }
    }
}
