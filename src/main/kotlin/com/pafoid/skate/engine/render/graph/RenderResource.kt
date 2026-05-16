package com.pafoid.skate.engine.render.graph

/**
 * Base class for all resources in the render graph (textures, framebuffers, etc.).
 * 
 * Render resources are used as inputs and outputs for [RenderPass] nodes.
 */
sealed class RenderResource {
    /**
     * Unique name for this resource in the graph.
     */
    abstract val name: String
    
    /**
     * A texture resource (e.g., shadow map, color attachment).
     */
    data class Texture(override val name: String, val textureId: Int) : RenderResource()
    
    /**
     * A framebuffer resource.
     */
    data class Buffer(override val name: String, val bufferId: Int) : RenderResource()
    
    /**
     * A generic value resource (e.g., resolution, state flags).
     */
    data class Value<T>(override val name: String, val value: T) : RenderResource()
}
