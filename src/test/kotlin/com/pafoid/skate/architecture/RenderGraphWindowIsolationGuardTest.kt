package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-54 §3: the Render Graph window reads the graph only through `engine.render.graph` (RenderGraphInfo,
 * PassTimings, setEnabled): no pass classes, no RHI, so renderer internals can change without touching the editor.
 */
class RenderGraphWindowIsolationGuardTest {
    private val dir = File("src/main/kotlin/com/pafoid/skate/editor/ui/windows/rendergraph")
    private val forbidden = listOf("com.pafoid.skate.engine.render.renderer.passes", "com.pafoid.skate.engine.render.rhi")

    @Test
    fun RenderGraphWindow_ImportsNoPassesOrRhi() {
        val files = dir.listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue(files.any { it.name == "RenderGraphWindow.kt" }, "window moved: update the guard")
        val violations = files.flatMap { f ->
            f.readLines().filter { line -> forbidden.any { line.contains(it) } }.map { "${f.name}: ${it.trim()}" }
        }
        assertTrue(violations.isEmpty(), "Use engine.render.graph (info/readTimings/setEnabled) instead:\n${violations.joinToString("\n")}")
    }
}
