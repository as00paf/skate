package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.Sprite
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.config.FogCycle
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.RenderTraversal
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
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
 * VK-03: the geometry pass on Vulkan matches OpenGL view by view: MainScene (lit, textured, skinned skater, sky dome)
 * and the FeatureShowcase areas the geometry pass draws (point/spot lights with per-object culling, PBR and emissive
 * materials, alpha blending, skinned characters, the HDRI sky, every fog mode). Only the geometry pass runs on either
 * backend. Opt-in (`SKATE_RHI=vulkan`), GPU-only (@GpuTest). Writes `build/vk03/<view>-{gl,vulkan,diff}.png` and
 * `build/vk03/report.txt`; the tolerance is [TOLERANCE] (docs/testing/VK-03.md).
 */
@GpuTest
class VulkanGeometryParityGpuTest {
    private val width = 1280
    private val height = 720
    private val out = File("build/vk03").apply { mkdirs() }

    /** A camera and environment setup, applied before rendering a few frames and reading the scene target back. */
    private class View(val name: String, val scene: String, val setup: (Engine) -> Unit)

    private val views: List<View> = buildList {
        add(View("main-default", MAIN) { it.look(Vector3f(6f, 4f, 8f), Vector3f(0f, 0.8f, 0f)) })
        add(View("main-skater", MAIN) { e ->
            val skater = e.skinnedObjectPosition()
            e.look(Vector3f(skater).add(1.6f, 1.4f, 2.2f), Vector3f(skater).add(0f, 1f, 0f))
            e.animate()
        })
        add(View("main-sky", MAIN) { it.look(Vector3f(0f, 1.5f, 0f), Vector3f(10f, 6f, -10f)) })
        // 2D sprites batched in the geometry pass (dynamic vertex buffers): coloured and textured, two z-indices
        add(View("main-sprites", MAIN) { e ->
            e.look(Vector3f(0f, 1.5f, 6f), Vector3f(0f, 1.5f, 0f))
            val texture = e.anyTexture()
            val scene = checkNotNull(e.sceneManager.currentScene)
            for (i in 0 until 6) {
                val sprite = Sprite(texture = if (i % 2 == 0) texture else null)
                val colour = Vector4f(0.2f + 0.15f * i, 1f - 0.12f * i, 0.3f + 0.1f * (i % 3), 1f)
                scene.addChild(GameObject("[VK-03] Sprite_$i")
                    .addComponent(Transform(Vector3f(-2.5f + i, 1f + 0.3f * (i % 2), 0.5f * (i % 3)), Vector3f(0.9f)))
                    .addComponent(SpriteRenderer(colour, sprite, zIndex = i % 2)))
            }
        })
        add(View("showcase-overview", SHOWCASE) { it.focus(FeatureShowcase.overviewTarget()) })
        for (key in listOf("lights", "materials", "animation", "prefabs", "netcode")) {
            add(View("showcase-$key", SHOWCASE) { e ->
                e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { a -> a.key == key }))
                if (key == "animation") e.animate()
            })
        }
        // Alpha MASK: the transparent cube (alpha 0.4) passes a 0.3 cutoff, the textured cube (alpha 0.2) is discarded
        add(View("showcase-alpha-mask", SHOWCASE) { e ->
            e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "materials" }))
            e.material("[Materials] Transparent").apply { alphaMode = AlphaMode.MASK; alphaCutoff = 0.3f }
            e.material("[Materials] Textured").apply { alphaMode = AlphaMode.MASK; alphaCutoff = 0.5f; baseColor.w = 0.2f }
        })
        // Culling: from inside the fog tower only double-sided faces are visible
        for (doubleSided in listOf(false, true)) {
            add(View(if (doubleSided) "showcase-inside-double-sided" else "showcase-inside-culled", SHOWCASE) { e ->
                val tower = e.objectPosition("[Fog] Tower")
                e.look(Vector3f(tower).add(0f, 1f, 0f), Vector3f(tower).add(0.4f, 1.6f, -0.3f))
                e.material("[Fog] Tower").doubleSided = doubleSided
            })
        }
        for (step in FogCycle.Step.entries) {
            add(View("showcase-fog-${step.name.lowercase()}", SHOWCASE) { e ->
                e.focus(FeatureShowcase.focusTarget(FeatureShowcase.AREAS.first { it.key == "fog" }))
                e.environment()?.let { FogCycle.apply(it, step) }
            })
        }
    }

    private fun Engine.look(eye: Vector3f, target: Vector3f) {
        cameraManager.transform.translation.set(eye)
        cameraManager.transform.lookAt(target)
    }

    private fun Engine.focus(target: com.pafoid.skate.engine.ecs.components.CameraFocusTarget) {
        cameraManager.transform.translation.set(target.position)
        cameraManager.transform.rotation.set(target.rotation)
    }

    /**
     * Poses the skinned characters away from their bind pose: steps only the animation system, with the scene marked
     * running just for it (no cameras, physics or scripts), so both backends get the same pose.
     */
    private fun Engine.animate() {
        val scene = checkNotNull(sceneManager.currentScene)
        val animation = checkNotNull(systemManager.getSystem<AnimationSystem>()) { "no animation system" }
        scene.isRunning = true
        try {
            repeat(45) { animation.update(1f / 60f) }
        } finally {
            scene.isRunning = false
        }
    }

    private fun Engine.environment(): EnvironmentComponent? = sceneManager.currentScene?.getComponent<EnvironmentComponent>()

    private fun Engine.find(name: String): GameObject {
        val scene = checkNotNull(sceneManager.currentScene)
        var found: GameObject? = null
        RenderTraversal.forEachRendered(scene) { if (found == null && it.name == name) found = it }
        return checkNotNull(found) { "no $name in ${scene.name}" }
    }

    private fun Engine.objectPosition(name: String): Vector3f = find(name).getComponent<Transform>()!!.worldMatrix.getTranslation(Vector3f())

    private fun Engine.material(name: String): Material = checkNotNull(find(name).getComponent<RenderComponent>()?.material) { "$name has no material" }

    /** A base colour texture of some mesh in the current scene. */
    private fun Engine.anyTexture(): Texture {
        val scene = checkNotNull(sceneManager.currentScene)
        var found: Texture? = null
        RenderTraversal.forEachRendered(scene) { go ->
            if (found == null) {
                val rc = go.getComponent<RenderComponent>()
                found = rc?.material?.baseColorTexture ?: rc?.model?.mesh?.firstNotNullOfOrNull { it.material.baseColorTexture }
            }
        }
        return checkNotNull(found) { "no textured mesh in ${scene.name}" }
    }

    private fun Engine.skinnedObjectPosition(): Vector3f {
        val scene = checkNotNull(sceneManager.currentScene)
        var found: Vector3f? = null
        RenderTraversal.forEachRendered(scene) { go ->
            if (found == null && go.getComponent<SkeletonComponent>() != null) {
                found = go.getComponent<Transform>()?.worldMatrix?.getTranslation(Vector3f())
            }
        }
        return checkNotNull(found) { "no skinned object in ${scene.name}" }
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
        val window = glfwCreateWindow(64, 64, "VK-03", 0, 0)
        check(window != 0L) { "no window" }
        if (gl) glfwMakeContextCurrent(window)
        val device = createDevice()
        val engine = Engine()
        val dir = kotlin.io.path.createTempDirectory("skate-vk03").toFile()
        val frames = LinkedHashMap<String, ByteArray>()
        try {
            engine.start(window, device)
            engine.resizeFrameBuffer(width, height)
            check(ProjectManager(engine).createProject("Vk03", dir).isSuccess)
            for (view in views) {
                val scene = engine.sceneManager.openScenes.first { if (view.scene == MAIN) it.name != SHOWCASE else it.name == SHOWCASE }
                if (engine.sceneManager.currentScene !== scene) engine.sceneManager.switchScene(scene)
                engine.renderer.renderGraph.getAllPasses().forEach { it.isEnabled = it.name == "GeometryPass" }
                view.setup(engine)
                repeat(3) { engine.update(1f / 60f) }
                val pixels = ByteBuffer.allocateDirect(width * height * 4)
                device.readPixels(engine.renderer.frameBuffer.target, 0, IntRect(0, 0, width, height), PixelFormat.RGBA8, pixels)
                frames[view.name] = ByteArray(pixels.capacity()).also { pixels.get(it) }
            }
            return frames
        } finally {
            runCatching { engine.destroy() }
            runCatching { device.close() }
            glfwDestroyWindow(window)
            runCatching { dir.deleteRecursively() }
        }
    }

    @Test
    fun GeometryPass_EveryView_VulkanMatchesOpenGl() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        assumeTrue(runCatching { VulkanDevice.create().close() }.isSuccess, "no Vulkan driver")
        val errorsBefore = VulkanDevice.validationErrors

        val gl = renderAll({ OpenGlDevice.create() }, gl = true)
        val vk = renderAll({ VulkanDevice.create() }, gl = false)

        val report = StringBuilder("VK-03 geometry pass parity, ${width}x$height, Vulkan vs OpenGL; tolerance $TOLERANCE\n")
        val failures = ArrayList<String>()
        for (view in views) {
            val a = gl.getValue(view.name)
            val b = vk.getValue(view.name)
            val d = FrameDiff(a, b)
            val colours = FrameDiff.distinctColours(b)
            val ok = d.meets(TOLERANCE) && colours > 20
            report.append("%-24s %s, colours %d%s\n".format(view.name, d.summary(), colours, if (ok) "" else "  <-- FAIL"))
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
        private const val MAIN = "MainScene"
        private const val SHOWCASE = FeatureShowcase.SCENE_NAME

        /**
         * Mean channel difference at most 0.25/255 and 99.9% of pixels within 2/255 of OpenGL: allows the drivers'
         * rounding (blending, filtering, interpolation) on scattered pixels, not a visible difference.
         */
        val TOLERANCE = FrameDiff.Tolerance(maxMean = 0.25, channel = 2, minShareWithin = 0.999)
    }
}
