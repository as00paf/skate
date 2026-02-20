package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.NonPickable
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.PickingTexture
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.ShaderConst.Attribs
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.lwjgl.opengl.GL30.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL30.GL_CULL_FACE
import org.lwjgl.opengl.GL30.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL30.GL_DEPTH_TEST
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_LESS
import org.lwjgl.opengl.GL30.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL30.GL_TEXTURE0
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D
import org.lwjgl.opengl.GL30.glActiveTexture
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBindTexture
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glClear
import org.lwjgl.opengl.GL30.glClearColor
import org.lwjgl.opengl.GL30.glDepthFunc
import org.lwjgl.opengl.GL30.glDepthMask
import org.lwjgl.opengl.GL30.glDisable
import org.lwjgl.opengl.GL30.glEnable
import org.lwjgl.opengl.GL30.glUseProgram
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

    // Window dimensions (To be moved to Renderer later)
    var currentWidth = 0
    var currentHeight = 0

    fun initFrameBuffer() {
        pickingTexture = PickingTexture(1920, 1080)
        frameBuffer = FrameBuffer(currentWidth, currentHeight) //width and height must be set before
        frameBuffer.initialize()
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
    }

    private fun loadProjectionMatrix(camera: Camera) {
        defaultShader.start()
        defaultShader.uploadMat4f(Attribs.PROJECTION_MATRIX, camera.createProjectionMatrix())
    }

    private fun loadViewMatrix(camera: Camera) {
        defaultShader.start()
        defaultShader.uploadMat4f(Attribs.VIEW_MATRIX, camera.createViewMatrix())
    }

    override fun render(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) {
        EngineStats.resetDrawCalls()
        debugRenderer.beginFrame()
        pickingRenderer.beginFrame()
        
        // 1. Picking Pass
        pickingTexture.resize(currentWidth, currentHeight)
        pickingTexture.enableWriting()
        glViewport(0, 0, currentWidth, currentHeight)
        
        // CRITICAL: Reset state that might have been changed by ImGui or previous passes
        glDisable(GL_SCISSOR_TEST)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LESS)
        glDisable(GL_CULL_FACE)
        
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        
        renderer2D.bindCamera(scene.camera)
        render2D(scene, pickingShader)
        render3DPicking(scene, activeGameObject)
        pickingRenderer.draw()
        
        pickingTexture.disableWriting()

        // 2. Regular Pass
        if (useFbo) {
            frameBuffer.bind()
            glViewport(0, 0, frameBuffer.width, frameBuffer.height)
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            glViewport(0, 0, currentWidth, currentHeight)
        }
        
        clearColor(scene.sceneData.skyColor)

        val camera = scene.camera
        val light = scene.sceneData.light

        // 2D Rendering Setup
        renderer2D.bindCamera(camera)

        // 3D Rendering Setup
        loadProjectionMatrix(camera)
        loadViewMatrix(camera)

        // Upload lighting uniforms
        defaultShader.start()
        lightingUniformsLoader.loadLightingUniforms(defaultShader, camera, scene.sceneData)

        scene.gameObjectManager.gameObjects.forEach { go ->
            val renderComponent = go.getComponent<RenderComponent>()
            val transformComponent = go.getComponent<Transform>()
            if (renderComponent != null && transformComponent != null) {
                // Hover & Selected states
                var selectionState = 0.0f
                if (go == activeGameObject) selectionState = 1.0f
                else if (go == hoveredGameObject) selectionState = 2.0f

                defaultShader.uploadFloat(Uniforms.SELECTED, selectionState)

                val skeletonComponent = go.getComponent<SkeletonComponent>()
                val camera = sceneManager.currentScene?.camera
                val cameraPosition = camera?.position ?: Vector3f(0f, 0f, 0f)
                
                modelRenderer.render(
                    go = go,
                    transform = transformComponent,
                    renderComponent = renderComponent,
                    defaultShader = defaultShader,
                    cameraPosition = cameraPosition,
                    skeletonComponent = skeletonComponent
                )
            }
        }

        defaultShader.stop()
        
        // Render 2D
        render2D(scene, batchShader)
        
        // Render Skybox / Dome
        skyDomeRenderer.render(camera, scene)

        // 3. Debug Pass
        debugRenderer.draw()
        
        if (useFbo) {
            frameBuffer.unbind()
        }
        
        // Final state cleanup
        glUseProgram(0)
        glBindVertexArray(0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, 0)
        
        // Final screen viewport reset
        glViewport(0, 0, currentWidth, currentHeight)
    }

    private fun render3DPicking(scene: Scene, activeGameObject: GameObject?) {
        if(activeGameObject != null) return
        val camera = scene.camera

        pickingShader3D.start()
        pickingShader3D.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.createProjectionMatrix())
        pickingShader3D.uploadMat4f(Uniforms.VIEW_MATRIX, camera.createViewMatrix())

        scene.gameObjectManager.gameObjects.forEach { go ->
            val renderComponent = go.getComponent<RenderComponent>()
            val transform = go.getComponent<Transform>()
            if (renderComponent != null && transform != null && go.getComponent<NonPickable>() == null) {
                val skeletonComponent = go.getComponent<SkeletonComponent>()
                
                pickingShader3D.uploadFloat(Uniforms.ENTITY_ID, go.getUid().toFloat() + 1)
                pickingShader3D.uploadBoolean(Uniforms.USE_BATCH, false)

                modelRenderer.renderSimple(
                    go = go,
                    transform = transform,
                    renderComponent = renderComponent,
                    shader = pickingShader3D,
                    skeletonComponent = skeletonComponent
                )
            }
        }
        pickingShader3D.stop()
    }

    private fun render2D(scene: Scene, shader: Shader) {
        renderer2D.bindShader(shader)
        renderer2D.bindCamera(scene.camera)

        scene.gameObjectManager.gameObjects.forEach { go ->
            go.getComponent<SpriteRenderer>()?.let { sprite ->
                renderer2D.add(go)
            }
        }
        renderer2D.render()
        renderer2D.clear()
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
        defaultShader.destroy()
        batchShader.destroy()
        skyboxShader.destroy()
        pickingShader.destroy()
        pickingShader3D.destroy()
    }

}

