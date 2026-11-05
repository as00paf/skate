package com.pafoid.skate.editor.systems

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-18: per-user editor files live in the OS user-data folder, not the working directory. */
class EditorPathsTest {

    @Test
    fun Windows_UsesAppData() {
        val dir = EditorPaths.userDataDir(mapOf("APPDATA" to "C:/Users/al/AppData/Roaming"), "Windows 11", "C:/Users/al", override = null)
        assertEquals(File("C:/Users/al/AppData/Roaming", "Skate"), dir)
    }

    @Test
    fun Windows_WithoutAppData_FallsBackUnderHome() {
        assertEquals(File("C:/Users/al/AppData/Roaming", "Skate"), EditorPaths.userDataDir(emptyMap(), "Windows 10", "C:/Users/al", override = null))
    }

    @Test
    fun Mac_UsesApplicationSupport() {
        assertEquals(File("/Users/al", "Library/Application Support/Skate"), EditorPaths.userDataDir(emptyMap(), "Mac OS X", "/Users/al", override = null))
    }

    @Test
    fun Linux_UsesXdgConfigHomeOrDotConfig() {
        assertEquals(File("/xdg", "skate"), EditorPaths.userDataDir(mapOf("XDG_CONFIG_HOME" to "/xdg"), "Linux", "/home/al", override = null))
        assertEquals(File("/home/al/.config", "skate"), EditorPaths.userDataDir(emptyMap(), "Linux", "/home/al", override = null))
    }

    @Test
    fun OverrideProperty_Wins() {
        assertEquals(File("build/ud"), EditorPaths.userDataDir(mapOf("APPDATA" to "x"), "Windows 11", "h", override = "build/ud"))
    }

    @Test
    fun DefaultProjectsDir_PrefersDocuments(@TempDir home: File) {
        assertEquals(File(home, "Skate Projects"), EditorPaths.defaultProjectsDir(home.path))
        File(home, "Documents").mkdirs()
        assertEquals(File(home, "Documents/Skate Projects"), EditorPaths.defaultProjectsDir(home.path))
    }

    @Test
    fun EditorCode_DoesNotOpenUserFilesInTheWorkingDirectory() {
        val names = listOf(EditorPaths.SETTINGS_FILE, EditorPaths.IMGUI_INI, EditorPaths.OPEN_WINDOWS_FILE, EditorPaths.SEARCH_HISTORY_FILE)
        val bare = Regex("""File\(\s*"(${names.joinToString("|") { Regex.escape(it) }})"\s*\)""")
        val offenders = File("src/main/kotlin/com/pafoid/skate").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { bare.containsMatchIn(it.readText()) }
            .map { it.path }.toList()
        assertTrue(offenders.isEmpty(), "Use EditorPaths.file(...) for per-user files: $offenders")
    }
}
