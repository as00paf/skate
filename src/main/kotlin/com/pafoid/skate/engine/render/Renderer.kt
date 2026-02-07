package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.ShaderConst.Attribs
import com.pafoid.skate.engine.assets.ShaderConst.Uniforms
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.NonPickable
import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.scenes.components.toWorldMatrix
import com.pafoid.skate.engine.utils.EngineStats
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.lwjgl.opengl.GL30.*

class Renderer(
    private val debugDraw: DebugDraw,
    private val pickingDraw: PickingDraw,
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

    lateinit var frameBuffer: FrameBuffer
    override var useFbo = false // Default to false for initial feature tests

    private val renderer2D = Renderer2D()
    private val pickingTexture = PickingTexture(1920, 1080)

    private lateinit var skyboxRenderer:SkyboxRenderer
    private lateinit var skyDomeRenderer:SkyDomeRenderer

    fun initFrameBuffer(width: Int, height: Int){
        frameBuffer = FrameBuffer(width, height)
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
        debugDraw.beginFrame()
        pickingDraw.beginFrame()
        
        // 1. Picking Pass
        pickingTexture.resize(sceneManager.currentWidth, sceneManager.currentHeight)
        pickingTexture.enableWriting()
        glViewport(0, 0, sceneManager.currentWidth, sceneManager.currentHeight)
        
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
        pickingDraw.draw()
        
        pickingTexture.disableWriting()

        // 2. Regular Pass
        if (useFbo) {
            frameBuffer.bind()
            glViewport(0, 0, frameBuffer.width, frameBuffer.height)
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            glViewport(0, 0, sceneManager.currentWidth, sceneManager.currentHeight)
        }
        
        clearColor(scene.skyColor)
        
        val camera = scene.camera
        val light = scene.light
        // Offset light from camera slightly so we get some shading but plenty of light
        light.position.set(camera.position).add(5f, 5f, 10f)

        // 2D Rendering Setup
        renderer2D.bindCamera(camera)
        
        // 3D Rendering Setup
        loadProjectionMatrix(camera)
        loadViewMatrix(camera)

        defaultShader.start()
        defaultShader.uploadVec3f(Uniforms.LIGHT_POSITION, light.position)
        defaultShader.uploadVec3f(Uniforms.LIGHT_COLOR, Vector3f(1.5f, 1.5f, 1.5f)) // Brighter light
        
        val ambient = if (scene.useAmbient) scene.ambientLight else Vector3f(0f, 0f, 0f)
        defaultShader.uploadVec3f(Uniforms.AMBIENT_LIGHT, ambient)

        // Sun
        defaultShader.uploadVec3f(Uniforms.SUN_DIRECTION, scene.sun.direction)
        val finalSunColor = if (scene.useSun) Vector3f(scene.sun.color).mul(scene.sun.intensity) else Vector3f(0f, 0f, 0f)
        defaultShader.uploadVec3f(Uniforms.SUN_COLOR, finalSunColor)

        // Moon
        defaultShader.uploadVec3f(Uniforms.MOON_DIRECTION, scene.moon.direction)
        val finalMoonColor = Vector3f(scene.moon.color).mul(scene.moon.intensity)
        defaultShader.uploadVec3f(Uniforms.MOON_COLOR, finalMoonColor)

        // Fog
        defaultShader.uploadVec3f(Uniforms.FOG_COLOR, scene.fogColor)
        defaultShader.uploadFloat(Uniforms.FOG_DENSITY, scene.fogDensity)
        defaultShader.uploadFloat(Uniforms.FOG_GRADIENT, scene.fogGradient)

        scene.gameObjects.forEach { go ->
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
        debugDraw.draw()
        
        if (useFbo) {
            frameBuffer.unbind()
        }
        
        // Final state cleanup
        glUseProgram(0)
        glBindVertexArray(0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, 0)
        
        // Final screen viewport reset
        glViewport(0, 0, sceneManager.currentWidth, sceneManager.currentHeight)
    }

    private fun render3DPicking(scene: Scene, activeGameObject: GameObject?) {
        if(activeGameObject != null) return
        val camera = scene.camera

        pickingShader3D.start()
        pickingShader3D.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.createProjectionMatrix())
        pickingShader3D.uploadMat4f(Uniforms.VIEW_MATRIX, camera.createViewMatrix())

        scene.gameObjects.forEach { go ->
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

        scene.gameObjects.forEach { go ->
            go.getComponent<SpriteRenderer>()?.let { sprite ->
                renderer2D.add(go)
            }
        }
        renderer2D.render()
        renderer2D.clear()
    }

    override fun readPixel(x: Int, y: Int): Int {
        val w = sceneManager.currentWidth
        val h = sceneManager.currentHeight
        
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

