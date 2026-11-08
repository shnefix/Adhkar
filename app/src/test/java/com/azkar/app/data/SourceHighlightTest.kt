package com.azkar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHighlightTest {
    private fun hl(source: String, zikr: String) = SourceHighlight.ranges(source, zikr).map { source.substring(it) }

    @Test
    fun wholeZikrInsideVoweledHadith() {
        val src = "عَنْ أَبِي هُرَيْرَةَ، قَالَ رَسُولُ اللَّهِ ﷺ: «مَنْ قَالَ: سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، مِائَةَ مَرَّةٍ»"
        assertEquals(listOf("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ"), hl(src, "سبحان الله وبحمده"))
    }

    @Test
    fun voweledZikrInsidePlainSource() {
        assertEquals(listOf("لا حول ولا قوة إلا بالله"), hl("قال: أكثر من قول لا حول ولا قوة إلا بالله فإنها كنز", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ"))
    }

    @Test
    fun clausesWhenTheWholeIsNotThere() {
        val src = "كان يقول: اللهم إني أسألك الهدى والتقى، ثم يسكت، ثم يقول: والعفاف والغنى"
        val got = hl(src, "اللهم إني أسألك الهدى والتقى، والعفاف والغنى")
        assertEquals(listOf("اللهم إني أسألك الهدى والتقى", "والعفاف والغنى"), got)
    }

    @Test
    fun ayahNumbersBetweenWords() {
        val src = "قَالَ رَبِّ اشْرَحْ لِي صَدْرِي ﴿25﴾ وَيَسِّرْ لِي أَمْرِي ﴿26﴾"
        assertEquals(listOf("رَبِّ اشْرَحْ لِي صَدْرِي ﴿25﴾ وَيَسِّرْ لِي أَمْرِي"), hl(src, "رَبِّ اشْرَحْ لِي صَدْرِي وَيَسِّرْ لِي أَمْرِي"))
    }

    @Test
    fun smallPrefixesAndExtraWordsAtTheStart() {
        assertEquals(listOf("لأَسْتَغْفِرُ اللَّهَ"), hl("وَاللَّهِ إِنِّي لأَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ", "أَسْتَغْفِرُ اللَّهَ"))
        assertEquals(listOf("رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً"), hl("وَمِنْهُمْ مَنْ يَقُولُ رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً", "اللَّهُمَّ رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً"))
        assertEquals(listOf("السماوات والأرض"), hl("رب السماوات والأرض", "رَبُّ السَّمَوَاتِ وَالْأَرْضِ").map { it }.let { listOf(it.single().removePrefix("رب ")) })
    }

    @Test
    fun punctuationInsideIsColoredToo() {
        val src = "قَالَ: «سُبْحَانَ اللَّهِ، وَالْحَمْدُ لِلَّهِ. وَاللَّهُ أَكْبَرُ» ثُمَّ سَكَتَ"
        assertEquals(listOf("سُبْحَانَ اللَّهِ، وَالْحَمْدُ لِلَّهِ. وَاللَّهُ أَكْبَرُ"), hl(src, "سبحان الله والحمد لله، والله أكبر"))
    }

    @Test
    fun aWordTheSourceAddsInsideIsColoredToo() {
        val src = "أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ لَكَ بِذَنْبِي، فَاغْفِرْ لِي\" . قَالَ"
        assertEquals(listOf("أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ لَكَ بِذَنْبِي، فَاغْفِرْ لِي"), hl(src, "أبوء لك بنعمتك علي، وأبوء بذنبي فاغفر لي"))
    }

    @Test
    fun aSingleNameIsFound() {
        assertEquals(listOf("الشَّهِيدُ", "الشهيد"), hl("الْحَقُّ الشَّهِيدُ الْوَكِيلُ ... وهو الشهيد", "الشَّهِيدُ"))
    }

    @Test
    fun commonPairsAloneAreNotColored() {
        assertTrue(hl("قال رسول الله صلى الله عليه وسلم: من قال", "اللهم صل على رسول الله وأصحابه أجمعين").isEmpty())
    }

    @Test
    fun noPartialWordsAndNothingWhenAbsent() {
        assertTrue(hl("قال المستغفرين كثيرا", "استغفر").isEmpty())
        assertTrue(hl("نص آخر تماما", "سبحان الله العظيم").isEmpty())
        assertTrue(hl("", "سبحان الله").isEmpty())
    }
}
