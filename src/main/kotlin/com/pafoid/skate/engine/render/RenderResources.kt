package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.render.renderer.DebugPass
import com.pafoid.skate.engine.render.renderer.GeometryPass
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.PickingPass
import com.pafoid.skate.engine.render.renderer.SkyDomeRenderer
import com.pafoid.skate.engine.render.renderer.SkyboxRenderer

/**
 * Container for all shader resources used by the rendering pipeline.
 *
 * @param default The main 3D PBR shader for mesh rendering
 * @param debug The debug visualization shader for lines and shapes
 * @param batch The 2D sprite batch shader
 * @param picking The 2D picking shader for sprite selection
 * @param picking3D The 3D picking shader for mesh selection
 * @param skybox The cube map skybox shader
 * @param skyDome The HDRI sky dome shader
 */
data class Shaders(
    val default: Shader,
    val debug: Shader,
    val batch: Shader,
    val picking: Shader,
    val picking3D: Shader,
    val skybox: Shader,
    val skyDome: Shader
)

/**
 * Container for all renderer instances.
 *
 * @param skybox Renders the cube map skybox
 * @param skyDome Renders the HDRI sky dome
 * @param model Renders 3D meshes with PBR materials
 */
data class Renderers(
    val skybox: SkyboxRenderer,
    val skyDome: SkyDomeRenderer,
    val model: ModelRenderer
)

/**
 * Container for all render passes in the pipeline.
 *
 * @param picking Renders object IDs for mouse selection
 * @param geometry Renders the full scene with PBR shading
 * @param debug Renders debug visualization on top
 */
data class RenderPasses(
    val picking: PickingPass,
    val geometry: GeometryPass,
    val debug: DebugPass
)

/**
 * Master container for all rendering resources.
 *
 * This data class groups together all resources needed for rendering:
 * - Shaders: All shader programs
 * - FrameBuffer: Off-screen render target for FBO rendering
 * - PickingTexture: Render target for object picking
 * - Renderers: Renderer instances for different object types
 * - RenderPasses: Organized render passes for the pipeline
 *
 * @param shaders All shader programs
 * @param frameBuffer The framebuffer for FBO rendering
 * @param pickingTexture The picking render target
 * @param renderers All renderer instances
 * @param renderPasses All render passes
 */
data class RenderResources(
    val shaders: Shaders,
    val frameBuffer: FrameBuffer,
    val pickingTexture: PickingTexture,
    val renderers: Renderers,
    val renderPasses: RenderPasses
)
