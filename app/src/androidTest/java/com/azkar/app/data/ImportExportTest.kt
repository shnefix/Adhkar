package com.azkar.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportExportTest {
    @Test
    fun exportedDataCanBeParsedBack() {
        val state = ImportMerge.State(
            listOf(Folder("morning", "Morning", null, false)),
            listOf(Zikr("z1", "Sample zikr", "", 3, "morning")),
            emptyList()
        )
        val parsed = ImportJson.parse(ExportJson.build(state, 1, true))
        assertTrue(parsed is ImportJson.Parsed.Ok)
    }

    @Test
    fun trackingAndPrayerSettingsSurviveABackup() {
        val state = ImportMerge.State(
            listOf(Folder("d_14", "Morning", null, true)),
            listOf(Zikr("z1", "Sample zikr", "", 3, "d_14")),
            emptyList()
        )
        val tracking = org.json.JSONObject()
            .put("folders", org.json.JSONArray(listOf("d_14")))
            .put("history", org.json.JSONObject().put("d_14", org.json.JSONObject().put("20261103", "1/1").put("bad", "x")))
        val raw = ExportJson.build(state, 1, true, sessionDelays = mapOf("m" to 30, "s" to 120), nudgeOn = false, nudgeMinutes = 60, tracking = tracking)
        val parsed = ImportJson.parse(raw) as ImportJson.Parsed.Ok
        assertEquals(mapOf("m" to 30, "s" to 120), parsed.settings?.prayerDelays)
        assertEquals(false, parsed.settings?.nudgeOn)
        assertEquals(60, parsed.settings?.nudgeMinutes)
        assertEquals(listOf("d_14"), parsed.tracking?.folders)
        // The malformed day is skipped, the good one kept.
        assertEquals(mapOf("20261103" to Tracking.Progress(1, 1)), parsed.tracking?.history?.get("d_14"))
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
