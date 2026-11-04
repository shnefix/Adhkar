package com.azkar.app.data

import com.azkar.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportJsonTest {
    private fun ok(raw: String) = ImportJson.parse(raw) as ImportJson.Parsed.Ok
    private fun error(raw: String) = ImportJson.parse(raw) as ImportJson.Parsed.Error

    @Test
    fun readsFoldersAzkarAndDefaults() {
        val p = ok("""{"folders":[{"name":"Morning","azkar":[{"text":"سبحان الله","count":33},{"text":"الحمد لله"}]}]}""")
        assertEquals(1, p.folders.size)
        assertEquals("Morning", p.folders[0].name)
        assertEquals(listOf(33, 1), p.folders[0].items.map { it.count })
    }

    @Test
    fun countAcceptsNumberStringsAndWholeDoubles() {
        val p = ok("""{"folders":[{"name":"F","azkar":[{"text":"a","count":"7"},{"text":"b","count":10.0}]}]}""")
        assertEquals(listOf(7, 10), p.folders[0].items.map { it.count })
    }

    @Test
    fun titlePositionAndNoteAreKept() {
        val p = ok("""{"folders":[{"name":"F","azkar":[{"text":"a","note":"n","title":"t","titlePosition":"below"}]}]}""")
        val item = p.folders[0].items[0]
        assertEquals("n", item.note)
        assertEquals("t", item.title)
        assertEquals(TitlePos.BELOW, item.titlePos)
    }

    @Test
    fun allTitlePositionsRoundTrip() {
        val p = ok("""{"folders":[{"name":"F","azkar":[
            {"text":"a","title":"t"},{"text":"b","title":"t","titlePosition":"BOTH"},
            {"text":"c","title":"t","titlePosition":"hidden"},{"text":"d","title":"t","titlePosition":"sideways"}]}]}""")
        assertEquals(listOf(TitlePos.ABOVE, TitlePos.BOTH, TitlePos.HIDDEN, TitlePos.ABOVE), p.folders[0].items.map { it.titlePos })
        val (state, _) = ImportMerge.apply(ImportMerge.State(emptyList(), emptyList(), emptyList()), p) { java.util.UUID.randomUUID().toString() }
        val back = ok(ExportJson.build(state, arabicSize = 1, notificationsOn = false))
        assertEquals(listOf(TitlePos.ABOVE, TitlePos.BOTH, TitlePos.HIDDEN, TitlePos.ABOVE), back.folders[0].items.map { it.titlePos })
        // Database codes: the old "below" flag values 0 / 1 keep their meaning.
        assertEquals(listOf(TitlePos.ABOVE, TitlePos.BELOW, TitlePos.BOTH, TitlePos.HIDDEN), (0..3).map { TitlePos.of(it) })
    }

    @Test
    fun sourceIsReadMergedAndExported() {
        val p = ok("""{"folders":[{"name":"F","azkar":[{"text":"a","source":" صحيح مسلم ","sourceDetail":"حديث 591",
            "narrator":"ثوبان","when":"بعد الصلاة","grade":"صحيح"},{"text":"b"}]}]}""")
        val full = ZikrSource("صحيح مسلم", "حديث 591", "ثوبان", "بعد الصلاة", "صحيح")
        assertEquals(listOf(full, ZikrSource()), p.folders[0].items.map { it.source })
        assertTrue(p.folders[0].items[1].source.isEmpty)
        var n = 0
        val (state, _) = ImportMerge.apply(ImportMerge.State(emptyList(), emptyList(), emptyList()), p) { "id${n++}" }
        assertEquals(listOf(full, ZikrSource()), state.zikrs.map { it.source })
        val back = ok(ExportJson.build(state, arabicSize = 1, notificationsOn = false))
        assertEquals(listOf(full, ZikrSource()), back.folders[0].items.map { it.source })
    }

    @Test
    fun structuralErrorsNameTheProblem() {
        assertEquals(R.string.import_err_empty, error("  ").res)
        assertEquals(R.string.import_err_bare_list, error("[]").res)
        assertEquals(R.string.import_err_invalid_json, error("{nope").res)
        assertEquals(R.string.import_err_no_folders, error("{}").res)
        assertEquals(R.string.import_err_folders_not_list, error("""{"folders":"x"}""").res)
        assertEquals(R.string.import_err_folders_empty, error("""{"folders":[]}""").res)
    }

    @Test
    fun zikrErrorsCarryWhereTheyHappened() {
        val e = error("""{"folders":[{"name":"F","azkar":[{"text":"a","count":1000}]}]}""")
        assertEquals(R.string.import_err_zikr_count, e.res)
        val where = e.args[0] as ImportJson.Parsed.Error
        assertEquals(R.string.import_where_folder, where.res)
        assertEquals(listOf<Any>("F"), where.args)
        assertEquals(1, e.args[1])
    }

    @Test
    fun duplicateFolderIdsAreRejected() {
        val e = error("""{"folders":[{"id":"x","name":"A","azkar":[{"text":"a"}]},{"id":"x","name":"B","azkar":[{"text":"b"}]}]}""")
        assertEquals(R.string.import_err_duplicate_id, e.res)
    }

    @Test
    fun reminderTimeIsValidatedAndBlankMessageKeptBlank() {
        val p = ok("""{"folders":[{"name":"F","azkar":[{"text":"a"}]}],"reminders":[{"title":"R","time":"06:30"}]}""")
        assertEquals(6, p.reminders[0].hour)
        assertEquals(30, p.reminders[0].minute)
        assertEquals("", p.reminders[0].message)   // the default text is chosen in the phone's language when it fires
        val e = error("""{"folders":[{"name":"F","azkar":[{"text":"a"}]}],"reminders":[{"title":"R","time":"25:00"}]}""")
        assertEquals(R.string.import_err_time, e.res)
    }

    @Test
    fun prayerSettingsRoundTripThroughExport() {
        val state = ImportMerge.State(listOf(Folder("f", "F")), listOf(Zikr("z", "a", "", 1, "f")), emptyList())
        val p = ok(ExportJson.build(state, 1, true, calcMethod = "UMM_AL_QURA", asrHanafi = true))
        assertEquals("UMM_AL_QURA", p.settings?.calcMethod)
        assertEquals(true, p.settings?.asrHanafi)
    }

    @Test
    fun exportedReminderTimesUseAsciiDigitsInAnyLocale() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            val state = ImportMerge.State(emptyList(), listOf(Zikr("z", "a", "", 1, null)),
                listOf(Schedule("s", "R", "", 6, 5, true, null)))
            val p = ok(ExportJson.build(state, 1, true))
            assertEquals(6, p.reminders[0].hour)
            assertEquals(5, p.reminders[0].minute)
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test
    fun mergeReportsTheIdsOfAddedRemindersOnly() {
        val p = ok("""{"folders":[{"name":"F","azkar":[{"text":"a"}]}],"reminders":[{"id":"r1","title":"A","time":"06:30"},{"id":"old","title":"B","time":"07:00"}]}""")
        val existing = ImportMerge.State(emptyList(), emptyList(), listOf(Schedule("old", "B", "", 7, 0, true, null)))
        val (_, sum) = ImportMerge.apply(existing, p) { "new" }
        assertEquals(mapOf("r1" to "r1"), sum.reminderIds)
    }
}
