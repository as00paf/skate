package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.render.graph.RenderGraph
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.ShadowRenderer
import com.pafoid.skate.engine.render.renderer.SkyDomeRenderer
import com.pafoid.skate.engine.render.renderer.SkyboxRenderer
import com.pafoid.skate.engine.render.renderer.SplashRenderer
import com.pafoid.skate.engine.render.renderer.passes.DebugPass
import com.pafoid.skate.engine.render.renderer.passes.GeometryPass
import com.pafoid.skate.engine.render.renderer.passes.PickingPass
import com.pafoid.skate.engine.render.renderer.passes.RenderPass

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
 * @param shadow The shadow mapping shader for depth-only rendering
 * @param splash The splash screen shader
 */
data class Shaders(
    val default: Shader,
    val debug: Shader,
    val batch: Shader,
    val picking: Shader,
    val picking3D: Shader,
    val skybox: Shader,
    val skyDome: Shader,
    val shadow: Shader,
    val splash: Shader
)

/**
 * Container for all renderer instances.
 *
 * @param skybox Renders the cube map skybox
 * @param skyDome Renders the HDRI sky dome
 * @param model Renders 3D meshes with PBR materials
 * @param shadow Renders shadow-casting objects to shadow map
 * @param splash Renders the splash screen quad
 */
data class Renderers(
    val skybox: SkyboxRenderer,
    val skyDome: SkyDomeRenderer,
    val model: ModelRenderer,
    val shadow: ShadowRenderer,
    val splash: SplashRenderer
)

/**
 * Container for all render passes in the pipeline.
 *
 * @param picking Renders object IDs for mouse selection
 * @param geometry Renders the full scene with PBR shading
 * @param debug Renders debug visualization on top
 * @param shadow Renders depth-only shadow map pass
 */
data class RenderPasses(
    val picking: PickingPass,
    val geometry: GeometryPass,
    val debug: DebugPass,
    val shadow: RenderPass
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
 * - ShadowMap: Depth texture for shadow mapping
 * - RenderGraph: Orchestrator for the rendering pipeline
 *
 * @param shaders All shader programs
 * @param frameBuffer The framebuffer for FBO rendering
 * @param pickingTexture The picking render target
 * @param renderers All renderer instances
 * @param renderPasses All render passes
 * @param shadowMap Optional shadow map for shadow mapping
 * @param renderGraph The render graph for pass execution
 */
data class RenderResources(
    val shaders: Shaders,
    val frameBuffer: FrameBuffer,
    val pickingTexture: PickingTexture,
    val renderers: Renderers,
    val renderPasses: RenderPasses,
    val renderGraph: RenderGraph,
    val shadowMap: ShadowMap? = null
)
