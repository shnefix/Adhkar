package com.azkar.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentChangesTest {
    private fun folder(id: String, name: String = id, position: Int = 0) = FolderEntity(id, name, null, false, position)

    @Test
    fun identicalContentHasNoChanges() {
        val list = listOf(folder("a", position = 0), folder("b", position = 1))
        assertTrue(ContentChanges.between(list, list, emptyList(), emptyList(), emptyList(), emptyList()).isEmpty)
    }

    @Test
    fun renameUpdatesOnlyThatRow() {
        val old = listOf(folder("a", position = 0), folder("b", position = 1))
        val new = listOf(folder("a", position = 0), folder("b", "B", position = 1))
        val (upserts, deletes) = ContentChanges.diff(old, new) { it.id }
        assertEquals(listOf(folder("b", "B", position = 1)), upserts)
        assertTrue(deletes.isEmpty())
    }

    @Test
    fun addedAndRemovedRowsAreFound() {
        val old = listOf(folder("a", position = 0), folder("b", position = 1))
        val new = listOf(folder("a", position = 0), folder("c", position = 1))
        val (upserts, deletes) = ContentChanges.diff(old, new) { it.id }
        assertEquals(listOf(folder("c", position = 1)), upserts)
        assertEquals(listOf("b"), deletes)
    }

    @Test
    fun reorderRewritesTheMovedPositions() {
        val old = listOf(folder("a", position = 0), folder("b", position = 1), folder("c", position = 2))
        val new = listOf(folder("b", position = 0), folder("a", position = 1), folder("c", position = 2))
        val (upserts, _) = ContentChanges.diff(old, new) { it.id }
        assertEquals(setOf("a", "b"), upserts.map { it.id }.toSet())
    }
}
