package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Ratcheting guards for rules that other domains are still fixing.
 *
 * Each rule has an exception list of files that violate it today. The guard fails when a NEW file violates the rule
 * and also when a listed file no longer violates it, so the list can only shrink. When fixing a file, remove it here.
 */
class PlatformApiGuardTest {

    private val sourceRoot: Path =
        Paths.get(System.getProperty("user.dir")).toAbsolutePath().resolve("src/main/kotlin/com/pafoid/skate")

    private fun mainFiles(): List<Pair<String, String>> = Files.walk(sourceRoot).use { paths ->
        paths
            .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
            .map { sourceRoot.relativize(it).invariantSeparatorsPathString to it.readText() }
            .toList()
    }

    private fun stripComments(text: String): String =
        text.lineSequence()
            .filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
            .joinToString("\n")

    private fun assertRatchet(rule: String, violators: Set<String>, exceptions: Set<String>) {
        val added = violators - exceptions
        val stale = exceptions - violators
        assertTrue(added.isEmpty(), "$rule: new violations (fix them, do not add exceptions): $added")
        assertTrue(stale.isEmpty(), "$rule: exceptions no longer violate, remove them from the list: $stale")
    }

    @Test
    fun PlatformApi_OpenGlOnlyInRenderAndAssetLayers() {
        val allowedPrefixes = listOf("engine/render/", "engine/assets/", "engine/core/Window.kt")
        val exceptions = setOf(
            "editor/systems/ThumbnailCache.kt",
            "engine/utils/ScreenshotUtils.kt",
            "engine/utils/testing/FrameCapture.kt",
            "game/GameMain.kt",
        )
        val violators = mainFiles()
            .filter { (path, text) -> allowedPrefixes.none { path.startsWith(it) } && text.contains("org.lwjgl.opengl") }
            .map { it.first }
            .toSet()
        assertRatchet("OpenGL outside render/assets", violators, exceptions)
    }

    @Test
    fun PlatformApi_OpenAlOnlyInAudioAndAssetLayers() {
        val allowedPrefixes = listOf("engine/audio/", "engine/assets/")
        val exceptions = setOf("engine/ecs/systems/AudioSystem.kt")
        val violators = mainFiles()
            .filter { (path, text) -> allowedPrefixes.none { path.startsWith(it) } && text.contains("org.lwjgl.openal") }
            .map { it.first }
            .toSet()
        assertRatchet("OpenAL outside audio/assets", violators, exceptions)
    }

    @Test
    fun Logging_NoDirectConsoleOutputOutsideLoggerService() {
        val exceptions = setOf(
            "editor/imgui/components/Component.kt",
            "editor/search/history/SearchHistory.kt",
            "editor/search/SearchEngine.kt",
            "engine/core/LoggerService.kt",
            "engine/core/Window.kt",
            "engine/EngineExtensions.kt",
            "engine/physics3d/native/NativeLibraryLoader.kt",
            "game/GameMain.kt",
        )
        val console = Regex("""\bprintln\(|\bprint\(|printStackTrace\(|System\.(out|err)\b""")
        val violators = mainFiles()
            .filter { (_, text) -> console.containsMatchIn(stripComments(text)) }
            .map { it.first }
            .toSet()
        assertRatchet("Direct console output (use LoggerService)", violators, exceptions)
    }

    @Test
    fun ResourceLifetime_FilesThatAllocateGlOrAlObjectsAlsoDeleteThem() {
        val exceptions = setOf(
            "engine/assets/data/CubeMap.kt",
            "engine/render/renderer/DebugRenderer.kt",
            "engine/render/renderer/PickingRenderer.kt",
        )
        val create = Regex("""glGen(Buffers|Textures|Framebuffers|VertexArrays|Renderbuffers)|glCreate(Program|Shader)|alGen(Buffers|Sources)""")
        val delete = Regex("""glDelete|alDelete""")
        val violators = mainFiles()
            .filter { (_, text) -> create.containsMatchIn(text) && !delete.containsMatchIn(text) }
            .map { it.first }
            .toSet()
        assertRatchet("GL/AL allocation without delete", violators, exceptions)
    }
}
