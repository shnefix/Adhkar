package com.azkar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {
    private val travel = Folder("f1", "أذكار السفر", null)
    private val sleep = Folder("f2", "أذكار النوم", null)
    private fun z(id: String, text: String, folder: String?, title: String = "", detail: String = "", ref: String = "") =
        Zikr(id, text, "", 1, folder, title, TitlePos.ABOVE, ZikrSource(reference = ref, detail = detail))

    private val index = LibrarySearch.Index(AzkarContent(listOf(travel, sleep), listOf(
        z("a", "اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَذَا الْبِرَّ وَالتَّقْوَى", "f1", ref = "صحيح مسلم 1342"),
        z("b", "أَسْتَوْدِعُ اللَّهَ دِينَكَ", "f1", title = "توديع المسافر"),
        z("c", "سُبْحَانَ اللَّهِ", "f2", detail = "عن أبي هريرة قال: من قالها في سفره كتبت له"),
        z("d", "أَسْتَغْفِرُ اللَّهَ", null)
    ), emptyList()))

    @Test fun ignoresTashkeelAndHamza() {
        val r = index.search("استغفر")
        assertEquals(listOf("d"), r.zikrs.map { it.zikr.id })
        assertEquals(0..10, r.zikrs[0].ranges[0])    // «أَسْتَغْفِرُ» in the original, marks included, up to «ر»
    }

    @Test fun findsFoldersAndRanksTextBeforeTitleBeforeHadith() {
        val r = index.search("سفر")
        assertEquals(listOf("f1"), r.folders.map { it.folder.id })
        assertEquals(listOf("a", "c"), r.zikrs.map { it.zikr.id })
        assertEquals(LibrarySearch.Where.TEXT, r.zikrs[0].where)
        assertEquals(LibrarySearch.Where.HADITH, r.zikrs[1].where)
        assertTrue(r.zikrs[1].snippet.contains("سفره"))
        assertEquals(LibrarySearch.Where.TITLE, index.search("المسافر").zikrs.single().where)
        assertEquals(LibrarySearch.Where.SOURCE, index.search("مسلم").zikrs.single().where)
    }
}
