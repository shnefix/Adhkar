package com.azkar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {
    private val folders = listOf(
        Folder("a", "A"),
        Folder("b", "B", parent = "a"),
        Folder("c", "C", parent = "b"),
        Folder("d", "D"),
        Folder("orphan", "O", parent = "gone"),
    )

    @Test
    fun topLevelIncludesFoldersWhoseParentIsGone() {
        assertEquals(listOf("a", "d", "orphan"), FolderTree.children(folders, null).map { it.id })
        assertEquals(listOf("b"), FolderTree.children(folders, "a").map { it.id })
    }

    @Test
    fun subtreeHoldsTheFolderAndEverythingBelowIt() {
        assertEquals(setOf("a", "b", "c"), FolderTree.subtree(folders, "a"))
        assertEquals(setOf("d"), FolderTree.subtree(folders, "d"))
    }

    @Test
    fun flattenListsParentsBeforeTheirFoldersWithDepth() {
        val flat = FolderTree.flatten(folders).map { it.first.id to it.second }
        assertEquals(listOf("a" to 0, "b" to 1, "c" to 2, "d" to 0, "orphan" to 0), flat)
    }

    @Test
    fun cyclesAreDetected() {
        val loop = listOf(Folder("x", "X", parent = "y"), Folder("y", "Y", parent = "x"))
        assertTrue(FolderTree.cycles(loop, "x"))
        assertFalse(FolderTree.cycles(folders, "c"))
    }

    @Test
    fun readableLeavesOutFoldersWithNoAzkarOfTheirOwn() {
        assertEquals(listOf("c", "d"), FolderTree.readable(folders, setOf("c", "d")).map { it.id })
        assertEquals(emptyList<String>(), FolderTree.readable(folders, emptySet()).map { it.id })
    }

    @Test
    fun aParentFolderReadsTheFirstFolderBelowItThatHasAzkar() {
        assertEquals("c", FolderTree.firstReadable(folders, "a", setOf("c", "d")))
        assertEquals("b", FolderTree.firstReadable(folders, "a", setOf("b", "c")))
        assertEquals("a", FolderTree.firstReadable(folders, "a", setOf("a", "c")))
        assertEquals("d", FolderTree.firstReadable(folders, "d", setOf("c")))       // empty, nothing below: unchanged
        assertEquals("gone", FolderTree.firstReadable(folders, "gone", setOf("c"))) // deleted folder: unchanged
    }

    @Test
    fun pathNamesTheFoldersAFolderSitsIn() {
        assertEquals("A ‹ B ‹ C", FolderTree.path(folders, "c"))
        assertEquals("O", FolderTree.path(folders, "orphan"))
        assertEquals("", FolderTree.path(folders, "gone"))
        val loop = listOf(Folder("x", "X", parent = "y"), Folder("y", "Y", parent = "x"))
        assertEquals("Y ‹ X", FolderTree.path(loop, "x"))
    }

    @Test
    fun importKeepsParentsOfCreatedFoldersAndMapsFileIds() {
        val p = ImportJson.parse(
            """{"folders":[
                {"id":"p1","name":"Parent","azkar":[{"text":"z"}]},
                {"id":"k1","name":"Kid","parent":"p1","azkar":[{"text":"z"}]},
                {"id":"k2","name":"Into existing","parent":"a","azkar":[{"text":"z"}]},
                {"id":"k3","name":"Nowhere","parent":"missing","azkar":[{"text":"z"}]}
            ]}"""
        ) as ImportJson.Parsed.Ok
        var n = 0
        val (state, _) = ImportMerge.apply(ImportMerge.State(folders, emptyList(), emptyList()), p) { "new${n++}" }
        val byName = state.folders.associateBy { it.name }
        assertEquals(byName.getValue("Parent").id, byName.getValue("Kid").parent)
        assertEquals("a", byName.getValue("Into existing").parent)
        assertNull(byName.getValue("Nowhere").parent)
    }

    @Test
    fun importBreaksAParentLoopInTheFile() {
        val p = ImportJson.parse(
            """{"folders":[{"id":"x","name":"X","parent":"y","azkar":[{"text":"z"}]},{"id":"y","name":"Y","parent":"x","azkar":[{"text":"z"}]}]}"""
        ) as ImportJson.Parsed.Ok
        val (state, _) = ImportMerge.apply(ImportMerge.State(emptyList(), emptyList(), emptyList()), p) { "id" }
        state.folders.forEach { assertFalse(FolderTree.cycles(state.folders, it.id)) }
    }
}
