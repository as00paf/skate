package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.graph.RenderGraphBuilder
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.PickingRenderer
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.render.renderer.ShadowRenderer
import com.pafoid.skate.engine.render.renderer.SkyDomeRenderer
import com.pafoid.skate.engine.render.renderer.SkyboxRenderer
import com.pafoid.skate.engine.render.renderer.SplashRenderer
import com.pafoid.skate.engine.render.renderer.ThumbnailRenderer
import com.pafoid.skate.engine.render.renderer.passes.DebugPass
import com.pafoid.skate.engine.render.renderer.passes.GeometryPass
import com.pafoid.skate.engine.render.renderer.passes.PickingPass
import com.pafoid.skate.engine.render.renderer.passes.RenderPass
import com.pafoid.skate.engine.render.renderer.passes.ShadowPass
import com.pafoid.skate.engine.render.utils.GLStateTracker

/**
 * Factory for creating all rendering resources.
 */
class RenderResourcesFactory(
    private val assetsManager: AssetsManager,
    private val cameraManager: CameraManager,
    private val logger: LoggerService,
) {

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
        val renderers = createRenderers(shaders, assetsManager)

        logger.log("Creating render passes...")
        val shadowMap = ShadowMap.createWithBestResolution(4096)
        logger.log("Shadow map resolution: ${shadowMap.width}x${shadowMap.height}")
        shadowMap.initialize()
        val renderPasses = createRenderPasses(
            shaders = shaders,
            renderers = renderers,
            frameBuffer = frameBuffer,
            pickingTexture = pickingTexture,
            shadowMap = shadowMap
        )

        logger.log("Building render graph...")
        val renderGraph = RenderGraphBuilder()
            .addPass(renderPasses.shadow)
            .addPass(renderPasses.picking)
            .addPass(renderPasses.geometry)
            .addPass(renderPasses.debug)
            .build()

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

    private suspend fun loadShaders(): Shaders {
        val shaders = listOf<suspend () -> Shader>(
            { assetsManager.getShader(Assets.Shaders.DEBUG) },
            { assetsManager.getShader(Assets.Shaders.SHADER_3D_DEFAULT) },
            { assetsManager.getShader(Assets.Shaders.SHADER_2D_BATCH) },
            { assetsManager.getShader(Assets.Shaders.PICKING) },
            { assetsManager.getShader(Assets.Shaders.PICKING_3D) },
            { assetsManager.getShader(Assets.Shaders.SKYBOX) },
            { assetsManager.getShader(Assets.Shaders.SKY_DOME) },
            { assetsManager.getShader(Assets.Shaders.SHADOW) },
            { assetsManager.getShader(Assets.Shaders.SPLASH) },
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
        assetsManager: AssetsManager
    ): Renderers {
        val vaoLoader = assetsManager.vaoLoader

        val skyboxRenderer = SkyboxRenderer(shaders.skybox, vaoLoader)
        val skyDomeRenderer = SkyDomeRenderer(shaders.skyDome, vaoLoader)
        val shadowRenderer = ShadowRenderer(shaders.shadow, assetsManager)
        val debugRenderer = DebugRenderer(assetsManager, cameraManager, logger)
        val modelRenderer = ModelRenderer(assetsManager, debugRenderer)
        val splashRenderer = SplashRenderer(vaoLoader)
        splashRenderer.initialize()

        val thumbnailRenderer = ThumbnailRenderer(assetsManager, modelRenderer)

        return Renderers(
            skybox = skyboxRenderer,
            skyDome = skyDomeRenderer,
            model = modelRenderer,
            shadow = shadowRenderer,
            splash = splashRenderer,
            debug = debugRenderer,
            thumbnail = thumbnailRenderer
        )
    }

    private fun createRenderPasses(
        shaders: Shaders,
        renderers: Renderers,
        frameBuffer: FrameBuffer,
        pickingTexture: PickingTexture,
        shadowMap: ShadowMap? = null
    ): RenderPasses {
        val renderer2D = Renderer2D()
        val pickingRenderer = PickingRenderer(assetsManager, logger, cameraManager.camera)
        val lightingUniformsLoader = LightingUniformsLoader()

        renderer2D.bindShader(shaders.batch)

        val pickingPass = PickingPass(
            pickingTexture = pickingTexture,
            pickingShader3D = shaders.picking3D,
            pickingRenderer = pickingRenderer,
            renderer2D = renderer2D,
            pickingShader = shaders.picking,
            modelRenderer = renderers.model,
            cameraManager = cameraManager
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
            cameraManager = cameraManager,
            shadowMapTextureId = shadowMapTextureId,
            shadowMapResolution = shadowMapRes
        )
        val debugPass = DebugPass(renderers.debug, cameraManager)

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
