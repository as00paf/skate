package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-60 §3: JGit stays inside `editor/vcs/` (only [ALLOWED] imports it), so the engine and exported games never
 * depend on it; build.gradle.kts also leaves its jars out of the game jar.
 */
class VcsLayeringGuardTest {
    private val main = File("src/main/kotlin/com/pafoid/skate")

    @Test
    fun JGit_IsOnlyImportedByTheVcsService() {
        val found = main.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.readLines().any { JGIT_IMPORT.containsMatchIn(it) } }
            .map { it.relativeTo(main).invariantSeparatorsPath }
            .toSet()
        assertEquals(ALLOWED, found)
    }

    @Test
    fun GameJar_LeavesTheEditorOnlyLibrariesOut() {
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("""!isEditorOnlyLib(it.name)"""), "gameLibsJar must filter editor-only jars")
        assertTrue(build.contains("""jarName.startsWith("org.eclipse.jgit")"""))
    }

    @Test
    fun Guard_DetectsAJGitImport() {
        assertTrue(JGIT_IMPORT.containsMatchIn("import org.eclipse.jgit.api.Git"))
        assertTrue(!JGIT_IMPORT.containsMatchIn("// org.eclipse.jgit is fine in a comment"))
    }

    companion object {
        private val JGIT_IMPORT = Regex("""^\s*import\s+org\.eclipse\.jgit\.""")
        private val ALLOWED = setOf("editor/vcs/JGitVcsService.kt")
    }
}
