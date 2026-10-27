package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class PureLogicIsolationGuardTest {

    private val sourceRoot: Path =
        Paths.get(System.getProperty("user.dir")).toAbsolutePath().resolve("src/main/kotlin/com/pafoid/skate")

    private fun importsOf(relative: String): List<String> {
        val file = sourceRoot.resolve(relative)
        assertTrue(Files.exists(file), "Pure-logic file moved or deleted, update the guard: $relative")
        return file.readText().lineSequence().map { it.trim() }.filter { it.startsWith("import ") }.toList()
    }

    @Test
    fun PureLogic_InputAudioEcsCoreFilesDoNotImportPlatformOrEditor() {
        val pureFiles = listOf(
            "engine/input/ActionMap.kt",
            "engine/input/DeadZone.kt",
            "engine/input/GamepadRegistry.kt",
            "engine/input/InputReader.kt",
            "engine/input/ProviderRawInput.kt",
            "engine/input/Rumble.kt",
            "engine/audio/ClipPicker.kt",
            "engine/audio/Mixer.kt",
            "engine/audio/SpatialAudio.kt",
            "engine/audio/StreamPolicy.kt",
            "engine/audio/VoiceManager.kt",
            "engine/ecs/ComponentQuery.kt",
            "engine/ecs/systems/SystemOrdering.kt",
        )
        val forbidden = listOf("import org.lwjgl.", "import com.pafoid.skate.editor.", "import imgui.")
        val violations = pureFiles.flatMap { f ->
            importsOf(f).filter { line -> forbidden.any { line.startsWith(it) } }.map { "$f -> $it" }
        }
        assertTrue(violations.isEmpty(), "Pure-logic files must stay unit-testable without GLFW/GL/AL/editor: $violations")
    }

    @Test
    fun EngineLayering_EngineDoesNotImportEditorExceptExportTools() {
        val exceptions = setOf("engine/utils/AssetsPacker.kt", "engine/utils/ProjectExporter.kt")
        val violators = Files.walk(sourceRoot.resolve("engine")).use { paths ->
            paths
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .filter { it.readText().contains("import com.pafoid.skate.editor.") }
                .map { sourceRoot.relativize(it).invariantSeparatorsPathString }
                .toList()
                .toSet()
        }
        assertTrue((violators - exceptions).isEmpty(), "New engine -> editor imports: ${violators - exceptions}")
        assertTrue((exceptions - violators).isEmpty(), "Exceptions no longer import editor, remove them: ${exceptions - violators}")
    }
}
