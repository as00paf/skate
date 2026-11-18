package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * AST-34 (docs/design/AST-17.md §4.3): JTS is editor-only. It is left out of the game jar (`isEditorOnlyLib` in
 * build.gradle.kts), so no code outside `editor/` may import it.
 */
class OsmEditorOnlyGuardTest {
    @Test
    fun JtsImports_OnlyUnderEditor() {
        val root = File("src/main/kotlin/com/pafoid/skate")
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.contains("/skate/editor/") }
            .filter { file -> file.readLines().any { it.trimStart().startsWith("import org.locationtech.jts") } }
            .map { it.invariantSeparatorsPath }
            .toList()
        assertTrue(offenders.isEmpty(), "JTS imported outside editor/: $offenders")
    }

    @Test
    fun GameJar_ExcludesJts() {
        val build = File("build.gradle.kts").readText()
        assertTrue(Regex("""fun isEditorOnlyLib\(jarName: String\).*jts-core""").containsMatchIn(build), "gameLibsJar must skip jts-core")
    }
}
