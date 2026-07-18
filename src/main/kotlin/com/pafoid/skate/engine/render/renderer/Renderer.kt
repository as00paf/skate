package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.RenderResources
import com.pafoid.skate.engine.render.RenderResourcesFactory
import com.pafoid.skate.engine.render.graph.RenderGraph
import org.joml.Vector3f
import org.lwjgl.opengl.GL30.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL30.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL30.GL_DEPTH_TEST
import org.lwjgl.opengl.GL30.glClear
import org.lwjgl.opengl.GL30.glClearColor
import org.lwjgl.opengl.GL30.glEnable
import org.lwjgl.opengl.GL30.glViewport

class Renderer(
    private val factory: RenderResourcesFactory,
    private val initialWidth: Int = 1920,
    private val initialHeight: Int = 1080
) {

    lateinit var renderResources: RenderResources
    private var isInitialized = false

    var useFbo: Boolean = true
    val frameBuffer: FrameBuffer
        get() = renderResources.frameBuffer

    val renderGraph: RenderGraph
        get() = renderResources.renderGraph

    suspend fun initialize() {
        if (!isInitialized) {
            renderResources = factory.create(initialWidth, initialHeight)
            isInitialized = true
        }
    }

    fun render(scene: Scene) {
        // Reset per-frame draw call counter
        EngineStats.resetDrawCalls()

        // Update camera viewport dimensions once for all passes (correct aspect ratio)
        val width = renderResources.frameBuffer.width
        val height = renderResources.frameBuffer.height
        scene.camera.viewportWidth = width
        scene.camera.viewportHeight = height

        // Execute the render graph - this handles all preparation, execution, and cleanup of passes
        renderResources.renderGraph.execute(scene)

        // Final screen viewport reset
        glViewport(0, 0, renderResources.frameBuffer.width, renderResources.frameBuffer.height)
    }

    fun readPixel(nx: Float, ny: Float): Int {
        val w = renderResources.frameBuffer.width
        val h = renderResources.frameBuffer.height

        val x = (nx * w).toInt()
        val y = (ny * h).toInt()

        // Clamp coordinates
        val safeX = x.coerceIn(0, w - 1)
        val safeY = y.coerceIn(0, h - 1)

        // Invert Y coordinate (screen space to texture space)
        return renderResources.pickingTexture.readPixel(safeX, h - 1 - safeY)
    }

    /**
     * Clears the screen with the specified sky color.
     *
     * @param sky The sky color to clear with (RGB)
     */
    fun clearColor(sky: Vector3f) {
        glEnable(GL_DEPTH_TEST)
        glClearColor(sky.x, sky.y, sky.z, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }

    fun destroy() {
        // Destroy shaders
        renderResources.shaders.default.destroy()
        renderResources.shaders.batch.destroy()
        renderResources.shaders.skybox.destroy()
        renderResources.shaders.picking.destroy()
        renderResources.shaders.picking3D.destroy()
        renderResources.shaders.debug.destroy()
        renderResources.shaders.skyDome.destroy()
        renderResources.shaders.shadow.destroy()

        // Destroy renderers
        renderResources.renderers.skybox.destroy()
        renderResources.renderers.skyDome.destroy()
        renderResources.renderers.shadow.destroy()

        // Destroy framebuffer (includes texture and depth buffer)
        renderResources.frameBuffer.destroy()

        // Destroy picking texture
        renderResources.pickingTexture.destroy()

        // Destroy shadow map
        renderResources.shadowMap?.destroy()
    }

    fun resize(width: Int, height: Int) {
        renderResources.frameBuffer.resize(width, height)
        renderResources.pickingTexture.resize(width, height)
        renderResources.renderPasses.picking.resize(width, height)
    }
}
