package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.graph.RenderContext
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
) : BaseRenderPass() {

    override val name: String = "DebugPass"
    override val description: String = "Renders debug visualization (physics, gizmos, etc.)"

    override fun prepare() {
        debugRenderer.beginFrame()
    }

    @Deprecated("Use execute(context: RenderContext) instead")
    override fun execute(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) {
        execute(RenderContext(scene, activeGameObject, hoveredGameObject))
    }

    override fun execute(context: RenderContext) {
        debugRenderer.draw()
    }
}
