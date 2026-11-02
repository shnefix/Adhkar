package com.azkar.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportExportTest {
    @Test
    fun exportedDataCanBeParsedBack() {
        val state = ImportMerge.State(
            listOf(Folder("morning", "Morning", null, false)),
            listOf(Zikr("z1", "سُبْحَانَ اللَّهِ", "", 3, "morning")),
            emptyList()
        )
        val parsed = ImportJson.parse(ExportJson.build(state, 1, true))
        assertTrue(parsed is ImportJson.Parsed.Ok)
    }

    @Test
    fun malformedBackupIsRejected() {
        assertTrue(ImportJson.parse("{\"folders\":\"broken\"}") is ImportJson.Parsed.Error)
    }

    @Test
    fun duplicateFolderIdsAreRejected() {
        val raw = """{"folders":[
            {"id":"same","name":"One","azkar":[{"text":"First","count":1}]},
            {"id":"same","name":"Two","azkar":[{"text":"Second","count":1}]}
        ]}"""
        assertTrue(ImportJson.parse(raw) is ImportJson.Parsed.Error)
    }
}
