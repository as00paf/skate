package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.render.rhi.ClearValues
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_CLIENT_API
import org.lwjgl.glfw.GLFW.GLFW_NO_API
import org.lwjgl.glfw.GLFW.GLFW_RESIZABLE
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDefaultWindowHints
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwGetFramebufferSize
import org.lwjgl.glfw.GLFW.glfwIconifyWindow
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwPollEvents
import org.lwjgl.glfw.GLFW.glfwRestoreWindow
import org.lwjgl.glfw.GLFW.glfwSetWindowSize
import org.lwjgl.glfw.GLFW.glfwWaitEventsTimeout
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import java.nio.ByteBuffer

/**
 * VK-01: a GLFW no-API window presents frames with the Vulkan backend (clears, then the REN-13 geometry pass blitted
 * to the back buffer like GameMain), survives resize, minimize and a vsync switch, with the Khronos validation layer
 * on and zero validation errors. Opt-in (`SKATE_RHI=vulkan`), GPU-only (@GpuTest). Needs the layer installed (SDK or
 * `VK_ADD_LAYER_PATH`, see docs/testing/VK-01.md); `SKATE_VK_VALIDATION=0` runs without it.
 */
@GpuTest
class VulkanPresentGpuTest {
    private val requireValidation = VkCore.validationRequested(env = System.getenv("SKATE_VK_VALIDATION") ?: "1")

    @BeforeEach
    fun requireVulkan() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(glfwVulkanSupported(), "no Vulkan loader")
    }

    private fun withDevice(width: Int = 640, height: Int = 360, vsync: Boolean = true, block: (Long, VulkanDevice) -> Unit) {
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        val window = glfwCreateWindow(width, height, "VK-01", 0, 0)
        check(window != 0L) { "no window" }
        val errorsBefore = VulkanDevice.validationErrors
        try {
            val device = VulkanDevice.create(window, vsync = vsync, validation = requireValidation)
            try {
                if (requireValidation) {
                    assertTrue(device.validation, "VK_LAYER_KHRONOS_validation is not installed: see docs/testing/VK-01.md (or set SKATE_VK_VALIDATION=0)")
                }
                block(window, device)
            } finally {
                device.close()
            }
        } finally {
            glfwDestroyWindow(window)
        }
        assertEquals(errorsBefore, VulkanDevice.validationErrors, "Vulkan validation errors (see the skate.rhi.vulkan log)")
    }

    private fun framebufferSize(window: Long): IntArray {
        val w = IntArray(1)
        val h = IntArray(1)
        glfwGetFramebufferSize(window, w, h)
        return intArrayOf(w[0], h[0])
    }

    /** Clears the back buffer to [rgb], reads it back before presenting and returns the centre pixel (RGBA). */
    private fun clearFrame(window: Long, device: VulkanDevice, rgb: FloatArray, read: Boolean = true): IntArray? {
        glfwPollEvents()
        device.beginFrame()
        val fb = framebufferSize(window)
        val cmd = device.commandList()
        cmd.beginPass(null, clear = ClearValues(floatArrayOf(rgb[0], rgb[1], rgb[2], 1f), null), viewport = IntRect(0, 0, fb[0], fb[1]))
        cmd.endPass()
        var pixel: IntArray? = null
        if (read && device.backbufferWidth > 0) {
            val out = ByteBuffer.allocateDirect(4)
            device.readPixels(null, 0, IntRect(device.backbufferWidth / 2, device.backbufferHeight / 2, 1, 1), PixelFormat.RGBA8, out)
            pixel = IntArray(4) { out.get(it).toInt() and 0xFF }
        }
        device.endFrame()
        return pixel
    }

    private fun assertColor(expected: IntArray, actual: IntArray?) {
        checkNotNull(actual) { "no back buffer" }
        for (c in 0 until 3) assertTrue(kotlin.math.abs(expected[c] - actual[c]) <= 1, "expected ${expected.toList()} got ${actual.toList()}")
    }

    @Test
    fun Present_ClearFrames_ReachTheBackBuffer() = withDevice { window, device ->
        assertTrue(device.presents)
        repeat(10) { i ->
            val red = if (i % 2 == 0) 1f else 0f
            assertColor(intArrayOf(if (i % 2 == 0) 255 else 0, 128, 0), clearFrame(window, device, floatArrayOf(red, 128 / 255f, 0f)))
        }
        assertEquals(10L, device.frameCount)
        // Frames in flight: many more frames than slots, no reads (no GPU waits) in between
        repeat(60) { clearFrame(window, device, floatArrayOf(0f, 0f, it / 60f), read = false) }
        assertEquals(70L, device.frameCount)
    }

    @Test
    fun Present_Resize_RecreatesTheSwapchain() = withDevice { window, device ->
        clearFrame(window, device, floatArrayOf(1f, 0f, 0f))
        val generation = device.swapchainGeneration
        for ((w, h) in listOf(800 to 500, 320 to 200, 1024 to 640)) {
            glfwSetWindowSize(window, w, h)
            // Let the window system apply the size
            repeat(20) { if (framebufferSize(window)[0] != w) glfwWaitEventsTimeout(0.05) }
            val pixel = clearFrame(window, device, floatArrayOf(0f, 1f, 0f))
            val fb = framebufferSize(window)
            assertEquals(fb[0], device.backbufferWidth)
            assertEquals(fb[1], device.backbufferHeight)
            assertColor(intArrayOf(0, 255, 0), pixel)
        }
        assertTrue(device.swapchainGeneration > generation, "resizes rebuilt the swapchain")
    }

    @Test
    fun Present_Minimize_SkipsTheBackBufferThenRecovers() = withDevice { window, device ->
        clearFrame(window, device, floatArrayOf(1f, 0f, 0f))
        glfwIconifyWindow(window)
        repeat(20) { if (framebufferSize(window)[0] != 0) glfwWaitEventsTimeout(0.05) }
        val minimized = SwapchainPolicy.isMinimized(framebufferSize(window)[0], framebufferSize(window)[1])
        // Minimized (or not, on window systems that keep the size): frames must not fail either way
        repeat(5) { clearFrame(window, device, floatArrayOf(0f, 0f, 1f), read = !minimized) }
        glfwRestoreWindow(window)
        repeat(20) { if (framebufferSize(window)[0] == 0) glfwWaitEventsTimeout(0.05) }
        assertColor(intArrayOf(0, 0, 255), clearFrame(window, device, floatArrayOf(0f, 0f, 1f)))
    }

    @Test
    fun Present_VsyncOff_SwitchesPresentMode() = withDevice(vsync = false) { window, device ->
        repeat(20) { clearFrame(window, device, floatArrayOf(0.5f, 0.5f, 0.5f), read = false) }
        val generation = device.swapchainGeneration
        device.vsync = true
        assertColor(intArrayOf(128, 128, 128), clearFrame(window, device, floatArrayOf(0.5f, 0.5f, 0.5f)))
        assertTrue(device.swapchainGeneration > generation, "the vsync switch rebuilt the swapchain")
    }

    @Test
    fun Present_GeometryAndPost_BlittedToTheBackBuffer_MatchesTheOffscreenFrame() = withDevice(1280, 720) { window, device ->
        val engine = Engine()
        val dir = kotlin.io.path.createTempDirectory("skate-vk01").toFile()
        try {
            engine.start(window, device)
            fun resize() = framebufferSize(window).also { engine.resizeFrameBuffer(it[0], it[1]) }
            resize()
            check(ProjectManager(engine).createProject("Vk01", dir).isSuccess)
            val main = engine.sceneManager.openScenes.first { it.name != "FeatureShowcase" }
            engine.sceneManager.switchScene(main)
            engine.cameraManager.transform.translation.set(6f, 4f, 8f)
            engine.cameraManager.transform.lookAt(Vector3f(0f, 0.8f, 0f))
            // Geometry plus post-processing (default bloom + FXAA: ping-pong targets and the blit back, VK-05)
            engine.renderer.renderGraph.getAllPasses().forEach { it.isEnabled = it.name == "GeometryPass" || it.name == "PostProcessPass" }

            /** One game frame: update (renders the scene target), blit to the back buffer, present. */
            fun frame(compare: Boolean) {
                glfwPollEvents()
                engine.update(1f / 60f)
                val target = engine.renderer.frameBuffer.target
                val w = target.width
                val h = target.height
                device.commandList().blit(target, null, dstRect = IntRect(0, 0, device.backbufferWidth.coerceAtLeast(1), device.backbufferHeight.coerceAtLeast(1)), filter = Filter.LINEAR)
                if (compare) {
                    assertEquals(w, device.backbufferWidth, "scene target and back buffer have the same size")
                    val scene = ByteBuffer.allocateDirect(w * h * 4)
                    val back = ByteBuffer.allocateDirect(w * h * 4)
                    device.readPixels(target, 0, IntRect(0, 0, w, h), PixelFormat.RGBA8, scene)
                    device.readPixels(null, 0, IntRect(0, 0, w, h), PixelFormat.RGBA8, back)
                    var maxDiff = 0
                    val colours = HashSet<Int>()
                    for (p in 0 until w * h) {
                        for (c in 0 until 3) maxDiff = maxOf(maxDiff, kotlin.math.abs((scene.get(p * 4 + c).toInt() and 0xFF) - (back.get(p * 4 + c).toInt() and 0xFF)))
                        if (p % 97 == 0) colours += (back.getInt(p * 4) and 0xFFFFFF)
                    }
                    assertTrue(colours.size > 50, "the presented frame has content (${colours.size} colours)")
                    assertEquals(0, maxDiff, "the back buffer holds exactly the scene frame")
                    // Upright (VK-05 orientation): in GL row order the top rows are sky (blue), the bottom rows floor
                    fun rowBlueness(row: Int): Double = (0 until w step 7).sumOf { x ->
                        val i = (row * w + x) * 4
                        ((back.get(i + 2).toInt() and 0xFF) - (back.get(i).toInt() and 0xFF)).toDouble()
                    } / (w / 7)
                    assertTrue(rowBlueness(h - 2) > rowBlueness(1) + 20, "sky at the top, floor at the bottom: ${rowBlueness(h - 2)} vs ${rowBlueness(1)}")
                }
                device.endFrame()
            }

            repeat(5) { frame(compare = false) }
            frame(compare = true)
            glfwSetWindowSize(window, 960, 540)
            repeat(20) { if (framebufferSize(window)[0] != 960) glfwWaitEventsTimeout(0.05) }
            resize()
            repeat(3) { frame(compare = false) }
            frame(compare = true)
        } finally {
            runCatching { engine.destroy() }
            runCatching { dir.deleteRecursively() }
        }
    }
}
