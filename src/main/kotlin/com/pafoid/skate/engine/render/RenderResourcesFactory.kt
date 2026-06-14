package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.graph.RenderGraph
import com.pafoid.skate.engine.render.graph.RenderGraphBuilder
import com.pafoid.skate.engine.render.graph.RenderResource
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.PickingRenderer
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.render.renderer.ShadowRenderer
import com.pafoid.skate.engine.render.renderer.SkyDomeRenderer
import com.pafoid.skate.engine.render.renderer.SkyboxRenderer
import com.pafoid.skate.engine.render.renderer.SplashRenderer
import com.pafoid.skate.engine.render.renderer.passes.DebugPass
import com.pafoid.skate.engine.render.renderer.passes.GeometryPass
import com.pafoid.skate.engine.render.renderer.passes.PickingPass
import com.pafoid.skate.engine.render.renderer.passes.RenderPass
import com.pafoid.skate.engine.render.renderer.passes.ShadowPass
import com.pafoid.skate.engine.render.utils.GLStateTracker
import org.koin.core.component.KoinComponent

/**
 * Factory for creating all rendering resources.
 */
class RenderResourcesFactory(
    private val resourceManager: ResourceManager,
    private val sceneManager: SceneManager,
    private val logger: LoggerService,
    private val vaoLoader: VAOLoader,
    private val debugRenderer: DebugRenderer,
    private val modelRenderer: ModelRenderer,
    private val splashRenderer: SplashRenderer,
    private val cameraManager: CameraManager
) : KoinComponent {

    suspend fun create(width: Int, height: Int): RenderResources {
        logger.log("Initializing OpenGL state tracker...")
        GLStateTracker.initialize()

        logger.log("Creating framebuffer and picking texture...")
        val frameBuffer = FrameBuffer(width, height)
        frameBuffer.initialize()

        val pickingTexture = PickingTexture(width, height)

        logger.log("Loading shaders...")
        val shaders = loadShaders()

        logger.log("Creating renderer instances...")
        val renderers = createRenderers(shaders, resourceManager)

        logger.log("Initializing splash renderer...")
        splashRenderer.initialize()

        logger.log("Creating render passes...")
        val shadowMap = ShadowMap.createWithBestResolution(4096)
        logger.log("Shadow map resolution: ${shadowMap.width}x${shadowMap.height}")
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

        logger.log("Building render graph...")
        val renderGraph = buildRenderGraph(renderPasses, shadowMap)

        logger.log("Render resources initialization complete.")

        return RenderResources(
            shaders = shaders,
            frameBuffer = frameBuffer,
            pickingTexture = pickingTexture,
            renderers = renderers,
            renderPasses = renderPasses,
            renderGraph = renderGraph,
            shadowMap = shadowMap
        )
    }

    private fun buildRenderGraph(
        passes: RenderPasses,
        shadowMap: ShadowMap?
    ): RenderGraph {
        val builder = RenderGraphBuilder()
        if (shadowMap != null) {
            builder.withResources(RenderResource.Texture("ShadowMap", shadowMap.getDepthTextureId()))
        }
        return builder
            .addPass(passes.shadow)
            .addPass(passes.picking)
            .addPass(passes.geometry)
            .addPass(passes.debug)
            .build()
    }

    private suspend fun loadShaders(): Shaders {
        val shaders = listOf<suspend () -> Shader>(
            { resourceManager.loadShader(Assets.Shaders.DEBUG) },
            { resourceManager.loadShader(Assets.Shaders.SHADER_3D_DEFAULT) },
            { resourceManager.loadShader(Assets.Shaders.SHADER_2D_BATCH) },
            { resourceManager.loadShader(Assets.Shaders.PICKING) },
            { resourceManager.loadShader(Assets.Shaders.PICKING_3D) },
            { resourceManager.loadShader(Assets.Shaders.SKYBOX) },
            { resourceManager.loadShader(Assets.Shaders.SKY_DOME) },
            { resourceManager.loadShader(Assets.Shaders.SHADOW) },
            { resourceManager.loadShader(Assets.Shaders.SPLASH) },
        )

        return Shaders(
            default = shaders[1].invoke(),
            debug = shaders[0].invoke(),
            batch = shaders[2].invoke(),
            picking = shaders[3].invoke(),
            picking3D = shaders[4].invoke(),
            skybox = shaders[5].invoke(),
            skyDome = shaders[6].invoke(),
            shadow = shaders[7].invoke(),
            splash = shaders[8].invoke()
        )
    }

    private fun createRenderers(
        shaders: Shaders,
        resourceManager: ResourceManager
    ): Renderers {
        val skyboxRenderer = SkyboxRenderer(shaders.skybox, vaoLoader)
        val skyDomeRenderer = SkyDomeRenderer(shaders.skyDome, vaoLoader, resourceManager)
        val shadowRenderer = ShadowRenderer(shaders.shadow, resourceManager)

        return Renderers(
            skybox = skyboxRenderer,
            skyDome = skyDomeRenderer,
            model = modelRenderer,
            shadow = shadowRenderer,
            splash = splashRenderer
        )
    }

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
        val pickingRenderer = PickingRenderer(resourceManager, logger, cameraManager.editorCamera)
        val lightingUniformsLoader = LightingUniformsLoader()

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
            getUseFbo = { true },
            sceneManager = sceneManager,
            cameraManager = cameraManager,
            shadowMapTextureId = shadowMapTextureId,
            shadowMapResolution = shadowMapRes
        )
        val debugPass = DebugPass(debugRenderer)

        val shadowPass = if (shadowMap != null) {
            ShadowPass(
                shadowRenderer = renderers.shadow,
                shadowMap = shadowMap,
                logger = logger
            )
        } else {
            object : RenderPass {
                override var executionTimeNs: Long = 0
                override var isEnabled: Boolean = true
            }
        }

        return RenderPasses(
            picking = pickingPass,
            geometry = geometryPass,
            debug = debugPass,
            shadow = shadowPass
        )
    }
}
