package com.pafoid.skate.editor.imgui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ContextPopupIdTest {
    @Test
    fun ContextPopupId_IsNeverEmpty() {
        assertTrue(contextPopupId("x").isNotEmpty())
        assertTrue(contextPopupId("x", 3).isNotEmpty())
    }

    @Test
    fun ContextPopupId_DiffersPerKey() {
        assertNotEquals(contextPopupId("hierarchy", 1), contextPopupId("hierarchy", 2))
        assertEquals(contextPopupId("hierarchy", 1), contextPopupId("hierarchy", 1))
    }

    @Test
    fun ContextPopupId_NoItemPopupWithoutExplicitId() {
        val root = File("src/main/kotlin")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }
            .filter { Regex("beginPopupContextItem\\(\\s*\\)").containsMatchIn(it.readText()) }
            .map { it.name }.toList()
        assertTrue(offenders.isEmpty(), "beginPopupContextItem() without id in: $offenders")
    }
}
