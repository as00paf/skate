package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class EngineGameLayeringGuardTest {

    // Known legacy dependencies (ECS-06 follow-ups); this set must only shrink.
    private val legacy = setOf(
        "PrefabsGenerator.kt", "Animator.kt", "PlayerController.kt", "PlayerMotionSystem.kt"
    )

    @Test
    fun EngineLayering_EventsDoNotImportGame() {
        val root = Paths.get(System.getProperty("user.dir")).resolve("src/main/kotlin/com/pafoid/skate/engine/events")
        val offenders = Files.walk(root).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.toList().filter { f ->
                Files.readAllLines(f).any { it.trimStart().startsWith("import com.pafoid.skate.game.") }
            }.map { it.fileName.toString() }
        }
        assertTrue(offenders.isEmpty(), "engine/events must not import game: $offenders")
    }

    @Test
    fun EngineLayering_NoNewEngineToGameImports() {
        val root = Paths.get(System.getProperty("user.dir")).resolve("src/main/kotlin/com/pafoid/skate/engine")
        val offenders = Files.walk(root).use { paths ->
            paths.filter { it.toString().endsWith(".kt") }.toList().filter { f ->
                f.fileName.toString() !in legacy &&
                    Files.readAllLines(f).any { it.trimStart().startsWith("import com.pafoid.skate.game.") }
            }.map { it.fileName.toString() }
        }
        assertTrue(offenders.isEmpty(), "engine must not import game: $offenders")
    }
}
