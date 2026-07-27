package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.render.renderer.passes.RenderPass

class RenderGraphBuilder {
    private val graph = RenderGraph()

    fun addPass(pass: RenderPass): RenderGraphBuilder {
        graph.addPass(pass)
        return this
    }

    fun build(): RenderGraph = graph
}
