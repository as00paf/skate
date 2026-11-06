package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraFocusTarget
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.RenderTraversal
import com.pafoid.skate.engine.render.renderer.passes.ShadowPass
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.render.shadow.ShadowQualityLevel
import com.pafoid.skate.engine.render.shadow.ShadowSettings
import com.pafoid.skate.game.samples.FeatureShowcase
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
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

/**
 * VK-04: cascaded sun shadows and spot/point shadow maps on Vulkan match OpenGL at every [ShadowQualityLevel], in the
 * FeatureShowcase Shadows area and on MainScene. Only the shadow and geometry passes run. Also checks the REN-22
 * shadow cache on Vulkan: a static frame skips its layers and stays identical, and moving a caster re-renders it.
 * Opt-in (`SKATE_RHI=vulkan`), GPU-only (@GpuTest). Writes `build/vk04/<view>-{gl,vulkan,diff}.png` and
 * `build/vk04/report.txt`; the tolerance is [TOLERANCE] (docs/testing/VK-04.md).
 */
@GpuTest
class VulkanShadowParityGpuTest {
    private val width = 1280
    private val height = 720
    private val out = File("build/vk04").apply { mkdirs() }
    private val initialQuality = ShadowSettings.quality

    @AfterEach
    fun restoreQuality() {
        ShadowSettings.quality = initialQuality
    }

    private class View(val name: String, val scene: String, val setup: (Engine) -> Unit)

    private val views: List<View> = listOf(
        View("showcase-shadows", SHOWCASE) { e -> e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "shadows" })) },
        View("showcase-spot", SHOWCASE) { e ->
            val target = e.objectPosition("[Shadows] SpotTarget")
            e.look(Vector3f(target).add(3.5f, 3f, 4f), target)
        },
        View("main-sun", MAIN) { e -> e.look(Vector3f(6f, 4f, 8f), Vector3f(0f, 0.8f, 0f)) },
    )

    private fun Engine.look(eye: Vector3f, target: Vector3f) {
        cameraManager.transform.translation.set(eye)
        cameraManager.transform.lookAt(target)
    }

    private fun Engine.focus(target: CameraFocusTarget) {
        cameraManager.transform.translation.set(target.position)
        cameraManager.transform.rotation.set(target.rotation)
    }

    private fun Engine.find(name: String): GameObject {
        val scene = checkNotNull(sceneManager.currentScene)
        var found: GameObject? = null
        RenderTraversal.forEachRendered(scene) { if (found == null && it.name == name) found = it }
        return checkNotNull(found) { "no $name in ${scene.name}" }
    }

    private fun Engine.objectPosition(name: String): Vector3f = find(name).getComponent<Transform>()!!.worldMatrix.getTranslation(Vector3f())

    private fun Engine.shadowPass(): ShadowPass = renderer.renderGraph.getAllPasses().filterIsInstance<ShadowPass>().single()

    private fun Engine.read(device: RhiDevice): ByteArray {
        val pixels = ByteBuffer.allocateDirect(width * height * 4)
        device.readPixels(renderer.frameBuffer.target, 0, IntRect(0, 0, width, height), PixelFormat.RGBA8, pixels)
        return ByteArray(pixels.capacity()).also { pixels.get(it) }
    }

    /** Frames of one backend: every view at every quality, plus the cache checks. */
    private class Run(val frames: Map<String, ByteArray>, val cacheIdentical: Boolean, val skippedWhenStatic: Int)

    private fun renderAll(createDevice: () -> RhiDevice, gl: Boolean): Run {
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        if (gl) {
            glfwWindowHint(GLFW_SAMPLES, 4)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        } else {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        }
        val window = glfwCreateWindow(64, 64, "VK-04", 0, 0)
        check(window != 0L) { "no window" }
        if (gl) glfwMakeContextCurrent(window)
        val device = createDevice()
        val engine = Engine()
        val dir = kotlin.io.path.createTempDirectory("skate-vk04").toFile()
        val frames = LinkedHashMap<String, ByteArray>()
        try {
            engine.start(window, device)
            engine.resizeFrameBuffer(width, height)
            check(ProjectManager(engine).createProject("Vk04", dir).isSuccess)
            engine.renderer.renderGraph.getAllPasses().forEach { it.isEnabled = it.name == "GeometryPass" || it.name == "ShadowPass" }
            for (quality in ShadowQualityLevel.entries) {
                ShadowSettings.quality = quality
                for (view in views) {
                    showScene(engine, view.scene)
                    view.setup(engine)
                    repeat(3) { engine.update(1f / 60f) }
                    frames["${view.name}-${quality.name.lowercase()}"] = engine.read(device)
                }
            }

            // REN-22 cache: a static frame skips every shadow layer and gives the same image; moving casters re-renders
            ShadowSettings.quality = ShadowQualityLevel.MEDIUM
            showScene(engine, SHOWCASE)
            views.first().setup(engine)
            repeat(3) { engine.update(1f / 60f) }
            val before = engine.read(device)
            engine.update(1f / 60f)
            val skipped = engine.shadowPass().layersSkipped
            val after = engine.read(device)
            engine.find("[Shadows] Group").getComponent<Transform>()!!.translation.add(1.5f, 0f, -1f)
            repeat(2) { engine.update(1f / 60f) }
            frames["showcase-shadows-moved"] = engine.read(device)
            return Run(frames, before.contentEquals(after), skipped)
        } finally {
            runCatching { engine.destroy() }
            runCatching { device.close() }
            glfwDestroyWindow(window)
            runCatching { dir.deleteRecursively() }
        }
    }

    private fun showScene(engine: Engine, name: String) {
        val scene = engine.sceneManager.openScenes.first { if (name == MAIN) it.name != SHOWCASE else it.name == SHOWCASE }
        if (engine.sceneManager.currentScene !== scene) engine.sceneManager.switchScene(scene)
    }

    @Test
    fun Shadows_EveryQualityLevel_VulkanMatchesOpenGl_AndTheCacheHolds() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(runCatching { VulkanDevice.create().close() }.isSuccess, "no Vulkan driver")
        val errorsBefore = VulkanDevice.validationErrors

        val gl = renderAll({ OpenGlDevice.create() }, gl = true)
        val vk = renderAll({ VulkanDevice.create() }, gl = false)

        val report = StringBuilder("VK-04 shadow parity, ${width}x$height, Vulkan vs OpenGL; tolerance $TOLERANCE\n")
        val failures = ArrayList<String>()
        for ((name, a) in gl.frames) {
            val b = vk.frames.getValue(name)
            val d = FrameDiff(a, b)
            val ok = d.meets(TOLERANCE) && FrameDiff.distinctColours(b) > 20
            report.append("%-28s %s%s\n".format(name, d.summary(), if (ok) "" else "  <-- FAIL"))
            if (!ok) failures += name
            png("$name-gl", a); png("$name-vulkan", b)
            png("$name-diff", ByteArray(a.size).also { img ->
                for (p in d.perPixel.indices) { val v = minOf(255, d.perPixel[p] * 16).toByte(); img[p * 4] = v; img[p * 4 + 1] = v; img[p * 4 + 2] = v; img[p * 4 + 3] = -1 }
            })
        }
        report.append("cache: static frame identical gl=${gl.cacheIdentical} vulkan=${vk.cacheIdentical}; " +
            "layers skipped gl=${gl.skippedWhenStatic} vulkan=${vk.skippedWhenStatic}\n")
        println(report)
        File(out, "report.txt").writeText(report.toString())
        assertTrue(failures.isEmpty(), "views outside tolerance: $failures\n$report")
        assertTrue(vk.cacheIdentical, "a static Vulkan frame that skipped its shadow layers must not change")
        assertTrue(vk.skippedWhenStatic > 0 && vk.skippedWhenStatic == gl.skippedWhenStatic, "Vulkan keeps REN-22 shadow caching\n$report")
        assertTrue(VulkanDevice.validationErrors == errorsBefore, "Vulkan validation errors (see the skate.rhi.vulkan log)")
    }

    private fun png(name: String, rgba: ByteArray) {
        val flipped = ByteBuffer.allocateDirect(rgba.size)
        for (row in 0 until height) flipped.put((height - 1 - row) * width * 4, rgba, row * width * 4, width * 4)
        stbi_write_png(File(out, "$name.png").absolutePath, width, height, 4, flipped, width * 4)
    }

    companion object {
        private const val MAIN = "MainScene"
        private const val SHOWCASE = FeatureShowcase.SCENE_NAME

        /**
         * Same as VK-03 (mean at most 0.25/255, 99.9% of pixels within 2/255): shadow edges come from the same depth
         * values and hardware 2x2 PCF on both backends, so only driver rounding is allowed.
         */
        val TOLERANCE = FrameDiff.Tolerance(maxMean = 0.25, channel = 2, minShareWithin = 0.999)
    }
}
