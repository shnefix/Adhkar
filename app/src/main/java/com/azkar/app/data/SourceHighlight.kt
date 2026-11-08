package com.azkar.app.data

/**
 * Finds where a zikr's own words appear inside its source text (the hadith or ayah on the source page),
 * so they can be shown in gold. Works word by word: every run of consecutive words the zikr and the source
 * share is colored, whatever lies between those words in the source (ayah numbers ﴿25﴾, punctuation, quotes).
 *
 * Words are compared without tashkeel or tatweel, with hamza forms, ta marbuta and alif maqsura folded,
 * without an alif inside the word (السموات = السماوات), and with one leading و / ف / ل / ب allowed on
 * either side (وتبارك = تبارك, لأستغفر = أستغفر).
 */
object SourceHighlight {
    /** Words too common to color as a two-word run on their own (they would light up every isnad). */
    private val COMMON = setOf("الله", "اللهم", "لله", "رسول", "صلي", "عليه", "وسلم", "قال", "يقول", "عن", "من", "في", "علي", "ان", "ما", "لا", "هو")
        .map { w -> w.filter { it != 'ا' } }.toSet()

    private fun isMark(c: Char) = c in 'ً'..'ٟ' || c == 'ٰ' || c == 'ـ' || c in 'ۖ'..'ۭ'
    private fun isLetter(c: Char) = c in 'ء'..'ي' || c in 'ٱ'..'ۓ' || c.isLetter() && !c.isDigit()

    private fun foldChar(c: Char): Char = when (c) {
        'أ', 'إ', 'آ', 'ٱ', 'ء' -> 'ا'
        'ؤ' -> 'و'
        'ئ', 'ى' -> 'ي'
        'ة' -> 'ه'
        else -> c
    }

    /** The comparable form of one word: folded letters with no alif at all (so السموات = السماوات, لأستغفر = ل + أستغفر). */
    private fun key(word: String): String {
        val sb = StringBuilder()
        for (c in word) {
            if (isMark(c)) continue
            val f = foldChar(c)
            if (f == 'ا') continue
            sb.append(f)
        }
        return sb.toString()
    }

    private class Word(val key: String, val start: Int, val end: Int)   // end exclusive, in the original text

    /** Words of [s]: runs of letters (tashkeel inside a word is kept with it); digits and anything else split. */
    private fun words(s: String): List<Word> {
        val out = ArrayList<Word>()
        var i = 0
        while (i < s.length) {
            if (!isLetter(s[i])) { i++; continue }
            val st = i
            while (i < s.length && (isLetter(s[i]) || isMark(s[i]))) i++
            val k = key(s.substring(st, i))
            if (k.isNotEmpty()) out.add(Word(k, st, i))
        }
        return out
    }

    // Keys have no alif, so «بأني» is already only 3 letters: a prefix may come off a word of 3 or more.
    private fun lettersBetween(s: String, from: Int, to: Int) = (from until to).count { isLetter(s[it]) }
    private fun wordsBetween(s: String, from: Int, to: Int) = words(s.substring(from, to)).size

    /** A word or two the source has and the zikr doesn't («وأبوء [لك] بذنبي») don't break the gold. */
    private const val MAX_GAP_WORDS = 2

    private fun strip(k: String) = if (k.length >= 3 && k[0] in "وفلب") k.substring(1) else k
    private fun same(a: String, b: String) = a == b || strip(a) == b || a == strip(b)

    /**
     * Ranges of [source] (inclusive) that repeat [zikr]. A short zikr (one or two words) is colored wherever
     * it all appears; a longer one wherever three or more of its words appear in a row, or two distinctive
     * ones. Ranges are sorted and never overlap.
     */
    fun ranges(source: String, zikr: String): List<IntRange> {
        val src = words(source); val z = words(zikr)
        if (src.isEmpty() || z.isEmpty()) return emptyList()
        val minRun = minOf(3, z.size)
        // run[i][j]: how many words match in a row starting at zikr word i and source word j.
        val run = Array(z.size + 1) { IntArray(src.size + 1) }
        for (i in z.size - 1 downTo 0) for (j in src.size - 1 downTo 0) {
            run[i][j] = if (same(z[i].key, src[j].key)) run[i + 1][j + 1] + 1 else 0
        }
        val colored = BooleanArray(src.size)
        val used = BooleanArray(z.size)
        /** A gold part: where it is in the source, and which zikr words (first, last) it repeats. */
        class Span(val range: IntRange, val zFirst: Int, val zLast: Int)
        val spans = ArrayList<Span>()
        fun take(i: Int, j: Int, l: Int) {
            for (t in 0 until l) { colored[j + t] = true; used[i + t] = true }
            spans.add(Span(src[j].start until src[j + l - 1].end, i, i + l - 1))
        }
        // Maximal runs only (not every shorter tail of a run).
        val starts = ArrayList<Triple<Int, Int, Int>>()
        for (i in z.indices) for (j in src.indices) {
            val l = run[i][j]
            if (l > 0 && !(i > 0 && j > 0 && run[i - 1][j - 1] > 0)) starts.add(Triple(i, j, l))
        }
        // 1) Runs that stand on their own: long enough, or two distinctive words.
        for ((i, j, l) in starts) {
            val ok = l >= minRun || (l == 2 && (0..1).all { d -> z[i + d].key.length >= 3 && strip(z[i + d].key) !in COMMON && z[i + d].key !in COMMON })
            if (ok && !(j until j + l).all { colored[it] }) take(i, j, l)
        }
        // 2) Short runs right beside what is already colored, in the zikr and in the source (a word or two
        //    the source words a little differently, e.g. «أستغفر الله [العظيم] الذي ...»). Repeat while it grows.
        fun near(flags: BooleanArray, a: Int, b: Int) = (maxOf(0, a - 2) until a).any { flags[it] } || (b until minOf(flags.size, b + 2)).any { flags[it] }
        var grew = spans.isNotEmpty()
        while (grew) {
            grew = false
            for ((i, j, l) in starts) {
                if ((j until j + l).all { colored[it] } || (i until i + l).all { used[it] }) continue
                if (near(used, i, i + l) && near(colored, j, j + l)) { take(i, j, l); grew = true }
            }
        }
        spans.sortBy { it.range.first }
        val merged = ArrayList<IntRange>()
        var prev: Span? = null
        for (sp in spans) {
            val r = sp.range
            val last = merged.lastOrNull()
            val p = prev
            // Joined into one part when they touch, when only punctuation, spaces, numbers or Quran marks lie
            // between them («،» «.» «﴿25﴾»), or when a word or two lie between them and the second part carries
            // on the zikr where the first stopped (not a second copy of the zikr starting over).
            val join = last != null && p != null && (
                r.first <= last.last + 1 ||
                lettersBetween(source, last.last + 1, r.first) == 0 ||
                (sp.zFirst > p.zLast && wordsBetween(source, last.last + 1, r.first) <= MAX_GAP_WORDS))
            if (join && last != null) merged[merged.size - 1] = last.first..maxOf(last.last, r.last) else merged.add(r)
            prev = if (join && p != null && p.zLast > sp.zLast) p else sp
        }
        return merged
    }
}
