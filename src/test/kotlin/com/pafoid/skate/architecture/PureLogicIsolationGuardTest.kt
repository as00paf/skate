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
            "engine/input/InputContextStack.kt",
            "engine/input/InputHistory.kt",
            "engine/input/UiNavigationActions.kt",
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
    fun PureLogic_RenderGraphWindowLogicHasNoImGui() {
        // EDT-54 §4: layout, view and timing stats are tested without ImGui; only RenderGraphWindow.kt draws.
        val files = listOf(
            "editor/ui/windows/rendergraph/RenderGraphLayout.kt",
            "editor/ui/windows/rendergraph/RenderGraphView.kt",
            "editor/ui/windows/rendergraph/PassTimingStats.kt",
        )
        val forbidden = listOf("import org.lwjgl.", "import imgui.", "import com.pafoid.skate.editor.imgui.")
        val violations = files.flatMap { f ->
            importsOf(f).filter { line -> forbidden.any { line.startsWith(it) } }.map { "$f -> $it" }
        }
        assertTrue(violations.isEmpty(), "Render Graph window logic must stay testable without ImGui: $violations")
    }

    @Test
    fun PureLogic_NetworkingAndShaderCoreStayPlatformFree() {
        val netFiles = sourceRoot.resolve("engine/net").let { dir ->
            Files.list(dir).use { it.filter { f -> f.fileName.toString().endsWith(".kt") }.toList() }
        }.map { sourceRoot.relativize(it).invariantSeparatorsPathString }
        val files = netFiles + listOf(
            "engine/render/shader/ShaderPreprocessor.kt",
            "engine/render/shader/ShaderVariantCache.kt",
            "engine/gameplay/ik/FootIk.kt",
            "engine/input/TextInputBuffer.kt",
            "engine/assets/PrefabStore.kt",
        )
        val forbidden = listOf("import org.lwjgl.", "import com.pafoid.skate.editor.", "import imgui.")
        val violations = files.flatMap { f ->
            importsOf(f).filter { line -> forbidden.any { line.startsWith(it) } }.map { "$f -> $it" }
        }
        assertTrue(netFiles.size >= 5, "engine/net unexpectedly empty: $netFiles")
        assertTrue(violations.isEmpty(), "Pure-logic files must not import GL/AL/editor: $violations")
    }

    private fun ktFilesIn(relativeDir: String): List<String> =
        Files.list(sourceRoot.resolve(relativeDir)).use { it.filter { f -> f.fileName.toString().endsWith(".kt") }.toList() }
            .map { sourceRoot.relativize(it).invariantSeparatorsPathString }

    @Test
    fun PureLogic_ShadowParticleAndCameraMathsStayPlatformFree() {
        // Whole directories: a new GL-bound file in shadow/ or particles/ belongs in renderer/passes instead.
        val shadowFiles = ktFilesIn("engine/render/shadow")
        val particleFiles = ktFilesIn("engine/render/particles")
        val files = shadowFiles + particleFiles + listOf(
            "engine/render/lighting/LightData.kt",
            "engine/render/lighting/LightCulling.kt",
            "engine/utils/CameraMotion.kt",
            "engine/input/ViewportPointer.kt",
        )
        // Body text too, not just imports: a fully qualified org.lwjgl.* call would bypass an import-only check.
        val forbidden = listOf("org.lwjgl.", "com.pafoid.skate.editor.", "imgui.")
        val violations = files.flatMap { f ->
            importsOf(f)
            sourceRoot.resolve(f).readText().lineSequence().map { it.trim() }
                .filter { line -> !line.startsWith("//") && !line.startsWith("*") && forbidden.any { line.contains(it) } }
                .map { "$f -> $it" }
                .toList()
        }
        assertTrue(shadowFiles.size >= 4, "engine/render/shadow unexpectedly small: $shadowFiles")
        assertTrue(
            particleFiles.containsAll(listOf("engine/render/particles/ParticleSimulator.kt", "engine/render/particles/ParticlePool.kt")),
            "Particle simulation moved, update the guard: $particleFiles",
        )
        assertTrue(violations.isEmpty(), "Shadow/particle/camera maths must stay unit-testable without GL/editor: $violations")
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
