package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.render.renderer.passes.RenderPass

/**
 * Fluent builder for creating [RenderGraph] instances.
 */
class RenderGraphBuilder {
    private val graph = RenderGraph()
    
    /**
     * Registers a set of initial resources.
     */
    fun withResources(vararg resources: RenderResource): RenderGraphBuilder {
        resources.forEach { graph.registerResource(it) }
        return this
    }
    
    /**
     * Appends a render pass to the graph execution chain.
     */
    fun addPass(pass: RenderPass): RenderGraphBuilder {
        graph.addPass(pass)
        return this
    }
    
    /**
     * Builds and returns the [RenderGraph] instance.
     */
    fun build(): RenderGraph = graph
}
