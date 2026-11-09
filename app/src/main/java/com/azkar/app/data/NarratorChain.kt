package com.azkar.app.data

/**
 * The chain of narrators (isnad) read from a source's hadith text: everyone named before the Prophet ﷺ, in the
 * order «حدثنا A عن B عن C أن رسول الله», turned round so it runs from the Prophet ﷺ to the book's author.
 *
 * Careful on purpose: every name must follow a transmission word (حدثنا، أخبرنا، عن، سمعت …). Text between the
 * names (a story, a verb) means the text isn't a plain chain, so null: no chain is shown rather than a wrong one.
 * Only after the last narrator may a story start («قال كان…», «أن رجلا جاء…»): the chain ends there.
 */
object NarratorChain {
    enum class Role { PROPHET, COMPANION, SUCCESSOR, NARRATOR, SHEIKH, AUTHOR }
    /** One person in the chain; [label] is the line under the name («صحابي», «شيخ البخاري», «أخرجه في صحيحه»). */
    data class Link(val name: String, val role: Role, val label: String)

    private val TASHKEEL = Regex("[ؐ-ًؚ-ٰٟـ]")
    private val PROPHET = Regex("رسول الله|النبي|نبي الله")
    private val PRAISE = Regex("رض[يى] الله عن\\S*|رحم[هة] الله|عليه السلام|عليهما السلام")
    private val PUNCT = Regex("[،,:؛.\"«»()\\[\\]\\-–ـ]")
    /** Words that hand the hadith on: the name after one of them is a narrator. */
    private val TRANSMIT = setOf("حدثنا", "وحدثنا", "حدثني", "وحدثني", "أخبرنا", "وأخبرنا", "أخبرني", "وأخبرني", "أنبأنا", "عن", "سمعت", "سمع")
    /** Words that end a name without starting a new one. */
    /** Companions who often narrate from another companion: they stay «صحابي» wherever they stand in the chain. */
    private val COMPANIONS = setOf(
        "ابن عباس", "عبد الله بن عباس", "ابن عمر", "عبد الله بن عمر", "عبد الله بن عمرو", "ابن مسعود", "عبد الله بن مسعود",
        "أبو هريرة", "أنس", "أنس بن مالك", "جابر", "جابر بن عبد الله", "أبو سعيد", "أبو سعيد الخدري", "عائشة", "أبو موسى",
        "أبو بكر الصديق", "عمر بن الخطاب", "علي بن أبي طالب", "عثمان بن عفان", "أبي بن كعب", "معاذ بن جبل", "أبو الدرداء",
        "سلمان", "أبو ذر", "زيد بن ثابت", "البراء", "البراء بن عازب", "أم سلمة", "حفصة", "ميمونة", "أبو أيوب", "سهل بن سعد"
    )
    private val UNNAMED = setOf("عمه", "عمته", "خاله", "خالته", "أخيه", "أخته", "أمه", "جدته", "مولاه", "رجل", "شيخ", "بعضهم", "أصحابه")
    private val PARALLEL = setOf("وحدثنا", "وحدثني", "وأخبرنا", "وأخبرني")
    private val END = setOf("قال", "قالت", "يقول", "تقول", "أنه", "أنها", "أنهم", "يحدث", "قالا", "قالوا", "وقال", "وقالا", "وقالوا", "فقال")
    /** «… حدثه»: the name before it told the hadith on, the only way a name after «أن» counts. */
    private val TOLD = setOf("حدثه", "أخبره", "حدثهم", "حدثته", "أخبرته", "نبأته", "نبأه")
    /** «أن» may introduce a narrator too («أن محجن بن الأدرع حدثه»); with nothing after it, it is just «أن رسول الله». */
    private const val THAT = "أن"
    /** Words where a name's description starts («عائشة زوج النبي», «يعني ابن عفان»): the name is what comes before. */
    private val CUT = setOf("زوج", "مولى", "يعني", "علمهم", "يبلغ", "يرفعه", "وكانت", "وكان")
    /** Words never part of a name: a piece holding one is prose. */
    private val PROSE = setOf(
        "كان", "كانت", "دخل", "جاء", "رأى", "رأيت", "يأمر", "قام", "لا", "لم", "ألا", "إلا", "إذا", "سأل", "أتى", "خرج",
        "بت", "عند", "حجة", "حتى", "رجل", "رجلا", "امرأة", "ناسا", "قوما", "في", "على", "إلى", "من", "ما", "هذا", "بعض", "أصحاب"
    )
    private val AUTHORS = listOf(
        "البخاري" to "الإمام البخاري", "مسلم" to "الإمام مسلم", "داود" to "أبو داود", "الترمذي" to "الترمذي",
        "النسائي" to "النسائي", "ابن ماجه" to "ابن ماجه", "أحمد" to "الإمام أحمد"
    )
    /** What the author's book is called, for «أخرجه في …». */
    private val BOOK_KIND = mapOf(
        "الإمام البخاري" to "صحيحه", "الإمام مسلم" to "صحيحه", "أبو داود" to "سننه", "النسائي" to "سننه",
        "ابن ماجه" to "سننه", "الترمذي" to "جامعه", "الإمام أحمد" to "مسنده"
    )
    /** Titles of narrators who come up often and are known by this exact name. */
    private val TITLES = mapOf(
        "مالك" to "إمام دار الهجرة", "نافع" to "مولى ابن عمر", "الزهري" to "إمام من التابعين",
        "ابن شهاب" to "إمام من التابعين", "شعبة" to "أمير المؤمنين في الحديث", "سفيان" to "من أئمة الحديث",
        "الأعمش" to "من أئمة الحديث", "سعيد بن المسيب" to "سيد التابعين", "عكرمة" to "مولى ابن عباس",
        "كريب" to "مولى ابن عباس", "قتادة" to "تابعي", "مجاهد" to "تابعي", "عروة" to "تابعي", "مسروق" to "تابعي"
    )
    private val WOMEN = setOf("عائشة", "حفصة", "جويرية", "خولة", "ميمونة", "زينب", "فاطمة", "أسماء", "سودة", "صفية", "أم")
    private val ALSO = Regex("وأخرجه أيضًا:\\s*([^،\\n]+)")

    /** «أبي هريرة» / «أبا هريرة» as written after «عن» reads «أبو هريرة» on its own. */
    private fun nominative(name: String) = when {
        name.startsWith("أبي ") -> "أبو " + name.removePrefix("أبي ")
        name.startsWith("أبا ") -> "أبو " + name.removePrefix("أبا ")
        else -> name
    }

    private fun isWoman(name: String) = name.split(' ').first() in WOMEN || " بنت " in " $name "

    /** «… عن أبيه» / «عن جده»: the father or grandfather read from the narrator before («سليمان بن بريدة» -> «بريدة»). */
    private val FATHER = setOf("أبيه", "أبي"); private val GRANDFATHER = setOf("جده", "جدي")
    private fun ancestor(word: String, before: String?): String? {
        val parts = before?.split(" بن ") ?: return null
        val steps = when (word) { in FATHER -> 1; in GRANDFATHER -> 2; else -> return null }
        return if (parts.size > steps) parts.drop(steps).joinToString(" بن ") else null
    }

    /** Before a name ends, «وإسحاق …» or «واللفظ …» starts something else: the name is the first one. */
    private val KUNYA = setOf("أبو", "أبي", "أبا", "بن", "ابن", "بنت", "أم")
    private fun firstName(tokens: List<String>): List<String> {
        val cut = tokens.indices.drop(1).firstOrNull { i -> tokens[i].startsWith("و") && tokens[i].length > 2 && tokens[i - 1] !in KUNYA }
        return if (cut == null) tokens else tokens.subList(0, cut)
    }

    /** «صحيح مسلم 2691» -> «مسلم 2691», for the short line under the author. */
    private fun shortBook(ref: String) = ref.trim()
        .removePrefix("صحيح ").removePrefix("سنن ").removePrefix("جامع ").removePrefix("مسند ")
        .replace("أبي داود", "أبو داود")

    fun parse(detail: String, reference: String): List<Link>? {
        val plain = TASHKEEL.replace(detail, "").replace('‏', ' ').replace('‎', ' ')
        val at = PROPHET.find(plain)?.range?.first ?: return null
        val raw = PUNCT.replace(PRAISE.replace(plain.substring(0, at), " "), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        // «قال عروة قال أبو هريرة»: a short name between two «قال» is handed on like «عن عروة».
        val words = raw.toMutableList()
        for (i in words.indices) {
            if (words[i] != "قال" || i == 0) continue
            val stop = (i + 1 until words.size).firstOrNull { words[it] in END || words[it] in TRANSMIT || words[it] == THAT } ?: continue
            val name = words.subList(i + 1, stop)
            if (name.size in 1..4 && name.none { it in PROSE } && words[stop] == "قال") words[i] = "عن"
        }
        // «ح» (switching to a second chain) or «وحدثنا …» after the first teacher: two chains in one text, not drawn.
        if ("ح" in words || words.drop(1).any { it in PARALLEL }) return null
        val names = ArrayList<String>()
        var between = 0                                // words skipped between two narrators
        var storyWords = 0                             // words of the story between the last narrator and the Prophet ﷺ
        var heard = false                              // the name came after «سمعت»: «أنسا» reads «أنس»
        // Index of the last transmission word: after it only the last narrator's name, then perhaps a story.
        val lastHandOn = words.indexOfLast { it in TRANSMIT || it == THAT }
        var current: MutableList<String>? = null       // the name being read, after a transmission word
        var afterThat = false                          // it came after «أن»: only a name if «حدثه» … closes it
        var ended = false                              // the last narrator is read; what follows is the story
        fun close(told: Boolean = false, last: Boolean = false): Boolean {
            val c = current ?: return true
            current = null
            if (c.isEmpty()) return true                 // «… أن رسول الله»: nothing between them
            var kept = firstName(c.takeWhile { it !in CUT })
            if (afterThat && !told) {
                // «أن رجلا جاء…» after the last narrator: the story begins, the chain is complete.
                if (last && kept.firstOrNull() in PROSE) { ended = true; storyWords += c.size; return true }
                return false                             // «أن تلبية رسول الله»: not a narrator
            }
            if (kept.any { it in PROSE }) {
                if (!last) return false                  // a story between two narrators: not a plain chain
                kept = kept.takeWhile { it !in PROSE }   // «سمعت البراء وجاءه رجل…»: the name, then the story
                storyWords += c.size - kept.size
                ended = true
            }
            if ((heard || afterThat) && kept.size == 1 && kept[0].length > 3 && kept[0].endsWith("ا")) kept = listOf(kept[0].dropLast(1))
            var name = kept.joinToString(" ").trim()
            if (name in FATHER || name in GRANDFATHER) name = ancestor(name, names.lastOrNull()) ?: return false
            if (name in UNNAMED) return false             // «عن عمه», «عن رجل»: who it was isn't written
            if (name.isEmpty() || kept.size > 6) return false
            names += name
            return true
        }
        for ((i, w) in words.withIndex()) {
            if (ended) { storyWords++; continue }
            val last = i >= lastHandOn
            when {
                w in TRANSMIT -> { if (!close()) return null; current = ArrayList(); afterThat = false; heard = w == "سمعت" || w == "سمع"; between = 0 }
                w == THAT -> { if (!close()) return null; current = ArrayList(); afterThat = true }
                w in TOLD -> { if (!close(told = true)) return null }
                w in END -> { if (!close(last = last)) return null; if (last && names.isNotEmpty()) ended = true }
                current != null -> current!!.add(w)
                i > lastHandOn && names.isNotEmpty() -> ended = true   // the story after the last narrator
                // «قال إسحاق أخبرنا وقال الآخران حدثنا»: who said which word. Anything longer is a story inside the
                // chain («يحدث أنه كان يختلف إلى عبد الله بن عمر…»), which could hide a narrator: no chain.
                i < lastHandOn -> if (++between > 3) return null
                else -> return null                     // text that no transmission word introduced: not a chain
            }
        }
        if (!close(last = true) || names.isEmpty()) return null
        // A short «قال كان…» means the last narrator saw it himself; a longer story may name the companion inside it,
        // so the last name is only called «راوي القصة». A «مرسل» hadith's first narrator is a successor, not a companion.
        val sure = storyWords <= 3
        val mursal = "مرسل" in reference
        val narrators = names.reversed().fold(emptyList<String>()) { acc, n -> if (acc.lastOrNull() == n) acc else acc + n }.map(::nominative)
        val author = AUTHORS.firstOrNull { reference.contains(it.first) }?.second
        // A chain that starts at the author's own teacher («حدثنا …») names him; one that starts at «عن» doesn't.
        val full = words.firstOrNull()?.let { it in TRANSMIT && it != "عن" } == true
        return buildList {
            add(Link("رسول الله ﷺ", Role.PROPHET, "قائل الحديث"))
            narrators.forEachIndexed { i, n ->
                val last = i == narrators.size - 1
                when {
                    i == 0 && mursal -> add(Link(n, Role.SUCCESSOR, "تابعي (مرسل)"))
                    i == 0 && !sure -> add(Link(n, Role.SUCCESSOR, "راوي القصة"))
                    i == 0 -> {
                        val woman = isWoman(n)
                        add(Link("$n ${if (woman) "رضي الله عنها" else "رضي الله عنه"}", Role.COMPANION, if (woman) "صحابية" else "صحابي"))
                    }
                    last && full && author != null -> add(Link(n, Role.SHEIKH, "شيخ ${author.removePrefix("الإمام ")}"))
                    n in COMPANIONS -> add(Link("$n ${if (isWoman(n)) "رضي الله عنها" else "رضي الله عنه"}", Role.NARRATOR, if (isWoman(n)) "صحابية" else "صحابي"))
                    i == 1 -> add(Link(n, Role.SUCCESSOR, TITLES[n] ?: "تابعي"))
                    else -> add(Link(n, Role.NARRATOR, TITLES[n] ?: "راوٍ"))
                }
            }
            if (author != null) {
                val also = ALSO.find(detail)?.groupValues?.get(1)?.let { " · و" + shortBook(it) }.orEmpty()
                add(Link(author, Role.AUTHOR, "أخرجه في ${BOOK_KIND[author] ?: "كتابه"}$also"))
            }
        }
    }
}
