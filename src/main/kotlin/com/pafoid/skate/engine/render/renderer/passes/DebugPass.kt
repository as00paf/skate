package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.DebugRenderer

/**
 * Debug visualization render pass.
 * 
 * Renders debug lines, triangles, and other visualization aids
 * on top of the final rendered image. This pass is typically
 * executed last so debug geometry appears over everything.
 * 
 * @param debugRenderer The debug renderer for drawing lines and shapes
 */
class DebugPass(
    private val debugRenderer: DebugRenderer
) : RenderPass {

    fun beginFrame() {
        debugRenderer.beginFrame()
    }

    override fun execute(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) {
        debugRenderer.draw()
    }
}
