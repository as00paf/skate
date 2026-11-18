package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.render.preview.SkinnedPreviewViewport
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
import com.pafoid.skate.engine.render.renderer.passes.SceneFrame
import com.pafoid.skate.engine.render.renderer.passes.TransparentPass
import com.pafoid.skate.engine.render.renderer.passes.UiPass
import com.pafoid.skate.engine.render.renderer.passes.WaterPass
import com.pafoid.skate.engine.render.renderer.passes.TerrainPass
import com.pafoid.skate.engine.render.terrain.TerrainRenderer
import com.pafoid.skate.engine.render.terrain.TerrainScatterRenderer
import com.pafoid.skate.engine.render.terrain.RgbaImage
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.water.WaterRenderer
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

    private val imageDecoder = com.pafoid.skate.engine.assets.loaders.TextureLoader()

    /** Decodes a terrain layer texture to RGBA8 on the CPU (null when it can't be read). */
    private fun terrainImage(path: String): RgbaImage? = runCatching {
        val data = imageDecoder.decodeFromFile(assetsManager.paths.localPath(path))
        try {
            val bytes = ByteArray(data.width * data.height * 4)
            data.pixels.get(0, bytes)
            RgbaImage(data.width, data.height, bytes)
        } finally {
            imageDecoder.discard(data)
        }
    }.getOrNull()

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
        val modelRenderer = ModelRenderer(debugRenderer, vaoLoader = vaoLoader)
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
            thumbnail = thumbnailRenderer,
            // Its own variant of the mesh shader, so the preview's camera and studio light never reach the scene's program
            skinnedPreview = SkinnedPreviewViewport(
                shader = { assetsManager.getShaderVariant(EngineDefaults.Shaders.DEFAULT_3D, setOf(ShaderFeature.SKINNED)) },
                modelRenderer = modelRenderer,
            ),
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

        val shadowFrame = ShadowFrame()
        val sceneFrame = SceneFrame(
            lightingUniformsLoader = lightingUniformsLoader,
            shadowFrame = shadowFrame,
            environmentProbe = EnvironmentProbe(assetsManager.getShader(EngineDefaults.Shaders.IBL_CAPTURE)),
        )
        // Water (REN-29 §6): shaders resolved on use, so project overrides and hot reloads apply
        lateinit var postProcessSettings: () -> com.pafoid.skate.engine.render.renderer.passes.PostProcessSettings
        val waterPass = WaterPass(
            shader = { assetsManager.getShader(EngineDefaults.Shaders.WATER) },
            pickingShader = { assetsManager.getShaderVariant(EngineDefaults.Shaders.WATER, setOf(ShaderFeature.PICKING)) },
            renderer = WaterRenderer(
                readText = { path -> assetsManager.readText(path)?.ifEmpty { null } },
                resolveTexture = assetsManager::getTexture,
            ),
            frameBuffer = frameBuffer,
            sceneFrame = sceneFrame,
            cameraManager = cameraManager,
            ssrSteps = { postProcessSettings().ssrSteps },
            debugRenderer = renderers.debug,
        )

        // Terrain (REN-28 §4): colour after geometry, ids in the picking pass, a caster in the shadow pass
        val terrainPass = TerrainPass(
            shader = { assetsManager.getShader(EngineDefaults.Shaders.TERRAIN) },
            pickingShader = { assetsManager.getShaderVariant(EngineDefaults.Shaders.TERRAIN, setOf(ShaderFeature.PICKING)) },
            depthShader = { assetsManager.getShaderVariant(EngineDefaults.Shaders.TERRAIN, setOf(ShaderFeature.DEPTH_ONLY)) },
            renderer = TerrainRenderer(
                material = { path -> runCatching { assetsManager.readText(path)?.takeIf { it.isNotBlank() }?.let(MaterialAsset::fromJson) }.getOrNull() },
                decode = { path -> terrainImage(path) },
            ),
            frameBuffer = frameBuffer,
            sceneFrame = sceneFrame,
            cameraManager = cameraManager,
            useFbo = useFbo,
            scatter = TerrainScatterRenderer(
                modelRenderer = renderers.model,
                loadModel = { path -> assetsManager.loadModel(path) },
                loadMaterial = { path ->
                    assetsManager.readText(path)?.takeIf { it.isNotBlank() }?.let { MaterialAsset.fromJson(it).toMaterial(assetsManager::getTexture) }
                },
            ),
            scatterShader = { assetsManager.getShaderVariant(EngineDefaults.Shaders.DEFAULT_3D, setOf(ShaderFeature.INSTANCED)) },
            scatterPickingShader = { assetsManager.getShaderVariant(EngineDefaults.Shaders.PICKING_3D, setOf(ShaderFeature.INSTANCED)) },
            scatterShadowShader = { assetsManager.getShaderVariant(EngineDefaults.Shaders.SHADOW, setOf(ShaderFeature.INSTANCED)) },
        )
        renderers.shadow.sources += terrainPass.shadowCaster

        val pickingPass = PickingPass(
            pickingTexture = pickingTexture,
            pickingShader3D = shaders.picking3D,
            pickingRenderer = pickingRenderer,
            renderer2D = renderer2D,
            pickingShader = shaders.picking,
            modelRenderer = renderers.model,
            cameraManager = cameraManager,
            extraPicking = { scene -> terrainPass.drawPicking(scene); waterPass.drawPicking(scene) },
        )

        val geometryPass = GeometryPass(
            defaultShader = shaders.default,
            batchShader = shaders.batch,
            modelRenderer = renderers.model,
            renderer2D = renderer2D,
            skyDomeRenderer = renderers.skyDome,
            frameBuffer = frameBuffer,
            sceneFrame = sceneFrame,
            useFbo = useFbo,
            cameraManager = cameraManager,
            // Custom material shaders (AST-26 §5.6): colour pass only; null falls back to the schema shader
            materialShader = { reference -> assetsManager.materialShader(reference) },
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
        postProcessSettings = postProcessPass::getSettings

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
            terrain = terrainPass,
            water = waterPass,
            transparent = TransparentPass(renderers.model, frameBuffer, sceneFrame, useFbo),
            sceneFrame = sceneFrame,
            debug = debugPass,
            shadow = shadowPass,
            postProcess = postProcessPass,
            ui = uiPass,
            particles = particlePass,
        )
    }
}
