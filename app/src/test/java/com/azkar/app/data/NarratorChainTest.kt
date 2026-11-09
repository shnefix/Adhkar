package com.azkar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NarratorChainTest {
    private fun names(detail: String, ref: String) = NarratorChain.parse(detail, ref)?.map { it.name }

    @Test fun shortChainRunsFromProphetToAuthor() {
        assertEquals(
            listOf("رسول الله ﷺ", "أبو هريرة رضي الله عنه", "الإمام البخاري"),
            names("عَنْ أَبِي هُرَيْرَةَ ـ رضى الله عنه ـ أَنَّ رَسُولَ اللَّهِ صلى الله عليه وسلم قَالَ \" مَنْ قَالَ", "صحيح البخاري 6405")
        )
    }

    @Test fun fullIsnadIsReversed() {
        assertEquals(
            listOf("رسول الله ﷺ", "أبو هريرة رضي الله عنه", "أبو صالح", "سمي", "مالك", "عبد الله بن مسلمة", "الإمام البخاري"),
            names("حَدَّثَنَا عَبْدُ اللَّهِ بْنُ مَسْلَمَةَ، عَنْ مَالِكٍ، عَنْ سُمَيٍّ، عَنْ أَبِي صَالِحٍ، عَنْ أَبِي هُرَيْرَةَ، أَنَّ رَسُولَ اللَّهِ صلى الله عليه وسلم قَالَ", "صحيح البخاري 6405")
        )
    }

    @Test fun roles() {
        val c = NarratorChain.parse("عن أبي هريرة أن رسول الله قال", "صحيح مسلم 2691")!!
        assertEquals(listOf(NarratorChain.Role.PROPHET, NarratorChain.Role.COMPANION, NarratorChain.Role.AUTHOR), c.map { it.role })
    }

    @Test fun labelsFollowTheMockup() {
        val c = NarratorChain.parse(
            "حَدَّثَنَا عَبْدُ اللَّهِ بْنُ مَسْلَمَةَ، عَنْ مَالِكٍ، عَنْ سُمَيٍّ، عَنْ أَبِي صَالِحٍ، عَنْ أَبِي هُرَيْرَةَ، أَنَّ رَسُولَ اللَّهِ صلى الله عليه وسلم قَالَ\n\nوأخرجه أيضًا: صحيح مسلم 2691",
            "صحيح البخاري 6405"
        )!!
        assertEquals(listOf("قائل الحديث", "صحابي", "تابعي", "راوٍ", "إمام دار الهجرة", "شيخ البخاري", "أخرجه في صحيحه · ومسلم 2691"), c.map { it.label })
        assertEquals("عائشة رضي الله عنها", NarratorChain.parse("عن عائشة أن النبي قال", "سنن أبي داود 1")!![1].name)
    }

    @Test fun narratorIntroducedByThat() {
        assertEquals(listOf("رسول الله ﷺ", "محجن بن الأدرع رضي الله عنه", "حنظلة بن علي", "أبو داود"),
            names("عن حنظلة بن علي أن محجن بن الأدرع حدثه أن رسول الله قال", "سنن أبي داود 985"))
        // «أن رجلا جاء…» after the last narrator is the story: the chain ends at Anas.
        assertEquals(listOf("رسول الله ﷺ", "أنس رضي الله عنه", "الإمام البخاري"), names("عن أنس أن رجلا جاء إلى رسول الله", "صحيح البخاري 1"))
        assertNull(names("عن أنس أن تلبية رسول الله", "صحيح البخاري 1"))
    }

    @Test fun storyAfterTheLastNarratorEndsTheChain() {
        assertEquals(listOf("رسول الله ﷺ", "عبد الله رضي الله عنه", "عبد الرحمن بن يزيد", "إبراهيم بن سويد", "الحسن بن عبيد الله", "جرير", "عثمان بن أبي شيبة", "أبو داود"),
            names("حدثنا عثمان بن أبي شيبة، حدثنا جرير، عن الحسن بن عبيد الله، عن إبراهيم، بن سويد عن عبد الرحمن بن يزيد، عن عبد الله، قال كان نبي الله صلى الله عليه وسلم إذا أمسى", "سنن أبي داود 5071"))
        // A long story before the Prophet ﷺ may name the companion inside it: the last name is only «راوي القصة».
        assertEquals(listOf("رسول الله ﷺ", "البراء", "أبو إسحاق", "سفيان", "محمد بن كثير", "الإمام البخاري"),
            names("حدثنا محمد بن كثير، حدثنا سفيان، عن أبي إسحاق، قال سمعت البراء، رضى الله عنه وجاءه رجل فقال يا أبا عمارة أتوليت يوم حنين فقال أما أنا فأشهد على النبي", "صحيح البخاري 2930"))
    }

    @Test fun severalTeachersKeepTheFirstAndFatherIsNamed() {
        assertEquals(listOf("رسول الله ﷺ", "بريدة رضي الله عنه", "سليمان بن بريدة", "علقمة بن مرثد", "سفيان", "محمد بن عبد الله الأسدي", "أبو بكر بن أبي شيبة", "الإمام مسلم"),
            names("حدثنا أبو بكر بن أبي شيبة، وزهير بن حرب، قالا حدثنا محمد بن عبد الله، الأسدي عن سفيان، عن علقمة بن مرثد، عن سليمان بن بريدة، عن أبيه، قال كان رسول الله", "صحيح مسلم 975"))
        assertNull(names("عن أبيه أن رسول الله", "صحيح مسلم 1"))   // whose father: nobody before him
        assertNull(names("حدثنا سفيان عن زياد بن علاقة عن عمه قال كان النبي", "سنن الترمذي 3591"))
        assertEquals("صحابي", NarratorChain.parse("حدثنا قتيبة حدثنا سفيان عن محمد بن عبد الرحمن عن كريب عن ابن عباس عن جويرية أن النبي", "صحيح مسلم 2726")!![2].label)
        assertEquals("زيد", names("حدثنا إسحاق حدثنا يحيى أن زيدا حدثه عن أبي سلام عن أبي مالك أن رسول الله", "صحيح مسلم 223")!![3])
        // A story between narrators may hide one (here Ibn Umar): no chain rather than a wrong companion.
        assertNull(names("حدثنا إبراهيم بن المنذر حدثنا صدقة بن بشير قال سمعت قدامة بن إبراهيم الجمحي يحدث أنه كان يختلف إلى عبد الله بن عمر وهو غلام فحدثهم أن رسول الله", "سنن ابن ماجه 3801"))
        assertEquals(listOf("رسول الله ﷺ", "أبو هريرة رضي الله عنه", "عروة", "ابن شهاب", "عقيل", "الليث", "يحيى بن بكير", "الإمام البخاري"),
            names("حدثنا يحيى بن بكير حدثنا الليث عن عقيل عن ابن شهاب قال قال عروة قال أبو هريرة قال رسول الله", "صحيح البخاري 3276"))
        assertEquals("أنس رضي الله عنه", names("حدثنا آدم حدثنا شعبة عن عبد العزيز بن صهيب قال سمعت أنسا يقول كان النبي", "صحيح البخاري 142")!![1])
        assertNull(names("حدثنا مسدد حدثنا حماد ح وحدثنا موسى حدثنا حماد عن أنس أن النبي", "سنن أبي داود 1"))
        assertEquals("تابعي (مرسل)", NarratorChain.parse("حدثنا موسى حدثنا أبان عن قتادة أن النبي كان", "أبو داود (5092) (مرسل)")!![1].label)
    }

    @Test fun noProphetOrProseGivesNoChain() {
        assertNull(names("إِنَّهُمْ كَانُوا إِذَا قِيلَ لَهُمْ لَا إِلَٰهَ إِلَّا اللَّهُ يَسْتَكْبِرُونَ", "سورة الصافات: 35"))
        assertNull(names("كان الناس في زمن طويل يجتمعون في المسجد بعد صلاة العشاء فيذكرون رسول الله", ""))
    }
}
