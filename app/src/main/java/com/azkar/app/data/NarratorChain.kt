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
    private val PRAISE = Regex("رض[يى] الله عن\\S*|عليه السلام|عليهما السلام")
    private val PUNCT = Regex("[،,:؛.\"«»()\\[\\]\\-–ـ]")
    /** Words that hand the hadith on: the name after one of them is a narrator. */
    private val TRANSMIT = setOf("حدثنا", "وحدثنا", "حدثني", "وحدثني", "أخبرنا", "وأخبرنا", "أخبرني", "وأخبرني", "أنبأنا", "عن", "سمعت", "سمع")
    /** Words that end a name without starting a new one. */
    private val END = setOf("قال", "قالت", "يقول", "تقول", "أن", "أنه", "أنها", "أنهم", "يحدث", "حدثه")
    /** Words where a name's description starts («عائشة زوج النبي», «يعني ابن عفان»): the name is what comes before. */
    private val CUT = setOf("زوج", "مولى", "يعني", "يبلغ", "يرفعه", "وكانت", "وكان")
    /** Words never part of a name: a piece holding one is prose. */
    private val PROSE = setOf(
        "كان", "كانت", "دخل", "جاء", "رأى", "رأيت", "يأمر", "قام", "لا", "لم", "ألا", "إلا", "إذا", "سأل", "أتى", "خرج",
        "بت", "عند", "حجة", "حتى", "في", "على", "إلى", "من", "ما", "هذا", "بعض", "أصحاب"
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
        fun close(): Boolean {
            val c = current ?: return true
            current = null
            val kept = c.takeWhile { it !in CUT }
            val name = kept.joinToString(" ").trim()
            if (name.isEmpty() || kept.size > 6 || kept.any { it in PROSE }) return false
            names += name
            return true
        }
        for (w in words) {
            when {
                w in TRANSMIT -> { if (!close()) return null; current = ArrayList() }
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
                    i == 1 -> add(Link(n, Role.SUCCESSOR, "تابعي"))
                    else -> add(Link(n, Role.NARRATOR, "راوٍ"))
                }
            }
            if (author != null) {
                val also = ALSO.find(detail)?.groupValues?.get(1)?.let { " · و" + shortBook(it) }.orEmpty()
                add(Link(author, Role.AUTHOR, "أخرجه في ${BOOK_KIND[author] ?: "كتابه"}$also"))
            }
        }
    }
}
