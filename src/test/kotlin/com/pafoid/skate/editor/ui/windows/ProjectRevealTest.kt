package com.pafoid.skate.editor.ui.windows

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-61: revealing a file in the Project window opens its folders, selects it and stops after one draw. */
class ProjectRevealTest {
    private val root = File("proj").absoluteFile
    private val james = File(root, "Assets/models/james.glb").path

    @Test
    fun Request_OpensOnlyTheFoldersAboveTheTarget() {
        val reveal = ProjectReveal().apply { request(james) }
        assertTrue(reveal.shouldOpen(root.path))
        assertTrue(reveal.shouldOpen(File(root, "Assets").path))
        assertTrue(reveal.shouldOpen(File(root, "Assets/models").path))
        assertFalse(reveal.shouldOpen(File(root, "Assets/textures").path))
        assertFalse(reveal.shouldOpen(File(root, "Assets/mod").path), "a name prefix is not a parent folder")
        assertFalse(reveal.shouldOpen(james), "the file itself is not a folder to open")
    }

    @Test
    fun Request_SelectsAndTargetsTheFile_ThenDoneStopsOpeningFolders() {
        val reveal = ProjectReveal().apply { request(james) }
        assertTrue(reveal.isTarget(File(root, "Assets/./models/james.glb").path), "paths are normalized")
        assertTrue(reveal.isSelected(james))
        assertTrue(reveal.wantsFocus)

        reveal.focusTaken()
        reveal.done()
        assertNull(reveal.target)
        assertFalse(reveal.wantsFocus)
        assertFalse(reveal.shouldOpen(root.path), "folders can be closed again afterwards")
        assertTrue(reveal.isSelected(james), "the file stays selected")
    }

    @Test
    fun ClickSelectsAnotherFile() {
        val reveal = ProjectReveal().apply { request(james) }
        val other = File(root, "Assets/a.png").path
        reveal.select(other)
        assertTrue(reveal.isSelected(other))
        assertFalse(reveal.isSelected(james))
    }

    @Test
    fun FilterThatHidesTheTarget_IsDetected() {
        val reveal = ProjectReveal()
        assertFalse(reveal.filterHidesTarget("tex"), "no request, nothing to clear")
        reveal.request(james)
        assertTrue(reveal.filterHidesTarget("tex"))
        assertFalse(reveal.filterHidesTarget("JAM"))
        assertFalse(reveal.filterHidesTarget("  "))
    }
}
