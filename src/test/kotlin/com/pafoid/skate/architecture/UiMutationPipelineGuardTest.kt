package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readLines

class UiMutationPipelineGuardTest {

    @Test
    fun UiMutationPipeline_TargetedUiEntryPoints_DoNotExecuteCommandsDirectly() {
        val projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        val guardedFiles = listOf(
            "src/main/kotlin/com/pafoid/skate/editor/search/providers/ActionSearchProvider.kt",
            "src/main/kotlin/com/pafoid/skate/editor/ui/menus/ViewportContextMenu.kt",
        ).map { projectRoot.resolve(it) }

        val executeCommandPattern = Regex("""\.\s*executeCommand\s*\(""")
        val undoRedoImportPattern = Regex("""^\s*import\s+com\.pafoid\.skate\.editor\.systems\.UndoRedoManager\s*$""")

        val violations = mutableListOf<String>()
        guardedFiles.forEach { file ->
            val exists = Files.exists(file)
            assertTrue(exists, "Expected guarded file to exist: ${projectRoot.relativize(file).invariantSeparatorsPathString}")

            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trimStart()
                val isComment = trimmed.startsWith("//") || trimmed.startsWith("*")
                if (!isComment && (undoRedoImportPattern.matches(line) || executeCommandPattern.containsMatchIn(line))) {
                    val relativePath = projectRoot.relativize(file).invariantSeparatorsPathString
                    violations.add("$relativePath:${index + 1} -> ${line.trim()}")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "UI mutation pipeline violation detected in guarded entry points.\n" +
                violations.joinToString("\n")
        )
    }
}
