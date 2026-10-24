package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.passes.RenderPass
import com.pafoid.skate.engine.render.state.GLBindings

class RenderGraph {
    private val passes = mutableListOf<RenderPass>()

    fun addPass(pass: RenderPass) {
        passes.add(pass)
    }

    fun getAllPasses(): List<RenderPass> = passes.toList()

    fun getPassByName(name: String): RenderPass? {
        return passes.find { it.name == name }
    }

    fun execute(scene: Scene) {
        // 1. Prepare all enabled nodes
        passes.filter { it.isEnabled }.forEach { it.prepare() }

        // 2. Execute all enabled nodes with timing
        passes.filter { it.isEnabled }.forEach {
            // Passes may use raw GL, so cached bindings are not trusted across pass boundaries
            GLBindings.cache.reset()
            it.executeWithTiming(scene)
            GLBindings.cache.reset()
        }

        // 3. Cleanup all nodes (even disabled ones may need cleanup)
        passes.forEach { it.cleanup() }
    }

    fun clear() {
        passes.clear()
    }
}
