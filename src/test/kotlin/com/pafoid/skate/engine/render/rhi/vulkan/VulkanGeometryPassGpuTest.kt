package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_CLIENT_API
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_NO_API
import org.lwjgl.glfw.GLFW.GLFW_SAMPLES
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDefaultWindowHints
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * REN-13: renders the default MainScene's geometry pass with the OpenGL backend and with the Vulkan spike, reads both
 * frames back the same way (GL row order) and reports how much they differ. Opt-in (`SKATE_RHI=vulkan`) and GPU-only
 * (@GpuTest, skipped on CI). Writes `build/ren13/{gl,vulkan,diff}.png` and `build/ren13/report.txt`.
 */
@GpuTest
class VulkanGeometryPassGpuTest {
    private val width = 1280
    private val height = 720
    private val out = File("build/ren13").apply { mkdirs() }

    private fun render(
        createDevice: () -> RhiDevice,
        gl: Boolean,
        sceneName: String? = null,
        passes: (String) -> Boolean = { it == "GeometryPass" },
        afterFrames: (Engine) -> Unit = {},
    ): ByteArray {
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        if (gl) {
            glfwWindowHint(GLFW_SAMPLES, 4)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        } else {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        }
        val window = glfwCreateWindow(64, 64, "REN-13", 0, 0)
        check(window != 0L) { "no window" }
        if (gl) glfwMakeContextCurrent(window)
        val device = createDevice()
        val engine = Engine()
        try {
            engine.start(window, device)
            engine.resizeFrameBuffer(width, height)
            val dir = kotlin.io.path.createTempDirectory("skate-ren13").toFile()
            check(ProjectManager(engine).createProject("Ren13", dir).isSuccess)
            val main = engine.sceneManager.openScenes.first { if (sceneName == null) it.name != "FeatureShowcase" else it.name == sceneName }
            engine.sceneManager.switchScene(main)
            engine.cameraManager.transform.translation.set(6f, 4f, 8f)
            engine.cameraManager.transform.lookAt(Vector3f(0f, 0.8f, 0f))
            // Only the geometry pass: no shadows, post-processing, debug, UI or picking on either backend
            engine.renderer.renderGraph.getAllPasses().forEach { it.isEnabled = passes(it.name) }
            repeat(5) { engine.update(1f / 60f) }
            afterFrames(engine)

            val pixels = ByteBuffer.allocateDirect(width * height * 4)
            device.readPixels(engine.renderer.frameBuffer.target, 0, IntRect(0, 0, width, height), PixelFormat.RGBA8, pixels)
            runCatching { dir.deleteRecursively() }
            return ByteArray(pixels.capacity()).also { pixels.get(it) }
        } finally {
            runCatching { engine.destroy() }
            runCatching { device.close() }
            glfwDestroyWindow(window)
        }
    }

    @Test
    fun GeometryPass_MainScene_VulkanMatchesOpenGl() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan spike is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        val vkAvailable = runCatching { VulkanDevice.create().close() }.isSuccess
        assumeTrue(vkAvailable, "no Vulkan driver")

        val gl = render({ OpenGlDevice.create() }, gl = true)
        val vk = render({ VulkanDevice.create() }, gl = false)

        val diff = ByteArray(gl.size)
        var sum = 0L
        var maxDiff = 0
        var within2 = 0
        var within8 = 0
        for (p in 0 until width * height) {
            var m = 0
            for (c in 0 until 3) {
                val d = abs((gl[p * 4 + c].toInt() and 0xFF) - (vk[p * 4 + c].toInt() and 0xFF))
                sum += d
                m = maxOf(m, d)
            }
            maxDiff = maxOf(maxDiff, m)
            if (m <= 2) within2++
            if (m <= 8) within8++
            val v = minOf(255, m * 8).toByte()
            diff[p * 4] = v; diff[p * 4 + 1] = v; diff[p * 4 + 2] = v; diff[p * 4 + 3] = -1
        }
        val pixels = width * height
        val report = "REN-13 geometry pass, MainScene ${width}x$height, Vulkan vs OpenGL\n" +
            "mean |diff| per channel: %.3f\n".format(sum.toDouble() / (pixels * 3)) +
            "pixels with max channel diff <= 2: %.2f%%\n".format(100.0 * within2 / pixels) +
            "pixels with max channel diff <= 8: %.2f%%\n".format(100.0 * within8 / pixels) +
            "max channel diff: $maxDiff\n" +
            "distinct Vulkan colours: ${(0 until pixels).map { vk[it * 4].toInt() * 65536 + vk[it * 4 + 1] * 256 + vk[it * 4 + 2] }.distinct().size}\n"
        println(report)
        File(out, "report.txt").writeText(report)
        png("gl", gl); png("vulkan", vk); png("diff", diff)

        val vkColours = (0 until pixels step 97).map { vk[it * 4].toInt() * 65536 + vk[it * 4 + 1] * 256 + vk[it * 4 + 2] }.distinct().size
        assertTrue(vkColours > 50, "the Vulkan frame has content (not one flat colour)")
        assertTrue(within8 > pixels * 0.9, "at least 90% of pixels within 8/255 of the OpenGL frame:\n$report")
    }

    /**
     * VK-02: every resource the passes create works on Vulkan. All passes but post-processing (its blit is VK-05) run
     * on both showcase scenes, including shadow maps (layered depth), picking (RGB32F readback), particles, debug lines
     * and the runtime UI (dynamic buffers). Parity of those passes is VK-03..06: the difference is reported, not asserted.
     */
    @Test
    fun AllPassesButPostProcessing_RunOnVulkan() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan spike is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(runCatching { VulkanDevice.create().close() }.isSuccess, "no Vulkan driver")
        val report = StringBuilder()
        for (scene in listOf("MainScene", "FeatureShowcase")) {
            var picked = -1
            val passes = { name: String -> name != "PostProcessPass" }
            val gl = render({ OpenGlDevice.create() }, gl = true, sceneName = scene.takeIf { it != "MainScene" }, passes = passes)
            val vk = render({ VulkanDevice.create() }, gl = false, sceneName = scene.takeIf { it != "MainScene" }, passes = passes) { engine ->
                picked = engine.renderer.readPixel(0.5f, 0.5f)
            }
            var within8 = 0
            for (p in 0 until width * height) {
                val m = (0 until 3).maxOf { c -> abs((gl[p * 4 + c].toInt() and 0xFF) - (vk[p * 4 + c].toInt() and 0xFF)) }
                if (m <= 8) within8++
            }
            report.append("%s: %.2f%% of pixels within 8/255 of OpenGL, picking id at centre %d\n".format(scene, 100.0 * within8 / (width * height), picked))
            val colours = (0 until width * height step 97).map { vk[it * 4].toInt() * 65536 + vk[it * 4 + 1] * 256 + vk[it * 4 + 2] }.distinct().size
            assertTrue(colours > 50, "$scene: the Vulkan frame has content")
        }
        println(report)
        File(out, "all-passes.txt").writeText(report.toString())
    }

    /** Saves GL-order rows (bottom first) as an upright PNG. */
    private fun png(name: String, rgba: ByteArray) {
        val flipped = ByteBuffer.allocateDirect(rgba.size)
        for (row in 0 until height) flipped.put((height - 1 - row) * width * 4, rgba, row * width * 4, width * 4)
        stbi_write_png(File(out, "$name.png").absolutePath, width, height, 4, flipped, width * 4)
    }
}
