package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
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

/**
 * Main renderer orchestrator.
 *
 * This class is responsible for orchestrating the rendering pipeline by executing
 * render passes in the correct order. All rendering resources are provided via
 * [RenderResources], which is created by [RenderResourcesFactory].
 *
 * @param factory Factory to create rendering resources
 * @param initialWidth Initial viewport width (default 1920)
 * @param initialHeight Initial viewport height (default 1080)
 */
class Renderer(
    private val factory: RenderResourcesFactory,
    private val initialWidth: Int = 1920,
    private val initialHeight: Int = 1080
) : IRenderer {

    private lateinit var renderResources: RenderResources
    private var isInitialized = false

    override var useFbo: Boolean = true

    /**
     * Exposes the framebuffer for editor integration.
     * Use this to access the framebuffer texture ID for ImGui rendering.
     */
    val frameBuffer: FrameBuffer
        get() = renderResources.frameBuffer

    /**
     * Exposes the render graph for visualization and debugging.
     * Use this to access render pass information for the Render Graph window.
     */
    val renderGraph: RenderGraph
        get() = renderResources.renderGraph

    /**
     * Initializes the renderer by creating all render resources.
     * Must be called before render() is called.
     */
    suspend fun initialize() {
        if (!isInitialized) {
            renderResources = factory.create(initialWidth, initialHeight)
            isInitialized = true
        }
    }

    /**
     * Renders the scene by executing the render graph.
     *
     * @param scene The scene to render
     * @param activeGameObject The currently selected game object (if any)
     * @param hoveredGameObject The currently hovered game object (if any)
     */
    override fun render(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) {
        // Update camera viewport dimensions once for all passes (correct aspect ratio)
        val width = renderResources.frameBuffer.width
        val height = renderResources.frameBuffer.height
        scene.camera.viewportWidth = width
        scene.camera.viewportHeight = height

        // Execute the render graph - this handles all preparation, execution, and cleanup of passes
        renderResources.renderGraph.execute(scene, activeGameObject, hoveredGameObject)

        // Final screen viewport reset
        glViewport(0, 0, renderResources.frameBuffer.width, renderResources.frameBuffer.height)
    }

    /**
     * Reads a pixel value from the picking texture at the specified normalized screen coordinates.
     *
     * @param x The normalized X coordinate (0.0 to 1.0)
     * @param y The normalized Y coordinate (0.0 to 1.0)
     * @return The encoded entity ID at the specified pixel, or -1 if no entity.
     */
    override fun readPixel(nx: Float, ny: Float): Int {
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
    override fun clearColor(sky: Vector3f) {
        glEnable(GL_DEPTH_TEST)
        glClearColor(sky.x, sky.y, sky.z, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }

    /**
     * Destroys all rendering resources.
     *
     * This method should be called when shutting down the renderer to properly
     * release OpenGL resources and prevent memory leaks.
     */
    override fun destroy() {
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

    /**
     * Resizes the framebuffer and picking texture.
     *
     * Call this method when the window is resized to ensure rendering
     * uses the correct dimensions.
     *
     * @param width The new viewport width
     * @param height The new viewport height
     */
    fun resize(width: Int, height: Int) {
        renderResources.frameBuffer.resize(width, height)
        renderResources.pickingTexture.resize(width, height)
        renderResources.renderPasses.picking.resize(width, height)
    }
}
