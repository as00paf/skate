package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.RenderResources
import com.pafoid.skate.engine.render.graph.RenderGraph

class Renderer(
    var renderResources: RenderResources,
    val cameraManager: CameraManager,
) {
    val frameBuffer: FrameBuffer
        get() = renderResources.frameBuffer

    val renderGraph: RenderGraph
        get() = renderResources.renderGraph

    fun render(scene: Scene) {
        // Reset per-frame draw call counter
        EngineStats.resetDrawCalls()

        // Update camera viewport dimensions once for all passes (correct aspect ratio)
        cameraManager.camera.viewportWidth = renderResources.frameBuffer.width
        cameraManager.camera.viewportHeight = renderResources.frameBuffer.height

        // Execute the render graph - this handles all preparation, execution, and cleanup of passes
        renderResources.renderGraph.execute(scene)
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
