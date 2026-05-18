package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readLines

class UiMutationPipelineGuardTest {

    @Test
    fun UiMutationPipeline_UiEntryPoints_DoNotExecuteCommandsDirectly() {
        val projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        val guardedRoots = listOf(
            "src/main/kotlin/com/pafoid/skate/editor/search/providers",
            "src/main/kotlin/com/pafoid/skate/editor/ui/windows",
            "src/main/kotlin/com/pafoid/skate/editor/ui/menus",
            "src/main/kotlin/com/pafoid/skate/editor/imgui",
        ).map { projectRoot.resolve(it) }

        val executeCommandPattern = Regex("""\.\s*executeCommand\s*\(""")

        val guardedFiles = guardedRoots
            .flatMap { root ->
                assertTrue(Files.exists(root), "Expected guarded path to exist: ${projectRoot.relativize(root).invariantSeparatorsPathString}")
                Files.walk(root).use { paths ->
                    paths
                        .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                        .toList()
                }
            }
            .sortedBy { projectRoot.relativize(it).invariantSeparatorsPathString }

        assertFalse(guardedFiles.isEmpty(), "Expected at least one guarded UI entrypoint file")

        val violations = mutableListOf<String>()
        guardedFiles.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trimStart()
                val isComment = trimmed.startsWith("//") || trimmed.startsWith("*")
                if (!isComment && executeCommandPattern.containsMatchIn(line)) {
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

    @Test
    fun UiMutationPipeline_UiEntryPoints_DoNotCallProjectMutationsDirectly() {
        val projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        val guardedRoots = listOf(
            "src/main/kotlin/com/pafoid/skate/editor/search/providers",
            "src/main/kotlin/com/pafoid/skate/editor/ui/windows",
            "src/main/kotlin/com/pafoid/skate/editor/ui/menus",
            "src/main/kotlin/com/pafoid/skate/editor/imgui",
        ).map { projectRoot.resolve(it) }

        val forbiddenPatterns = listOf(
            Regex("""projectManager\.\s*openProject\s*\("""),
            Regex("""projectManager\.\s*closeProject\s*\("""),
            Regex("""projectManager\.\s*createProject\s*\("""),
            Regex("""projectManager\.\s*loadLastProject\s*\("""),
            Regex("""projectManager\.\s*saveProject\s*\("""),
        )

        val guardedFiles = guardedRoots
            .flatMap { root ->
                assertTrue(Files.exists(root), "Expected guarded path to exist: ${projectRoot.relativize(root).invariantSeparatorsPathString}")
                Files.walk(root).use { paths ->
                    paths
                        .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                        .toList()
                }
            }
            .sortedBy { projectRoot.relativize(it).invariantSeparatorsPathString }

        assertFalse(guardedFiles.isEmpty(), "Expected at least one guarded UI entrypoint file")

        val violations = mutableListOf<String>()
        guardedFiles.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trimStart()
                val isComment = trimmed.startsWith("//") || trimmed.startsWith("*")
                if (isComment) return@forEachIndexed
                if (forbiddenPatterns.any { it.containsMatchIn(line) }) {
                    val relativePath = projectRoot.relativize(file).invariantSeparatorsPathString
                    violations.add("$relativePath:${index + 1} -> ${line.trim()}")
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "UI project-action bypass detected in guarded entry points.\n" +
                violations.joinToString("\n")
        )
    }
}
