package com.pafoid.skate.editor.systems

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** AST-27 (AST-26 NQ1): editor strings and themes live in src/main/resources/editor and ship on the classpath. */
class EditorResourcesTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun DevRuns_ReadTheSourceFolder() {
        assertEquals(File(EditorResources.DEV_DIR, "strings"), EditorResources.dir("strings"))
        assertTrue(File(EditorResources.dir("strings"), "core.properties").isFile)
        assertTrue(File(EditorResources.dir("themes"), "dark.theme.json").isFile)
    }

    @Test
    fun WithoutTheSourceFolder_TheClasspathCopyIsUsed() {
        val strings = EditorResources.dir("strings", devRoot = File(dir, "missing"))
        assertNotNull(strings)
        strings!!
        assertTrue(File(strings, "core.properties").isFile, strings.path)
        assertFalse(strings.startsWith(File(EditorResources.DEV_DIR).absoluteFile))
    }

    @Test
    fun TheRepoAssetsFolderHasNoEditorFiles() {
        assertFalse(File("assets/strings/editor").exists())
        assertFalse(File("assets/themes").exists())
    }
}
