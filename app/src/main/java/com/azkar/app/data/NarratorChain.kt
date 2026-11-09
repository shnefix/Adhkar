package com.azkar.app.data

/**
 * The chain of narrators (isnad) read from a source's hadith text: everyone named before the Prophet ﷺ, in the
 * order «حدثنا A عن B عن C أن رسول الله», turned round so it runs from the Prophet ﷺ to the book's author.
 *
 * Strict on purpose: every name must follow a transmission word (حدثنا، أخبرنا، عن، سمعت …), and any other text
 * before the Prophet ﷺ is named (a story, a verb) means the text isn't a plain chain, so null: no chain is shown
 * rather than a wrong one.
 */
object NarratorChain {
    enum class Role { PROPHET, COMPANION, SUCCESSOR, NARRATOR, SHEIKH, AUTHOR }
    /** One person in the chain; [label] is the line under the name («صحابي», «شيخ البخاري», «أخرجه في صحيحه»). */
    data class Link(val name: String, val role: Role, val label: String)

    private val TASHKEEL = Regex("[ؐ-ًؚ-ٰٟـ]")
    private val PROPHET = Regex("رسول الله|النبي")
    private val PRAISE = Regex("رض[يى] الله عن\\S*|رحم[هة] الله|عليه السلام|عليهما السلام")
    private val PUNCT = Regex("[،,:؛.\"«»()\\[\\]\\-–ـ]")
    /** Words that hand the hadith on: the name after one of them is a narrator. */
    private val TRANSMIT = setOf("حدثنا", "وحدثنا", "حدثني", "وحدثني", "أخبرنا", "وأخبرنا", "أخبرني", "وأخبرني", "أنبأنا", "عن", "سمعت", "سمع")
    /** Words that end a name without starting a new one. */
    private val END = setOf("قال", "قالت", "يقول", "تقول", "أنه", "أنها", "أنهم", "يحدث")
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

    /** «صحيح مسلم 2691» -> «مسلم 2691», for the short line under the author. */
    private fun shortBook(ref: String) = ref.trim()
        .removePrefix("صحيح ").removePrefix("سنن ").removePrefix("جامع ").removePrefix("مسند ")
        .replace("أبي داود", "أبو داود")

    fun parse(detail: String, reference: String): List<Link>? {
        val plain = TASHKEEL.replace(detail, "").replace('‏', ' ').replace('‎', ' ')
        val at = PROPHET.find(plain)?.range?.first ?: return null
        val words = PUNCT.replace(PRAISE.replace(plain.substring(0, at), " "), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val names = ArrayList<String>()
        var current: MutableList<String>? = null       // the name being read, after a transmission word
        var afterThat = false                          // it came after «أن»: only a name if «حدثه» … closes it
        fun close(told: Boolean = false): Boolean {
            val c = current ?: return true
            current = null
            if (c.isEmpty()) return true                 // «… أن رسول الله»: nothing between them
            if (afterThat && !told) return false         // «أن تلبية رسول الله»: not a narrator
            val kept = c.takeWhile { it !in CUT }
            val name = kept.joinToString(" ").trim()
            if (name.isEmpty() || kept.size > 6 || kept.any { it in PROSE }) return false
            names += name
            return true
        }
        for (w in words) {
            when {
                w in TRANSMIT -> { if (!close()) return null; current = ArrayList(); afterThat = false }
                w == THAT -> { if (!close()) return null; current = ArrayList(); afterThat = true }
                w in TOLD -> { if (!close(told = true)) return null }
                w in END -> { if (!close()) return null }
                current != null -> current!!.add(w)
                else -> return null                     // text that no transmission word introduced: not a chain
            }
        }
        if (!close() || names.isEmpty()) return null
        val narrators = names.reversed().fold(emptyList<String>()) { acc, n -> if (acc.lastOrNull() == n) acc else acc + n }.map(::nominative)
        val author = AUTHORS.firstOrNull { reference.contains(it.first) }?.second
        // A chain that starts at the author's own teacher («حدثنا …») names him; one that starts at «عن» doesn't.
        val full = words.firstOrNull()?.let { it in TRANSMIT && it != "عن" } == true
        return buildList {
            add(Link("رسول الله ﷺ", Role.PROPHET, "قائل الحديث"))
            narrators.forEachIndexed { i, n ->
                val last = i == narrators.size - 1
                when {
                    i == 0 -> {
                        val woman = isWoman(n)
                        add(Link("$n ${if (woman) "رضي الله عنها" else "رضي الله عنه"}", Role.COMPANION, if (woman) "صحابية" else "صحابي"))
                    }
                    last && full && author != null -> add(Link(n, Role.SHEIKH, "شيخ ${author.removePrefix("الإمام ")}"))
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
