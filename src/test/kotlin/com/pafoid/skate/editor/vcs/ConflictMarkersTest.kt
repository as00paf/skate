package com.pafoid.skate.editor.vcs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConflictMarkersTest {
    @Test
    fun HasMarkers_FindsGitConflictBlocksOnly() {
        assertTrue(ConflictMarkers.hasMarkers("{\n<<<<<<< HEAD\n\"a\": 1\n=======\n\"a\": 2\n>>>>>>> theirs\n}"))
        assertFalse(ConflictMarkers.hasMarkers("{\"text\": \"a <<<<<<< b\"}"))
        assertFalse(ConflictMarkers.hasMarkers("{\n  \"a\": 1\n}"))
    }

    @Test
    fun RequireClean_RejectsMarkersWithTheFileName() {
        assertEquals("{}", ConflictMarkers.requireClean("A.scene", "{}"))
        val e = assertThrows<ConflictMarkersException> { ConflictMarkers.requireClean("A.scene", "<<<<<<< HEAD\n") }
        assertTrue(e.message!!.contains("A.scene"))
    }
}
