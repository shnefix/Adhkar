package com.azkar.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTest {
    private fun github(
        tag: String = "v3.1",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """[{"name":"Adhkar-3.1.apk","size":2305776,"browser_download_url":"https://github.com/shnefix/Adhkar/releases/download/v3.1/Adhkar-3.1.apk"}]""",
    ) = """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"html_url":"https://github.com/shnefix/Adhkar/releases/tag/$tag",
        "body":"## Adhkar 3.1\n\n### Widgets\n- **Fixed** squashed widgets.\n- A live clock\n  on the dial.","assets":$assets}"""

    @Test
    fun readsTheLatestRelease() {
        val r = Release.fromGitHub(github())
        assertNotNull(r)
        assertEquals("3.1", r!!.version)
        assertEquals("https://github.com/shnefix/Adhkar/releases/download/v3.1/Adhkar-3.1.apk", r.apkUrl)
        assertEquals(2305776L, r.apkSize)
        assertEquals("https://github.com/shnefix/Adhkar/releases/tag/v3.1", r.pageUrl)
    }

    @Test
    fun offersNothingUsable() {
        assertNull(Release.fromGitHub(github(draft = true)))
        assertNull(Release.fromGitHub(github(prerelease = true)))
        assertNull(Release.fromGitHub(github(tag = "latest")))
        assertNull(Release.fromGitHub(github(assets = "[]")))
        assertNull(Release.fromGitHub(github(assets = """[{"name":"Adhkar-3.1-debug.apk","browser_download_url":"https://x/a.apk"}]""")))
        assertNull(Release.fromGitHub(github(assets = """[{"name":"Adhkar-3.1.apk","browser_download_url":"http://x/a.apk"}]""")))
        assertNull(Release.fromGitHub("not json"))
    }

    @Test
    fun picksTheReleaseApkAmongOtherFiles() {
        val r = Release.fromGitHub(github(assets = """[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
            {"name":"Adhkar-3.1-debug.apk","browser_download_url":"https://x/debug.apk"},
            {"name":"Adhkar-3.1.apk","size":5,"browser_download_url":"https://x/release.apk"}]"""))
        assertEquals("https://x/release.apk", r!!.apkUrl)
    }

    @Test
    fun keepsAcrossRestarts() {
        val r = Release.fromGitHub(github())!!
        assertEquals(r, Release.fromJson(r.toJson()))
        assertNull(Release.fromJson("{}"))
    }

    @Test
    fun comparesVersionsByNumber() {
        assertTrue(Versions.isNewer("3.1", "3.0"))
        assertTrue(Versions.isNewer("3.10", "3.9"))
        assertTrue(Versions.isNewer("3.1", "3.0.9"))
        assertTrue(Versions.isNewer("4", "3.9"))
        assertFalse(Versions.isNewer("3.0", "3.0"))
        assertFalse(Versions.isNewer("3", "3.0"))
        assertFalse(Versions.isNewer("2.1", "3.0"))
        assertFalse(Versions.isNewer("3.0.1-beta".substringBefore("-"), "3.0.1"))
        assertFalse(Versions.isNewer("latest", "3.0"))
        assertEquals(listOf(3, 1, 2), Versions.parts("3.1.2-beta"))
    }

    @Test
    fun summarisesTheNotes() {
        val notes = ReleaseNotes.summary(Release.fromGitHub(github())!!.notes)
        assertEquals(listOf("Fixed squashed widgets.", "A live clock on the dial."), notes)
        assertEquals(listOf("See the page"), ReleaseNotes.summary("- See [the page](https://example.com)"))
        assertEquals(1, ReleaseNotes.summary("- a\n- b\n- c", max = 1).size)
        assertTrue(ReleaseNotes.summary("- " + "x".repeat(300), maxChars = 50).single().let { it.length == 50 && it.endsWith("…") })
    }
}
