package com.pafoid.skate.game

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.engine.ui.GameSettings
import com.pafoid.skate.engine.ui.GameSettingsStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** GPL-19: backend selection and the OpenGL fallback, with fake windows (no GPU). */
class GameGraphicsTest {

    @TempDir
    lateinit var dir: File

    private val logger = LoggerService().apply { consoleSink = { _, _ -> } }
    private val opened = mutableListOf<GraphicsBackend>()

    private fun store(json: String? = null): GameSettingsStore {
        val file = GameSettings.fileIn(dir)
        json?.let(file::writeText)
        return GameSettingsStore(file, logger)
    }

    /** Opens a fake window; Vulkan throws [vulkanError] when set. */
    private fun opener(vulkanError: Throwable? = null): (GraphicsBackend) -> String = { backend ->
        opened += backend
        if (backend == GraphicsBackend.VULKAN && vulkanError != null) throw vulkanError
        "window:$backend"
    }

    @Test
    fun GameGraphics_SettingsFileOverridesTheProjectAndThePropertyOverridesBoth() {
        assertEquals(GraphicsBackend.VULKAN, GameGraphics.requested(GraphicsBackend.VULKAN, null, property = null))
        assertEquals(GraphicsBackend.VULKAN, GameGraphics.requested(GraphicsBackend.VULKAN, store(), property = null))
        assertEquals(GraphicsBackend.OPENGL, GameGraphics.requested(GraphicsBackend.VULKAN, store("""{"graphicsBackend":"OPENGL"}"""), property = null))
        assertEquals(GraphicsBackend.VULKAN, GameGraphics.requested(GraphicsBackend.OPENGL, store("""{"graphicsBackend":"OPENGL"}"""), property = "vulkan"))
    }

    @Test
    fun GameGraphics_OpenGlOpensOneOpenGlWindowAndLogsNothing() {
        val started = GameGraphics.start(GraphicsBackend.OPENGL, logger, store(), opener())

        assertEquals("window:OPENGL", started.window)
        assertEquals(listOf(GraphicsBackend.OPENGL), opened)
        assertFalse(started.fellBack)
        assertTrue(logger.logs.isEmpty())
    }

    @Test
    fun GameGraphics_VulkanStartsAndSaysWhatItDrawsSoFar() {
        val settings = store("""{"graphicsBackend":"VULKAN","graphicsNote":"VULKAN could not start: old driver."}""")

        val started = GameGraphics.start(GraphicsBackend.VULKAN, logger, settings, opener())

        assertEquals("window:VULKAN", started.window)
        assertEquals(GraphicsBackend.VULKAN, started.backend)
        val line = logger.logs.single()
        assertEquals(LoggerService.LogLevel.WARN, line.level)
        assertTrue(line.message.contains("VULKAN") && line.message.contains("QA-16"), line.message)
        assertNull(settings.settings.graphicsNote, "a successful start clears the old fallback note")
    }

    @Test
    fun GameGraphics_VulkanFailureFallsBackToOpenGlWithOneLogLineAndASettingsNote() {
        val settings = store("""{"locale":"fr","graphicsBackend":"VULKAN"}""")

        val started = GameGraphics.start(GraphicsBackend.VULKAN, logger, settings,
            opener(IllegalStateException("No Vulkan loader or driver (glfwVulkanSupported is false)")))

        assertEquals("window:OPENGL", started.window)
        assertEquals(listOf(GraphicsBackend.VULKAN, GraphicsBackend.OPENGL), opened)
        assertTrue(started.fellBack)
        assertEquals(GraphicsBackend.VULKAN, started.requested)
        val line = logger.logs.single()
        assertEquals(LoggerService.LogLevel.ERROR, line.level)
        assertTrue(line.message.contains("glfwVulkanSupported") && line.message.contains("falling back to OPENGL"), line.message)

        // The note is saved; the player's choice and locale are kept (Vulkan is tried again next launch).
        val saved = GameSettingsStore(GameSettings.fileIn(dir), logger).settings
        assertTrue(saved.graphicsNote!!.contains("ran on OPENGL"), saved.graphicsNote)
        assertEquals(GraphicsBackend.VULKAN, saved.graphicsBackend)
        assertEquals("fr", saved.locale)
    }

    @Test
    fun GameGraphics_NativeErrorsDuringVulkanInitAlsoFallBack() {
        val started = GameGraphics.start(GraphicsBackend.VULKAN, logger, null, opener(UnsatisfiedLinkError("vulkan-1.dll")))
        assertEquals(GraphicsBackend.OPENGL, started.backend)
        assertTrue(logger.logs.single().message.contains("vulkan-1.dll"))
    }

    @Test
    fun GameGraphics_OpenGlFailureIsNotSwallowed() {
        val broken: (GraphicsBackend) -> String = { throw IllegalStateException("Unable to create the GLFW window.") }
        assertThrows(IllegalStateException::class.java) { GameGraphics.start(GraphicsBackend.OPENGL, logger, null, broken) }
        assertThrows(IllegalStateException::class.java) { GameGraphics.start(GraphicsBackend.VULKAN, logger, null, broken) }
    }
}
