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
        assertTrue(listOf(DefaultFolders.HOME, DefaultFolders.ZAD, DefaultFolders.MORNING).all { it in ids })
    }

    @Test
    fun addedAzkarHaveSourcesAndNoDuplicatesInAFolder() {
        val added = parsed.folders.filter { it.id in DefaultFolders.ADDED_IN_V8 }
        assertEquals(643, added.sumOf { it.items.size })
        added.forEach { f ->
            f.items.forEach { assertTrue("${f.name}: ${it.text.take(30)}", it.source.reference.isNotBlank()) }
            // Store merges skip a text already in the folder, so a duplicate would silently disappear.
            assertEquals(f.name, f.items.size, f.items.map { it.text }.toSet().size)
        }
    }
}
