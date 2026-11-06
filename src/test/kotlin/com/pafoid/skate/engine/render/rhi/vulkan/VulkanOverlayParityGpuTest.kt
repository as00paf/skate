package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraFocusTarget
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.systems.ParticleSystem
import com.pafoid.skate.engine.getAllComponents
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import com.pafoid.skate.engine.ui.PauseMenu
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UIImageComponent
import com.pafoid.skate.engine.ui.UIListComponent
import com.pafoid.skate.engine.ui.UiListItem
import com.pafoid.skate.engine.ui.UiScaleMode
import com.pafoid.skate.game.samples.FeatureShowcase
import com.pafoid.skate.testing.GpuTest
import org.joml.Vector3f
import org.joml.Vector4f
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
 * VK-06: the passes drawn over the geometry (runtime UI, instanced particles, debug lines) match OpenGL on Vulkan.
 * Each view renders the geometry pass plus the pass under test, with the same scene state on both backends (seeded
 * particles stepped the same way, no Play-time systems). Opt-in (`SKATE_RHI=vulkan`), GPU-only (@GpuTest). Writes
 * `build/vk06/<view>-{gl,vulkan,diff}.png` and `build/vk06/report.txt`; the tolerance is [TOLERANCE], or
 * [DEBUG_TOLERANCE] for views with debug geometry (docs/design/VK-06.md). Needs the VK-05 orientation convention.
 */
@GpuTest
class VulkanOverlayParityGpuTest {
    private val width = 1280
    private val height = 720
    private val out = File("build/vk06").apply { mkdirs() }

    /** A camera and scene setup plus the passes to run (besides the geometry pass). */
    private class View(val name: String, val passes: Set<String>, val setup: (Engine) -> Unit)

    private val views: List<View> = listOf(
        // Showcase legend canvas: panels, buttons, SDF text (CONSTANT and scaled canvases)
        View("ui-showcase", setOf(UI)) { it.focus(FeatureShowcase.overviewTarget()) },
        // Pause menu over the scene: 60% black dim (straight alpha, target kept opaque), panel, buttons, title, slider
        View("ui-pause-menu", setOf(UI)) { e ->
            e.focus(FeatureShowcase.overviewTarget())
            e.scene().addChild(PauseMenu.create().also { it.getComponent<UICanvasComponent>()!!.visible = true })
        },
        // A scrolled list clips its rows with the scissor; a translucent image overlaps the clip edge
        View("ui-scissor-list", setOf(UI)) { e ->
            e.focus(FeatureShowcase.overviewTarget())
            e.scene().addChild(listCanvas())
        },
        // Every showcase emitter (alpha and additive, textured and flat), seeded and stepped identically
        View("particles-area", setOf(PARTICLES)) { e ->
            e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "particles" }))
            e.simulateParticles()
        },
        View("particles-close", setOf(PARTICLES)) { e ->
            val fire = e.scene().children.single { it.name == "[Particles] Fire" }
            val at = fire.getComponent<com.pafoid.skate.engine.ecs.components.Transform>()!!.worldMatrix.getTranslation(Vector3f())
            e.look(Vector3f(at).add(0.8f, 1.2f, 2.4f), Vector3f(at).add(0f, 0.9f, 0f))
            e.simulateParticles()
        },
        // Editor grid with the REN-19 per-fragment depth pull, seen from above and at a grazing angle
        View("debug-grid", setOf(DEBUG)) { it.look(Vector3f(4f, 6f, 9f), Vector3f(0f, 0f, 0f)) },
        // From high above the origin axes are short (axis length shrinks with height) and entirely in front of the camera
        View("debug-grid-above", setOf(DEBUG)) { it.look(Vector3f(3f, 30f, 12f), Vector3f(0f, 0f, 0f)) },
        View("debug-grid-grazing", setOf(DEBUG)) { it.look(Vector3f(0f, 0.35f, 6f), Vector3f(0f, 0.2f, -30f)) },
        // Physics collider wireframes (debug lines and triangles, depth-tested against the scene)
        View("debug-physics", setOf(DEBUG)) { e ->
            e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "physics" }))
            e.scene().getComponent<ScenePhysicsComponent>()?.debugEnabled = true
        },
        // Everything at once, in frame order: particles, debug, UI
        View("all-overlays", setOf(PARTICLES, DEBUG, UI)) { e ->
            e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "particles" }))
            e.simulateParticles()
        },
    )

    private fun Engine.scene() = checkNotNull(sceneManager.currentScene)

    private fun Engine.look(eye: Vector3f, target: Vector3f) {
        cameraManager.transform.translation.set(eye)
        cameraManager.transform.lookAt(target)
    }

    private fun Engine.focus(target: CameraFocusTarget) {
        cameraManager.transform.translation.set(target.position)
        cameraManager.transform.rotation.set(target.rotation)
    }

    /**
     * Seeds every emitter, then steps only the particle system with the scene marked running (no cameras, physics or
     * scripts), so both backends draw the same particles. The system is then disabled: in edit mode it would clear them.
     */
    private fun Engine.simulateParticles() {
        val scene = scene()
        scene.getAllComponents<ParticleEmitter>().forEachIndexed { i, emitter -> emitter.seed = 1000 + i }
        val particles = checkNotNull(systemManager.getSystem<ParticleSystem>()) { "no particle system" }
        scene.isRunning = true
        try {
            repeat(90) { particles.update(1f / 60f) }
        } finally {
            scene.isRunning = false
        }
        particles.enabled = false
        check(scene.getAllComponents<ParticleEmitter>().any { it.runtime.pool.count > 0 }) { "no live particles" }
    }

    private fun listCanvas() = GameObject("[VK-06] ListCanvas").apply {
        addComponent(UICanvasComponent(scaleMode = UiScaleMode.CONSTANT_PIXEL, sortOrder = 50))
        addComponent(UIElementComponent().stretched())
        addChild(GameObject("List").apply {
            addComponent(UIElementComponent().placed(0.5f, 0.5f, 0f, 0f, 420f, 190f))
            addComponent(UIImageComponent(tint = Vector4f(0.1f, 0.12f, 0.2f, 0.9f)))
            addComponent(UIListComponent(items = (1..12).map { UiListItem("hud.score", listOf("$it")) }, itemHeight = 40f, selectedIndex = 3, scroll = 25f))
        })
        addChild(GameObject("Overlap").apply {
            addComponent(UIElementComponent().placed(0.5f, 0.5f, 170f, 80f, 160f, 90f))
            addComponent(UIImageComponent(tint = Vector4f(0.9f, 0.3f, 0.2f, 0.5f)))
        })
    }

    /** Renders every view with one backend; returns RGBA8 frames in GL row order, by view name. */
    private fun renderAll(createDevice: () -> RhiDevice, gl: Boolean): Map<String, ByteArray> {
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        if (gl) {
            glfwWindowHint(GLFW_SAMPLES, 4)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        } else {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        }
        val window = glfwCreateWindow(64, 64, "VK-06", 0, 0)
        check(window != 0L) { "no window" }
        if (gl) glfwMakeContextCurrent(window)
        val device = createDevice()
        val engine = Engine()
        val dir = kotlin.io.path.createTempDirectory("skate-vk06").toFile()
        val frames = LinkedHashMap<String, ByteArray>()
        try {
            engine.start(window, device)
            engine.resizeFrameBuffer(width, height)
            engine.gameStrings.addStrings(com.pafoid.skate.engine.assets.Assets.Strings.GAME_STRINGS)
            check(ProjectManager(engine).createProject("Vk06", dir).isSuccess)
            val scene = engine.sceneManager.openScenes.first { it.name == FeatureShowcase.SCENE_NAME }
            if (engine.sceneManager.currentScene !== scene) engine.sceneManager.switchScene(scene)
            for (view in views) {
                val enabled = view.passes + "GeometryPass"
                engine.renderer.renderGraph.getAllPasses().forEach { it.isEnabled = it.name in enabled }
                view.setup(engine)
                repeat(3) { engine.update(1f / 60f) }
                val pixels = ByteBuffer.allocateDirect(width * height * 4)
                device.readPixels(engine.renderer.frameBuffer.target, 0, IntRect(0, 0, width, height), PixelFormat.RGBA8, pixels)
                frames[view.name] = ByteArray(pixels.capacity()).also { pixels.get(it) }
                reset(engine)
            }
            return frames
        } finally {
            runCatching { engine.destroy() }
            runCatching { device.close() }
            glfwDestroyWindow(window)
            runCatching { dir.deleteRecursively() }
        }
    }

    /** Undoes a view's setup: added canvases, the paused particle system (re-enabled, it clears the particles), physics lines. */
    private fun reset(engine: Engine) {
        val scene = engine.scene()
        scene.children.filter { it.name == PauseMenu.NAME || it.name.startsWith("[VK-06]") }.forEach { scene.removeChild(it) }
        scene.getComponent<ScenePhysicsComponent>()?.debugEnabled = false
        engine.systemManager.getSystem<ParticleSystem>()?.enabled = true
        engine.update(1f / 60f)
    }

    @Test
    fun Overlays_EveryView_VulkanMatchesOpenGl() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(runCatching { VulkanDevice.create().close() }.isSuccess, "no Vulkan driver")
        val errorsBefore = VulkanDevice.validationErrors

        val gl = renderAll({ OpenGlDevice.create() }, gl = true)
        val vk = renderAll({ VulkanDevice.create() }, gl = false)

        val report = StringBuilder("VK-06 UI/particles/debug parity, ${width}x$height, Vulkan vs OpenGL; tolerance $TOLERANCE\n")
        val failures = ArrayList<String>()
        for (view in views) {
            val a = gl.getValue(view.name)
            val b = vk.getValue(view.name)
            val d = FrameDiff(a, b)
            val tolerance = if (DEBUG in view.passes) DEBUG_TOLERANCE else TOLERANCE
            val ok = d.meets(tolerance)
            report.append("%-20s %s%s\n".format(view.name, d.summary(), if (ok) "" else "  <-- FAIL"))
            if (!ok) failures += view.name
            png("${view.name}-gl", a); png("${view.name}-vulkan", b)
            png("${view.name}-diff", ByteArray(a.size).also { img ->
                for (p in d.perPixel.indices) { val v = minOf(255, d.perPixel[p] * 16).toByte(); img[p * 4] = v; img[p * 4 + 1] = v; img[p * 4 + 2] = v; img[p * 4 + 3] = -1 }
            })
        }
        println(report)
        File(out, "report.txt").writeText(report.toString())
        assertTrue(failures.isEmpty(), "views outside tolerance: $failures\n$report")
        assertTrue(VulkanDevice.validationErrors == errorsBefore, "Vulkan validation errors (see the skate.rhi.vulkan log)")
    }

    /** Saves GL-order rows (bottom first) as an upright PNG. */
    private fun png(name: String, rgba: ByteArray) {
        val flipped = ByteBuffer.allocateDirect(rgba.size)
        for (row in 0 until height) flipped.put((height - 1 - row) * width * 4, rgba, row * width * 4, width * 4)
        stbi_write_png(File(out, "$name.png").absolutePath, width, height, 4, flipped, width * 4)
    }

    companion object {
        private const val UI = "UiPass"
        private const val PARTICLES = "ParticlePass"
        private const val DEBUG = "DebugPass"

        /** Same budget as VK-03: driver rounding on scattered pixels (blending, filtering), nothing visible. */
        val TOLERANCE = FrameDiff.Tolerance(maxMean = 0.25, channel = 2, minShareWithin = 0.999)

        /**
         * Views with debug geometry: 99.8% within 2/255. The editor's X/Z axes are 100 m ribbons a few centimetres
         * wide; when one end is behind the camera, the drivers clip them at the near plane with different rounding,
         * and the ribbon edge moves by a pixel or two along its length (about 0.14% of the frame in `debug-grid`).
         * Seen from where the axes do not cross the near plane (`debug-grid-above`), parity is 99.999%. A missing
         * grid, axis or collider wireframe still fails by far.
         */
        val DEBUG_TOLERANCE = FrameDiff.Tolerance(maxMean = 0.25, channel = 2, minShareWithin = 0.998)
    }
}
