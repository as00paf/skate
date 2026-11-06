package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.RenderTraversal
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.game.samples.FeatureShowcase
import org.joml.Vector3f
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
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import java.io.File
import java.nio.ByteBuffer

/** A camera and environment setup in [scene], rendered with the passes [passes] accepts (VK-03, VK-05). */
class ParityView(val name: String, val scene: String, val passes: (String) -> Boolean, val setup: (Engine) -> Unit)

/**
 * Renders [ParityView]s with OpenGL and Vulkan in one JVM and compares them (VK-03, VK-05): each view's frame is read
 * back from the scene target in GL row order. Writes `<out>/<view>-{gl,vulkan,diff}.png` and `<out>/report.txt`.
 */
class BackendParityHarness(private val out: File, private val width: Int = 1280, private val height: Int = 720) {
    init {
        out.mkdirs()
    }

    /** Renders every view with one backend; returns RGBA8 frames in GL row order, by view name. */
    fun renderAll(createDevice: () -> RhiDevice, gl: Boolean, views: List<ParityView>): Map<String, ByteArray> {
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        if (gl) {
            glfwWindowHint(GLFW_SAMPLES, 4)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        } else {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        }
        val window = glfwCreateWindow(64, 64, "parity", 0, 0)
        check(window != 0L) { "no window" }
        if (gl) glfwMakeContextCurrent(window)
        val device = createDevice()
        val engine = Engine()
        val dir = kotlin.io.path.createTempDirectory("skate-parity").toFile()
        val frames = LinkedHashMap<String, ByteArray>()
        try {
            engine.start(window, device)
            engine.resizeFrameBuffer(width, height)
            check(ProjectManager(engine).createProject("Parity", dir).isSuccess)
            for (view in views) {
                val scene = engine.sceneManager.openScenes.first { if (view.scene == MAIN) it.name != SHOWCASE else it.name == view.scene }
                if (engine.sceneManager.currentScene !== scene) engine.sceneManager.switchScene(scene)
                engine.renderer.renderGraph.getAllPasses().forEach { it.isEnabled = view.passes(it.name) }
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

    /**
     * Compares the frames of every view against its [tolerance]; writes the images and the report titled [title]. Returns
     * the report and the names of the views outside tolerance (or with a flat Vulkan frame).
     */
    fun compare(title: String, views: List<ParityView>, gl: Map<String, ByteArray>, vk: Map<String, ByteArray>, tolerance: (ParityView) -> FrameDiff.Tolerance): Pair<String, List<String>> {
        val report = StringBuilder("$title, ${width}x$height, Vulkan vs OpenGL\n")
        val failures = ArrayList<String>()
        for (view in views) {
            val a = gl.getValue(view.name)
            val b = vk.getValue(view.name)
            val d = FrameDiff(a, b)
            val colours = FrameDiff.distinctColours(b)
            val t = tolerance(view)
            val ok = d.meets(t) && colours > 20
            report.append("%-30s %s, colours %d (mean <= %.2f, %.1f%% within %d)%s\n".format(
                view.name, d.summary(), colours, t.maxMean, 100 * t.minShareWithin, t.channel, if (ok) "" else "  <-- FAIL"))
            if (!ok) failures += view.name
            png("${view.name}-gl", a); png("${view.name}-vulkan", b)
            png("${view.name}-diff", ByteArray(a.size).also { img ->
                for (p in d.perPixel.indices) { val v = minOf(255, d.perPixel[p] * 16).toByte(); img[p * 4] = v; img[p * 4 + 1] = v; img[p * 4 + 2] = v; img[p * 4 + 3] = -1 }
            })
        }
        println(report)
        File(out, "report.txt").writeText(report.toString())
        return report.toString() to failures
    }

    /** Saves GL-order rows (bottom first) as an upright PNG. */
    private fun png(name: String, rgba: ByteArray) {
        val flipped = ByteBuffer.allocateDirect(rgba.size)
        for (row in 0 until height) flipped.put((height - 1 - row) * width * 4, rgba, row * width * 4, width * 4)
        stbi_write_png(File(out, "$name.png").absolutePath, width, height, 4, flipped, width * 4)
    }

    companion object {
        const val MAIN = "MainScene"
        const val SHOWCASE = FeatureShowcase.SCENE_NAME
    }
}

fun Engine.look(eye: Vector3f, target: Vector3f) {
    cameraManager.transform.translation.set(eye)
    cameraManager.transform.lookAt(target)
}

fun Engine.focus(target: com.pafoid.skate.engine.ecs.components.CameraFocusTarget) {
    cameraManager.transform.translation.set(target.position)
    cameraManager.transform.rotation.set(target.rotation)
}

/**
 * Poses the skinned characters away from their bind pose: steps only the animation system, with the scene marked
 * running just for it (no cameras, physics or scripts), so both backends get the same pose.
 */
fun Engine.animate() {
    val scene = checkNotNull(sceneManager.currentScene)
    val animation = checkNotNull(systemManager.getSystem<AnimationSystem>()) { "no animation system" }
    scene.isRunning = true
    try {
        repeat(45) { animation.update(1f / 60f) }
    } finally {
        scene.isRunning = false
    }
}

fun Engine.environment(): EnvironmentComponent? = sceneManager.currentScene?.getComponent<EnvironmentComponent>()

fun Engine.find(name: String): GameObject {
    val scene = checkNotNull(sceneManager.currentScene)
    var found: GameObject? = null
    RenderTraversal.forEachRendered(scene) { if (found == null && it.name == name) found = it }
    return checkNotNull(found) { "no $name in ${scene.name}" }
}

fun Engine.objectPosition(name: String): Vector3f = find(name).getComponent<Transform>()!!.worldMatrix.getTranslation(Vector3f())

fun Engine.material(name: String): Material = checkNotNull(find(name).getComponent<RenderComponent>()?.material) { "$name has no material" }

/** A base colour texture of some mesh in the current scene. */
fun Engine.anyTexture(): Texture {
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

fun Engine.skinnedObjectPosition(): Vector3f {
    val scene = checkNotNull(sceneManager.currentScene)
    var found: Vector3f? = null
    RenderTraversal.forEachRendered(scene) { go ->
        if (found == null && go.getComponent<SkeletonComponent>() != null) {
            found = go.getComponent<Transform>()?.worldMatrix?.getTranslation(Vector3f())
        }
    }
    return checkNotNull(found) { "no skinned object in ${scene.name}" }
}

