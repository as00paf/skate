package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.PickingTexture
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.utils.GLStateTracker
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.lwjgl.opengl.GL30.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL30.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL30.GL_DEPTH_TEST
import org.lwjgl.opengl.GL30.glClear
import org.lwjgl.opengl.GL30.glClearColor
import org.lwjgl.opengl.GL30.glEnable
import org.lwjgl.opengl.GL30.glViewport

class Renderer(
    private val debugRenderer: DebugRenderer,
    private val pickingRenderer: PickingRenderer,
    private val resourceManager: ResourceManager,
    private val sceneManager: SceneManager,
    private val vaoLoader: VAOLoader,
    private val logger: LoggerService,
) : IRenderer, KoinComponent {

    private lateinit var defaultShader: Shader
    private lateinit var debugShader: Shader
    private lateinit var batchShader: Shader
    private lateinit var pickingShader: Shader
    private lateinit var pickingShader3D: Shader
    private lateinit var skyboxShader: Shader
    private lateinit var skyDomeShader: Shader

    private val modelRenderer: ModelRenderer = ModelRenderer(resourceManager)
    private val lightingUniformsLoader: LightingUniformsLoader = LightingUniformsLoader()

    lateinit var frameBuffer: FrameBuffer
    override var useFbo = false // Default to false for initial feature tests

    private val renderer2D = Renderer2D()
    private lateinit var pickingTexture: PickingTexture

    private lateinit var skyboxRenderer: SkyboxRenderer
    private lateinit var skyDomeRenderer: SkyDomeRenderer

    // Render passes
    private lateinit var pickingPass: PickingPass
    private lateinit var geometryPass: GeometryPass
    private lateinit var debugPass: DebugPass

    // Window dimensions (To be moved to Renderer later)
    var currentWidth = 0
    var currentHeight = 0

    fun initFrameBuffer() {
        pickingTexture = PickingTexture(1920, 1080)
        frameBuffer = FrameBuffer(currentWidth, currentHeight) //width and height must be set before
        frameBuffer.initialize()

        // Initialize OpenGL state tracker after GL context is ready
        GLStateTracker.initialize()
    }

    private fun initializeRenderPasses() {
        pickingPass = PickingPass(
            pickingTexture = pickingTexture,
            pickingShader3D = pickingShader3D,
            pickingRenderer = pickingRenderer,
            renderer2D = renderer2D,
            pickingShader = pickingShader,
            modelRenderer = modelRenderer,
            getWindowWidth = { currentWidth },
            getWindowHeight = { currentHeight }
        )

        geometryPass = GeometryPass(
            defaultShader = defaultShader,
            batchShader = batchShader,
            modelRenderer = modelRenderer,
            renderer2D = renderer2D,
            skyDomeRenderer = skyDomeRenderer,
            frameBuffer = frameBuffer,
            lightingUniformsLoader = lightingUniformsLoader,
            getUseFbo = { useFbo },
            sceneManager = sceneManager,
            getWindowWidth = { currentWidth },
            getWindowHeight = { currentHeight }
        )

        debugPass = DebugPass(debugRenderer)
    }

    suspend fun loadShaders(updateProgress:(Int, Int) -> Unit) {
        val shaders = listOf<suspend ()->Unit>(
            { debugShader = resourceManager.loadShader(Assets.Shaders.DEBUG) },
            { defaultShader = resourceManager.loadShader(Assets.Shaders.SHADER_3D_DEFAULT) },
            { batchShader = resourceManager.loadShader(Assets.Shaders.SHADER_2D_BATCH) },
            { pickingShader = resourceManager.loadShader(Assets.Shaders.PICKING) },
            { pickingShader3D = resourceManager.loadShader(Assets.Shaders.PICKING_3D) },
            { skyboxShader = resourceManager.loadShader(Assets.Shaders.SKYBOX) },
            { skyDomeShader = resourceManager.loadShader(Assets.Shaders.SKY_DOME) },
        )

        shaders.forEachIndexed { index, function ->
            logger.logEngine("Loading shader ${index + 1}/${shaders.size}")
            function.invoke()
            updateProgress(index, shaders.size)
        }

        renderer2D.bindShader(batchShader)
        skyboxRenderer = SkyboxRenderer(skyboxShader, vaoLoader)
        skyDomeRenderer = SkyDomeRenderer(skyDomeShader, vaoLoader, resourceManager)

        // Initialize render passes after shaders and renderers are ready
        initializeRenderPasses()
    }

    override fun render(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) {
        EngineStats.resetDrawCalls()

        // Begin frame for debug and picking systems
        debugPass.beginFrame()
        pickingRenderer.beginFrame()

        // 1. Picking Pass - Render object IDs for mouse selection
        pickingPass.execute(scene, activeGameObject, hoveredGameObject)

        // 2. Geometry Pass - Render full scene with PBR shading
        geometryPass.execute(scene, activeGameObject, hoveredGameObject)
        geometryPass.unbind()
        geometryPass.cleanup()

        // 3. Debug Pass - Render debug visualization on top
        debugPass.execute(scene, activeGameObject, hoveredGameObject)

        // Final screen viewport reset
        glViewport(0, 0, currentWidth, currentHeight)
    }

    override fun readPixel(x: Int, y: Int): Int {
        val w = currentWidth
        val h = currentHeight

        // Clamp coordinates
        val safeX = x.coerceIn(0, w - 1)
        val safeY = y.coerceIn(0, h - 1)
        // Invert Y coordinate
        return pickingTexture.readPixel(safeX, h - 1 - safeY)
    }

    override fun clearColor(sky: Vector3f) {
        glEnable(GL_DEPTH_TEST)
        glClearColor(sky.x, sky.y, sky.z, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }

    override fun destroy() {
        // Destroy shaders
        defaultShader.destroy()
        batchShader.destroy()
        skyboxShader.destroy()
        pickingShader.destroy()
        pickingShader3D.destroy()
        debugShader.destroy()
        skyDomeShader.destroy()

        // Destroy renderers
        skyboxRenderer.destroy()
        skyDomeRenderer.destroy()
        renderer2D.destroy()

        // Destroy framebuffer (includes texture and depth buffer)
        frameBuffer.destroy()
    }
}

