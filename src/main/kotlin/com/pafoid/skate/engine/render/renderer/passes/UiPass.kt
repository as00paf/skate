package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.Renderer2D

/**
 * Runtime UI overlay pass, the last pass of the frame.
 *
 * Draws the primitives the UI systems recorded into [Renderer2D.ui] (screen pixels, origin top-left). It runs after
 * post-processing and the debug pass, so bloom/FXAA never blur the UI and grid lines or gizmos never draw over it.
 *
 * @param renderer2D Shared 2D renderer holding the UI draw list
 * @param batchShader The 2D batch shader the UI quads are drawn with
 * @param bindTarget Binds the presented render target and its viewport (same target as the debug pass)
 * @param targetSize Size of the presented target in pixels, read every frame (it changes on resize)
 * @param endTarget Ends the pass [bindTarget] began
 */
class UiPass(
    private val renderer2D: Renderer2D,
    private val batchShader: Shader,
    private val bindTarget: () -> Unit,
    private val targetSize: () -> Pair<Int, Int>,
    private val endTarget: () -> Unit = {},
) : BaseRenderPass() {

    override val name: String = "UiPass"
    override val description: String = "Renders the runtime UI on top of the post-processed scene and debug visuals"

    override fun execute(scene: Scene) {
        val (width, height) = targetSize()
        if (renderer2D.ui.quadCount == 0) {
            // Nothing recorded: still reset the list so scissor state does not leak into the next frame
            renderer2D.renderUi(width, height)
            return
        }
        bindTarget()
        renderer2D.bindShader(batchShader)
        renderer2D.renderUi(width, height)
        endTarget()
    }
}
