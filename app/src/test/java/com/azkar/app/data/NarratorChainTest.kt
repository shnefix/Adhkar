package com.azkar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NarratorChainTest {
    private fun names(detail: String, ref: String) = NarratorChain.parse(detail, ref)?.map { it.name }

    @Test fun shortChainRunsFromProphetToAuthor() {
        assertEquals(
            listOf("رسول الله ﷺ", "أبي هريرة", "الإمام البخاري"),
            names("عَنْ أَبِي هُرَيْرَةَ ـ رضى الله عنه ـ أَنَّ رَسُولَ اللَّهِ صلى الله عليه وسلم قَالَ \" مَنْ قَالَ", "صحيح البخاري 6405")
        )
    }

    @Test fun fullIsnadIsReversed() {
        assertEquals(
            listOf("رسول الله ﷺ", "أبي هريرة", "أبي صالح", "سمي", "مالك", "عبد الله بن مسلمة", "الإمام البخاري"),
            names("حَدَّثَنَا عَبْدُ اللَّهِ بْنُ مَسْلَمَةَ، عَنْ مَالِكٍ، عَنْ سُمَيٍّ، عَنْ أَبِي صَالِحٍ، عَنْ أَبِي هُرَيْرَةَ، أَنَّ رَسُولَ اللَّهِ صلى الله عليه وسلم قَالَ", "صحيح البخاري 6405")
        )
    }

    @Test fun roles() {
        val c = NarratorChain.parse("عن أبي هريرة أن رسول الله قال", "صحيح مسلم 2691")!!
        assertEquals(listOf(NarratorChain.Role.PROPHET, NarratorChain.Role.COMPANION, NarratorChain.Role.AUTHOR), c.map { it.role })
    }

    @Test fun noProphetOrProseGivesNoChain() {
        assertNull(names("إِنَّهُمْ كَانُوا إِذَا قِيلَ لَهُمْ لَا إِلَٰهَ إِلَّا اللَّهُ يَسْتَكْبِرُونَ", "سورة الصافات: 35"))
        assertNull(names("كان الناس في زمن طويل يجتمعون في المسجد بعد صلاة العشاء فيذكرون رسول الله", ""))
    }
}
