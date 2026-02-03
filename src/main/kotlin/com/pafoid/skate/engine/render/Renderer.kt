package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.ShaderConst.Attribs
import com.pafoid.skate.engine.assets.ShaderConst.Uniforms
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.NonPickable
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
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
        pickingTexture.enableWriting()
        glViewport(0, 0, 1920, 1080)
        
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
        render3DPicking(scene)
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
            go.getComponent<Entity>()?.let { entity ->
                var selectionState = 0.0f
                if (go == activeGameObject) selectionState = 1.0f
                else if (go == hoveredGameObject) selectionState = 2.0f
                
                defaultShader.uploadFloat(Uniforms.SELECTED, selectionState)
                renderEntity(go, entity)
            }
        }
        
        // Highlight Active Object (Outline effect via wireframe)
        activeGameObject?.let { go ->
            go.getComponent<Entity>()?.let { entity ->
                defaultShader.uploadFloat(Uniforms.SELECTED, 1.0f)
                glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)
                glLineWidth(4f)
                renderEntity(go, entity)
                glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)
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

    private fun render3DPicking(scene: Scene) {
        val camera = scene.camera
        
        pickingShader3D.start()
        pickingShader3D.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.createProjectionMatrix())
        pickingShader3D.uploadMat4f(Uniforms.VIEW_MATRIX, camera.createViewMatrix())

        scene.gameObjects.forEach { go ->
            val entity = go.getComponent<Entity>()
            if (entity != null && go.getComponent<NonPickable>() == null) {
                renderEntityPicking(go, entity)
            }
        }
        pickingShader3D.stop()
    }
    
    private fun renderEntityPicking(go: GameObject, entity: Entity) {
        val texturedModel = entity.model

        pickingShader3D.uploadMat4f(Uniforms.TRANSFORMATION_MATRIX, go.transform.toWorldMatrix())
        pickingShader3D.uploadFloat(Uniforms.ENTITY_ID, go.getUid().toFloat() + 1)

        val skeleton = go.getComponent<Skeleton>() ?: texturedModel.skeleton
        val hasSkin = skeleton != null
        pickingShader3D.uploadBoolean(Uniforms.HAS_SKIN, hasSkin)
        if (skeleton != null) {
            pickingShader3D.uploadMat4fArray(Uniforms.JOINT_MATRICES, skeleton.getMatrixPalette())
        }

        for (part in texturedModel.parts) {
            val model = part.rawModel
            glBindVertexArray(model.vaoId)
            model.enabledAttributes.forEach { glEnableVertexAttribArray(it) }
            glDrawElements(model.drawMode, model.vertexCount, GL_UNSIGNED_INT, 0)
            EngineStats.drawCalls.incrementAndGet()
            model.enabledAttributes.forEach { glDisableVertexAttribArray(it) }
        }
        glBindVertexArray(0)
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
        // Clamp coordinates to picking texture bounds (1920x1080)
        val safeX = x.coerceIn(0, 1919)
        val safeY = y.coerceIn(0, 1079)
        // Invert Y coordinate (0 at top becomes 1079 at bottom)
        return pickingTexture.readPixel(safeX, 1079 - safeY)
    }

    private fun renderEntity(go: GameObject, entity: Entity) {
        val texturedModel = entity.model
        val camera = sceneManager.currentScene?.camera

        defaultShader.uploadMat4f(Uniforms.TRANSFORMATION_MATRIX, go.transform.toWorldMatrix())
        defaultShader.uploadFloat(Uniforms.TEXTURE_SCALE, entity.textureScale)
        if (camera != null) {
            defaultShader.uploadVec3f(Uniforms.CAMERA_POSITION, camera.position)
        }

        for (part in texturedModel.parts) {
            val model = part.rawModel
            val material = part.material
            
            glBindVertexArray(model.vaoId)
            
            // Enable only available attributes
            model.enabledAttributes.forEach { glEnableVertexAttribArray(it) }

            // Base Color
            glActiveTexture(GL_TEXTURE0)
            material.baseColorTexture?.bind() ?: resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind()
            defaultShader.uploadInt(Uniforms.BASE_COLOR_TEXTURE, 0)
            defaultShader.uploadVec4f(Uniforms.BASE_COLOR_FACTOR, material.baseColorFactor)

            // Normal Map
            glActiveTexture(GL_TEXTURE1)
            val hasNormal = material.normalMap != null
            if (hasNormal) material.normalMap?.bind()
            else resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind() // Bind dummy
            defaultShader.uploadInt(Uniforms.NORMAL_MAP, 1)
            defaultShader.uploadBoolean(Uniforms.HAS_NORMAL_MAP, hasNormal)

            // Metallic Roughness
            glActiveTexture(GL_TEXTURE2)
            val hasMR = material.metallicRoughnessTexture != null
            if (hasMR) material.metallicRoughnessTexture?.bind()
            else resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind() // Bind dummy
            defaultShader.uploadInt(Uniforms.METALLIC_ROUGHNESS_TEXTURE, 2)
            defaultShader.uploadBoolean(Uniforms.HAS_METALLIC_ROUGHNESS_TEXTURE, hasMR)
            defaultShader.uploadFloat(Uniforms.METALLIC_FACTOR, material.metallicFactor)
            defaultShader.uploadFloat(Uniforms.ROUGHNESS_FACTOR, material.roughnessFactor)

            // AO
            glActiveTexture(GL_TEXTURE3)
            val hasAO = material.aoTexture != null
            material.aoTexture?.bind() ?: resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind()
            defaultShader.uploadInt(Uniforms.AO_TEXTURE, 3)
            defaultShader.uploadBoolean(Uniforms.HAS_AO_TEXTURE, hasAO)

            // Emissive
            glActiveTexture(GL_TEXTURE4)
            val hasEmissive = material.emissiveTexture != null
            if (hasEmissive) material.emissiveTexture?.bind()
            else resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind() // Bind dummy
            defaultShader.uploadInt(Uniforms.EMISSIVE_TEXTURE, 4)
            defaultShader.uploadBoolean(Uniforms.HAS_EMISSIVE_TEXTURE, hasEmissive)
            defaultShader.uploadVec3f(Uniforms.EMISSIVE_FACTOR, material.emissiveFactor)

            // Alpha
            val alphaInt = when(material.alphaMode) {
                "OPAQUE" -> 0
                "MASK" -> 1
                "BLEND" -> 2
                else -> 0
            }
            defaultShader.uploadInt(Uniforms.ALPHA_MODE, alphaInt)
            defaultShader.uploadFloat(Uniforms.ALPHA_CUTOFF, material.alphaCutoff)

            val skeleton = entity.gameObject.getComponent<Skeleton>() ?: entity.model.skeleton
            val hasSkin = skeleton != null
            defaultShader.uploadBoolean(Uniforms.HAS_SKIN, hasSkin)
            if (skeleton != null) {
                defaultShader.uploadMat4fArray(Uniforms.JOINT_MATRICES, skeleton.getMatrixPalette())
            }

            if (alphaInt == 2) {
                glEnable(GL_BLEND)
                glDepthMask(false)
            } else {
                glDisable(GL_BLEND)
                glDepthMask(true)
            }

            if (material.doubleSided) glDisable(GL_CULL_FACE)
            else glEnable(GL_CULL_FACE)

            glDrawElements(model.drawMode, model.vertexCount, GL_UNSIGNED_INT, 0)
            EngineStats.drawCalls.incrementAndGet()

            if (alphaInt == 2) {
                glDisable(GL_BLEND)
                glDepthMask(true)
            }

            model.enabledAttributes.forEach { glDisableVertexAttribArray(it) }
        }
        glBindVertexArray(0)
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
