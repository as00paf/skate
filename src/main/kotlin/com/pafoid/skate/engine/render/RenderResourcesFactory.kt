package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.graph.RenderGraphBuilder
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.lighting.EnvironmentProbe
import com.pafoid.skate.engine.render.renderer.GridRenderer
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
import com.pafoid.skate.engine.render.renderer.passes.NoShadowPass
import com.pafoid.skate.engine.render.renderer.passes.ParticlePass
import com.pafoid.skate.engine.render.renderer.passes.PickingPass
import com.pafoid.skate.engine.render.renderer.passes.PostProcessPass
import com.pafoid.skate.engine.render.renderer.passes.PresentedViewport
import com.pafoid.skate.engine.render.renderer.passes.ShadowPass
import com.pafoid.skate.engine.render.renderer.passes.UiPass
import com.pafoid.skate.engine.render.shader.ShaderFeature
import com.pafoid.skate.engine.render.shadow.ShadowFrame
import com.pafoid.skate.engine.render.shadow.ShadowSettings
import com.pafoid.skate.engine.render.rhi.RhiContext

class RenderResourcesFactory(
    engine: Engine,
) {
    private val logger: LoggerService = engine.logger
    private val assetsManager: AssetsManager = engine.assetsManager
    private val cameraManager: CameraManager = engine.cameraManager

    private var useFbo: Boolean = true

    fun create(width: Int, height: Int, useFbo: Boolean = true): RenderResources {
        this.useFbo = useFbo

        logger.log("Creating framebuffer and picking texture...")
        val frameBuffer = FrameBuffer(width, height)
        frameBuffer.initialize()

        val pickingTexture = PickingTexture(width, height)

        logger.log("Loading shaders...")
        val shaders = loadShaders()

        logger.log("Creating renderer instances...")
        val renderers = createRenderers(shaders, assetsManager)

        logger.log("Creating render passes...")
        val quality = ShadowSettings.quality
        val shadowMap = ShadowMap()
        shadowMap.ensure(quality.cascadeResolution, quality.cascadeCount)
        val localShadowMap = ShadowMap()
        logger.log("Shadow quality: $quality (cascades ${shadowMap.layers}x${shadowMap.resolution})")
        val renderPasses = createRenderPasses(
            shaders = shaders,
            renderers = renderers,
            frameBuffer = frameBuffer,
            pickingTexture = pickingTexture,
            shadowMap = shadowMap,
            localShadowMap = localShadowMap,
        )

        logger.log("Building render graph...")
        val renderGraph = RenderGraphBuilder()
            .apply { renderPasses.inExecutionOrder().forEach { addPass(it) } }
            .build()

        logger.log("Render resources initialization complete.")

        return RenderResources(
            shaders = shaders,
            frameBuffer = frameBuffer,
            pickingTexture = pickingTexture,
            renderers = renderers,
            renderPasses = renderPasses,
            renderGraph = renderGraph,
            shadowMap = shadowMap,
            localShadowMap = localShadowMap,
        )
    }

    private fun loadShaders(): Shaders {
        return Shaders(
            default = assetsManager.getShader(Assets.Shaders.SHADER_3D_DEFAULT),
            debug = assetsManager.getShader(Assets.Shaders.DEBUG),
            batch = assetsManager.getShader(Assets.Shaders.SHADER_2D_BATCH),
            picking = assetsManager.getShader(Assets.Shaders.PICKING),
            picking3D = assetsManager.getShader(Assets.Shaders.PICKING_3D),
            skybox = assetsManager.getShader(Assets.Shaders.SKYBOX),
            skyDome = assetsManager.getShader(Assets.Shaders.SKY_DOME),
            shadow = assetsManager.getShader(Assets.Shaders.SHADOW),
            splash = assetsManager.getShader(Assets.Shaders.SPLASH),
            texture3D = assetsManager.getShader(Assets.Shaders.SHADER_3D_TEXTURE),
        )
    }

    private fun createRenderers(
        shaders: Shaders,
        assetsManager: AssetsManager
    ): Renderers {
        val vaoLoader = assetsManager.vaoLoader

        val skyboxRenderer = SkyboxRenderer(shaders.skybox, vaoLoader)
        val skyDomeRenderer = SkyDomeRenderer(shaders.skyDome, vaoLoader)
        val shadowRenderer = ShadowRenderer(
            shaders.shadow,
            assetsManager.getShaderVariant(Assets.Shaders.SHADOW, setOf(ShaderFeature.ALPHA_TEST)),
        )
        val debugRenderer = DebugRenderer(shaders.debug, cameraManager)
        val modelRenderer = ModelRenderer(debugRenderer)
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
        shadowMap: ShadowMap? = null,
        localShadowMap: ShadowMap? = null,
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

        val shadowFrame = ShadowFrame()
        val geometryPass = GeometryPass(
            defaultShader = shaders.default,
            batchShader = shaders.batch,
            modelRenderer = renderers.model,
            renderer2D = renderer2D,
            skyDomeRenderer = renderers.skyDome,
            frameBuffer = frameBuffer,
            lightingUniformsLoader = lightingUniformsLoader,
            useFbo = useFbo,
            cameraManager = cameraManager,
            shadowFrame = shadowFrame,
            environmentProbe = EnvironmentProbe(assetsManager.getShader(EngineDefaults.Shaders.IBL_CAPTURE)),
        )
        // Debug visuals and the runtime UI draw into the presented target after post-processing
        val presentedViewport = PresentedViewport()
        val bindPresentedTarget = {
            RhiContext.require().commandList()
                .beginPass(if (useFbo) frameBuffer.target else null, viewport = presentedViewport.of(frameBuffer, useFbo))
        }
        val endPresentedTarget = { RhiContext.require().commandList().endPass() }
        val debugPass = DebugPass(
            renderers.debug, cameraManager,
            bindTarget = bindPresentedTarget, targetHeight = { frameBuffer.height }, endTarget = endPresentedTarget,
            grid = GridRenderer(assetsManager.getShader(EngineDefaults.Shaders.EDITOR_GRID), cameraManager),
        )
        val uiPass = UiPass(
            renderer2D = renderer2D,
            batchShader = shaders.batch,
            bindTarget = bindPresentedTarget,
            targetSize = { frameBuffer.width to frameBuffer.height },
            endTarget = endPresentedTarget,
        )
        val particlePass = ParticlePass(
            shader = assetsManager.getShader(EngineDefaults.Shaders.PARTICLES),
            frameBuffer = frameBuffer,
            useFbo = useFbo,
            cameraManager = cameraManager,
            texture = { path -> assetsManager.getTexture(path).rhi },
        )

        val postProcessPass = PostProcessPass(
            frameBuffer = frameBuffer,
            useFbo = useFbo,
            bloomShader = assetsManager.getShader(EngineDefaults.Shaders.POST_BLOOM),
            tonemapShader = assetsManager.getShader(EngineDefaults.Shaders.POST_TONEMAP),
            gammaShader = assetsManager.getShader(EngineDefaults.Shaders.POST_GAMMA),
            fxaaShader = assetsManager.getShader(EngineDefaults.Shaders.POST_FXAA),
            ssaoShader = assetsManager.getShader(EngineDefaults.Shaders.POST_SSAO),
            vaoLoader = assetsManager.vaoLoader
        )

        val shadowPass = if (shadowMap != null && localShadowMap != null) {
            ShadowPass(
                shadowRenderer = renderers.shadow,
                cascadeMap = shadowMap,
                localMap = localShadowMap,
                cameraManager = cameraManager,
                frame = shadowFrame,
            )
        } else {
            NoShadowPass()
        }

        return RenderPasses(
            picking = pickingPass,
            geometry = geometryPass,
            debug = debugPass,
            shadow = shadowPass,
            postProcess = postProcessPass,
            ui = uiPass,
            particles = particlePass,
        )
    }
}
