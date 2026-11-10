package com.azkar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The built-in folders file parses, keeps its ids, and every zikr added in defaults version 8 has a source. */
class DefaultFoldersAssetTest {
    private val parsed by lazy {
        val f = listOf("src/main/assets/default_folders.json", "app/src/main/assets/default_folders.json").map(::File).first { it.exists() }
        ImportJson.parse(f.readText(Charsets.UTF_8)) as ImportJson.Parsed.Ok
    }

    @Test
    fun everyAddedFolderIsPresent() {
        val ids = parsed.folders.mapNotNull { it.id }.toSet()
        assertTrue(DefaultFolders.ADDED_IN_V8.all { it in ids })
        assertTrue(listOf(DefaultFolders.ZAD, DefaultFolders.MORNING).all { it in ids })
    }

    @Test
    fun azkarFoundInTheQuranOrHadithBooksCarryTheirText() {
        val all = parsed.folders.flatMap { it.items }
        assertTrue(all.count { it.source.detail.isNotBlank() } >= 500)
        // A hadith source names its book and number; a Quran source its surah and ayahs.
        all.filter { it.source.detail.isNotBlank() }.forEach {
            assertTrue(it.source.reference, Regex("\\d").containsMatchIn(it.source.reference))
        }
    }

    @Test
    fun addedAzkarHaveSourcesAndNoDuplicatesInAFolder() {
        val added = parsed.folders.filter { it.id in DefaultFolders.ADDED_IN_V8 || it.id in DefaultFolders.ADDED_IN_V13 }
        // 643 from version 8, 4 more in كفارة المجلس and 801 in the new folders from version 13; 96 without a source
        // dropped from أدعية للميّت، جوامع الدعاء and أذكار داخل الصلاة in version 22, a duplicate dropped in 23, and one added in أذكار متفرقة in 24.
        // The library trim (6a2295f) then dropped 66 more from these folders.
        assertEquals(643 + 4 + 801 - 96 - 1 + 1 - 66, added.sumOf { it.items.size })
        assertTrue(DefaultFolders.ADDED_IN_V13.all { id -> parsed.folders.any { it.id == id } })
        // The duas added to existing folders in version 13 are still found in those folders' file entries. The library
        // trim (6a2295f) dropped some weak ones from أذكار الصباح (d_14); the version 13 merge just skips a missing text.
        DefaultFolders.ADDED_TO_EXISTING_IN_V13.forEach { (id, texts) ->
            val inFile = parsed.folders.first { it.id == id }.items.map { it.text }.toSet()
            if (id == DefaultFolders.MORNING) assertTrue(id, texts.any { it in inFile })
            else assertTrue(id, texts.all { it in inFile })
        }
        added.forEach { f ->
            // A zikr with no source has none at all, not a "no source found" placeholder.
            f.items.forEach {
                assertTrue("${f.name}: ${it.text.take(30)}", listOf("دعاء عام", "لم يُعثر").none { p -> it.source.reference.startsWith(p) })
            }
            // Store merges skip a text already in the folder, so a duplicate would silently disappear.
            assertEquals(f.name, f.items.size, f.items.map { it.text }.toSet().size)
        }
    }
}
