package com.pafoid.skate.engine.render

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.PickingRenderer
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.render.renderer.SkyDomeRenderer
import com.pafoid.skate.engine.render.renderer.SkyboxRenderer
import com.pafoid.skate.engine.render.renderer.passes.DebugPass
import com.pafoid.skate.engine.render.renderer.passes.GeometryPass
import com.pafoid.skate.engine.render.renderer.passes.PickingPass
import com.pafoid.skate.engine.render.utils.GLStateTracker

/**
 * Factory for creating all rendering resources.
 *
 * This factory centralizes the initialization logic for the entire rendering pipeline,
 * ensuring proper initialization order and dependency injection.
 *
 * @param resourceManager Loads and manages shader and texture assets
 * @param sceneManager Provides access to the current scene
 * @param logger Logs initialization progress and errors
 * @param vaoLoader Loads vertex array objects for GPU rendering
 * @param debugRenderer Shared debug renderer for physics and other debug visualization
 */
class RenderResourcesFactory(
    private val resourceManager: ResourceManager,
    private val sceneManager: SceneManager,
    private val logger: LoggerService,
    private val vaoLoader: VAOLoader,
    private val debugRenderer: DebugRenderer
) {
    /**
     * Creates all rendering resources.
     *
     * **Important**: This method requires an active OpenGL context to be current on the
     * calling thread. It will crash if called before the OpenGL context is created.
     *
     * ## Initialization Order
     *
     * This method performs the following initialization in order:
     * 1. Initializes OpenGL state tracker (queries GL state - requires context)
     * 2. Creates framebuffer and picking texture (allocates GL resources)
     * 3. Loads all shaders (compiles GLSL programs)
     * 4. Creates all renderer instances
     * 5. Creates all render passes
     *
     * @param width Initial viewport width
     * @param height Initial viewport height
     * @return Fully initialized RenderResources container
     * @throws IllegalStateException if called without an active OpenGL context
     */
    suspend fun create(width: Int, height: Int): RenderResources {
        logger.logEngine("Initializing OpenGL state tracker...")
        GLStateTracker.initialize()

        logger.logEngine("Creating framebuffer and picking texture...")
        val frameBuffer = FrameBuffer(width, height)
        frameBuffer.initialize()

        val pickingTexture = PickingTexture(width, height)

        logger.logEngine("Loading shaders...")
        val shaders = loadShaders()

        logger.logEngine("Creating renderer instances...")
        val renderers = createRenderers(shaders, resourceManager)

        logger.logEngine("Creating render passes...")
        // Create shadow map with highest supported resolution up to 4096x4096
        val shadowMap = ShadowMap.createWithBestResolution(4096)
        logger.logEngine("Shadow map resolution: ${shadowMap.width}x${shadowMap.height}")
        shadowMap.initialize()
        val renderPasses = createRenderPasses(
            shaders = shaders,
            renderers = renderers,
            frameBuffer = frameBuffer,
            pickingTexture = pickingTexture,
            width = width,
            height = height,
            shadowMap = shadowMap
        )

        logger.logEngine("Render resources initialization complete.")

        return RenderResources(
            shaders = shaders,
            frameBuffer = frameBuffer,
            pickingTexture = pickingTexture,
            renderers = renderers,
            renderPasses = renderPasses,
            shadowMap = shadowMap
        )
    }

    /**
     * Loads all shader programs.
     */
    private suspend fun loadShaders(): Shaders {
        val shaders = listOf<suspend () -> Shader>(
            { resourceManager.loadShader(Assets.Shaders.DEBUG) },
            { resourceManager.loadShader(Assets.Shaders.SHADER_3D_DEFAULT) },
            { resourceManager.loadShader(Assets.Shaders.SHADER_2D_BATCH) },
            { resourceManager.loadShader(Assets.Shaders.PICKING) },
            { resourceManager.loadShader(Assets.Shaders.PICKING_3D) },
            { resourceManager.loadShader(Assets.Shaders.SKYBOX) },
            { resourceManager.loadShader(Assets.Shaders.SKY_DOME) },
        )

        logger.logEngine("Loading shader 1/7: Debug")
        val debugShader = shaders[0].invoke()

        logger.logEngine("Loading shader 2/7: Default 3D")
        val defaultShader = shaders[1].invoke()

        logger.logEngine("Loading shader 3/7: 2D Batch")
        val batchShader = shaders[2].invoke()

        logger.logEngine("Loading shader 4/7: Picking")
        val pickingShader = shaders[3].invoke()

        logger.logEngine("Loading shader 5/7: Picking 3D")
        val picking3DShader = shaders[4].invoke()

        logger.logEngine("Loading shader 6/7: Skybox")
        val skyboxShader = shaders[5].invoke()

        logger.logEngine("Loading shader 7/7: Sky Dome")
        val skyDomeShader = shaders[6].invoke()

        return Shaders(
            default = defaultShader,
            debug = debugShader,
            batch = batchShader,
            picking = pickingShader,
            picking3D = picking3DShader,
            skybox = skyboxShader,
            skyDome = skyDomeShader
        )
    }

    /**
     * Creates all renderer instances.
     */
    private fun createRenderers(
        shaders: Shaders,
        resourceManager: ResourceManager
    ): Renderers {
        val skyboxRenderer = SkyboxRenderer(shaders.skybox, vaoLoader)
        val skyDomeRenderer = SkyDomeRenderer(shaders.skyDome, vaoLoader, resourceManager)
        val modelRenderer = ModelRenderer(resourceManager, debugRenderer)

        return Renderers(
            skybox = skyboxRenderer,
            skyDome = skyDomeRenderer,
            model = modelRenderer
        )
    }

    /**
     * Creates all render passes with their dependencies.
     */
    private fun createRenderPasses(
        shaders: Shaders,
        renderers: Renderers,
        frameBuffer: FrameBuffer,
        pickingTexture: PickingTexture,
        width: Int,
        height: Int,
        shadowMap: ShadowMap? = null
    ): RenderPasses {
        val renderer2D = Renderer2D()
        val pickingRenderer = PickingRenderer(resourceManager, logger, sceneManager)
        val lightingUniformsLoader = LightingUniformsLoader()

        // Bind initial shader and camera for renderer2D
        // (will be rebound each frame)
        renderer2D.bindShader(shaders.batch)

        val pickingPass = PickingPass(
            pickingTexture = pickingTexture,
            pickingShader3D = shaders.picking3D,
            pickingRenderer = pickingRenderer,
            renderer2D = renderer2D,
            pickingShader = shaders.picking,
            modelRenderer = renderers.model
        )

        val shadowMapTextureId = shadowMap?.getDepthTextureId() ?: 0
        val shadowMapRes = shadowMap?.width?.toFloat() ?: 2048f
        val geometryPass = GeometryPass(
            defaultShader = shaders.default,
            batchShader = shaders.batch,
            modelRenderer = renderers.model,
            renderer2D = renderer2D,
            skyDomeRenderer = renderers.skyDome,
            frameBuffer = frameBuffer,
            lightingUniformsLoader = lightingUniformsLoader,
            getUseFbo = { true }, // Default to FBO, can be made configurable
            sceneManager = sceneManager,
            shadowMapTextureId = shadowMapTextureId,
            shadowMapResolution = shadowMapRes
        )

        val debugPass = DebugPass(debugRenderer)

        return RenderPasses(
            picking = pickingPass,
            geometry = geometryPass,
            debug = debugPass
        )
    }
}
