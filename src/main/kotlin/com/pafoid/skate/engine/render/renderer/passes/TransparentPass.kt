package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.graph.RenderResourceIds
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.rhi.RhiContext

/**
 * Draws the alpha-blended meshes the geometry pass collected, in scene order, over the opaque scene, the sky and water
 * (REN-29 task 1). Lighting, shadows and IBL come from the frame the geometry pass began ([SceneFrame]).
 */
class TransparentPass(
    private val modelRenderer: ModelRenderer,
    private val frameBuffer: FrameBuffer,
    private val sceneFrame: SceneFrame,
    private val useFbo: Boolean,
) : BaseRenderPass() {

    override val name: String = "TransparentPass"
    override val inputs: Set<String> = setOf(RenderResourceIds.SCENE_COLOR, RenderResourceIds.SCENE_DEPTH)
    override val outputs: Set<String> = setOf(RenderResourceIds.SCENE_COLOR)
    override val description: String = "Renders alpha-blended meshes over the opaque scene"

    private val presentedViewport = PresentedViewport()

    override fun execute(scene: Scene) {
        val queue = sceneFrame.transparent
        if (!sceneFrame.began || queue.size == 0) return
        val cmd = RhiContext.require().commandList()
        cmd.beginPass(if (useFbo) frameBuffer.target else null, viewport = presentedViewport.of(frameBuffer, useFbo))
        GeometryPass.drawItems(queue, sceneFrame, modelRenderer, scene)
        cmd.endPass()
        queue.clear()
    }

    override fun cleanup() {
        // Disabled or skipped: drop the hand-off so items never leak into the next frame
        sceneFrame.transparent.clear()
    }
}
