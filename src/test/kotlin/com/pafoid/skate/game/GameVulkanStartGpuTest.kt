package com.pafoid.skate.game

import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.Window
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.vulkan.RhiBackend
import com.pafoid.skate.engine.render.rhi.vulkan.VulkanDevice
import com.pafoid.skate.testing.GpuTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwInit

/**
 * GPL-19 on a real GPU (opt-in like the other Vulkan tests: `SKATE_RHI=vulkan`, and `SKATE_VK_VALIDATION=0` until the
 * LunarG SDK is installed). The game's own start path: [GameGraphics] opens a Vulkan [Window], the engine starts on
 * its device and plays a new project's MainScene with every render pass, presenting each frame like GameMain.
 */
@GpuTest
class GameVulkanStartGpuTest {

    @Test
    fun GameStart_VulkanWindowRunsTheDefaultSceneAndPresents() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")

        val logger = LoggerService().apply { consoleSink = { _, _ -> } }
        val started = GameGraphics.start(GraphicsBackend.VULKAN, logger, null) { backend -> Window(1280, 720, "GPL-19", backend = backend) }
        assumeTrue(!started.fellBack, "no Vulkan driver: ${logger.logs.map { it.message }}")
        val window = started.window
        assertTrue(window.device is VulkanDevice)
        assertTrue(logger.logs.single().message.contains("QA-16"))

        val engine = Engine()
        val dir = kotlin.io.path.createTempDirectory("gpl19").toFile()
        try {
            engine.start(window.glfwWindow, window.device)
            assertTrue(ProjectManager(engine).createProject("Gpl19", dir).isSuccess)
            engine.sceneManager.switchScene(engine.sceneManager.openScenes.first { it.name != "FeatureShowcase" })
            engine.runtimePlaying = true
            val failed = engine.renderer.renderGraph.getAllPasses().filter { !it.isEnabled }.map { it.name }
            repeat(60) {
                engine.update(1f / 60f)
                window.device.commandList().blit(engine.renderer.frameBuffer.target, null, dstRect = IntRect(0, 0, window.width, window.height), filter = Filter.LINEAR)
                window.device.endFrame()
            }
            // The bar here is "no ERROR logs"; pixel parity of every pass is QA-16 (FeatureShowcaseVulkanParityGpuTest).
            assertEquals(emptyList<String>(), engine.logger.logs.filter { it.level == LoggerService.LogLevel.ERROR }.map { it.message }, "errors (disabled passes: $failed)")
        } finally {
            runCatching { engine.destroy() }
            runCatching { window.device.close() }
            glfwDestroyWindow(window.glfwWindow)
            runCatching { dir.deleteRecursively() }
        }
    }
}
