package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.components.NonPickable
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
import com.pafoid.skate.engine.scenes.components.toWorldMatrix
import com.pafoid.skate.engine.utils.Color
import org.joml.Vector3f
import org.lwjgl.opengl.GL30.*

class Renderer(
    private val defaultShader: Shader,
    private val batchShader: Shader,
    private val pickingShader: Shader,
    private val pickingShader3D: Shader,
    private val skyboxShader: Shader,
    private val skyDomeShader: Shader
) : IRenderer {
    override var useFbo = false // Default to false for initial feature tests
    
    private val clearColor = Color.GRAY
    private val renderer2D = Renderer2D()
    private val pickingTexture = PickingTexture(1920, 1080)
    private val skyboxRenderer = SkyboxRenderer(skyboxShader, VAOLoader())
    private val skyDomeRenderer = SkyDomeRenderer(skyDomeShader, VAOLoader())

    init {
        renderer2D.bindShader(batchShader)
    }

    private fun loadProjectionMatrix(camera: Camera) {
        defaultShader.start()
        defaultShader.uploadMat4f("projectionMatrix", camera.createProjectionMatrix())
    }

    private fun loadViewMatrix(camera: Camera) {
        defaultShader.start()
        defaultShader.uploadMat4f("viewMatrix", camera.createViewMatrix())
    }

    override fun render(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) {
        DebugDraw.beginFrame()
        PickingDraw.beginFrame()
        
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
        PickingDraw.draw()
        
        pickingTexture.disableWriting()

        // 2. Regular Pass
        val mainFbo = Window.getFrameBuffer()
        if (useFbo) {
            mainFbo?.bind()
            glViewport(0, 0, mainFbo?.width ?: 0, mainFbo?.height ?: 0)
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            glViewport(0, 0, Window.currentWidth, Window.currentHeight)
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
        defaultShader.uploadVec3f("lightPosition", light.position)
        defaultShader.uploadVec3f("lightColor", Vector3f(1.5f, 1.5f, 1.5f)) // Brighter light
        
        val ambient = if (scene.useAmbient) scene.ambientLight else Vector3f(0f, 0f, 0f)
        defaultShader.uploadVec3f("uAmbientLight", ambient)

        // Sun
        defaultShader.uploadVec3f("uSunDirection", scene.sun.direction)
        val finalSunColor = if (scene.useSun) Vector3f(scene.sun.color).mul(scene.sun.intensity) else Vector3f(0f, 0f, 0f)
        defaultShader.uploadVec3f("uSunColor", finalSunColor)

        // Moon
        defaultShader.uploadVec3f("uMoonDirection", scene.moon.direction)
        val finalMoonColor = Vector3f(scene.moon.color).mul(scene.moon.intensity)
        defaultShader.uploadVec3f("uMoonColor", finalMoonColor)

        // Fog
        defaultShader.uploadVec3f("uFogColor", scene.fogColor)
        defaultShader.uploadFloat("uFogDensity", scene.fogDensity)
        defaultShader.uploadFloat("uFogGradient", scene.fogGradient)

        scene.gameObjects.forEach { go ->
            go.getComponent<Entity>()?.let { entity ->
                var selectionState = 0.0f
                if (go == activeGameObject) selectionState = 1.0f
                else if (go == hoveredGameObject) selectionState = 2.0f
                
                defaultShader.uploadFloat("uSelected", selectionState)
                renderEntity(go, entity)
            }
        }
        
        // Highlight Active Object (Outline effect via wireframe)
        activeGameObject?.let { go ->
            go.getComponent<Entity>()?.let { entity ->
                defaultShader.uploadFloat("uSelected", 1.0f)
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
        DebugDraw.draw()
        
        if (useFbo) {
            mainFbo?.unbind()
        }
        
        // Final state cleanup
        glUseProgram(0)
        glBindVertexArray(0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, 0)
        
        // Final screen viewport reset
        glViewport(0, 0, Window.currentWidth, Window.currentHeight)
    }

    private fun render3DPicking(scene: Scene) {
        val camera = scene.camera
        
        pickingShader3D.start()
        pickingShader3D.uploadMat4f("projectionMatrix", camera.createProjectionMatrix())
        pickingShader3D.uploadMat4f("viewMatrix", camera.createViewMatrix())

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

        pickingShader3D.uploadMat4f("transformationMatrix", go.transform.toWorldMatrix())
        pickingShader3D.uploadFloat("uEntityId", go.getUid().toFloat() + 1)

        val skeleton = go.getComponent<com.pafoid.skate.engine.animation.Skeleton>() ?: texturedModel.skeleton
        val hasSkin = skeleton != null
        pickingShader3D.uploadBoolean("u_HasSkin", hasSkin)
        if (skeleton != null) {
            pickingShader3D.uploadMat4fArray("u_JointMatrices", skeleton.getMatrixPalette())
        }

        for (part in texturedModel.parts) {
            val model = part.rawModel
            glBindVertexArray(model.vaoId)
            model.enabledAttributes.forEach { glEnableVertexAttribArray(it) }
            glDrawElements(model.drawMode, model.vertexCount, GL_UNSIGNED_INT, 0)
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
        val camera = com.pafoid.skate.engine.scenes.SceneManager.getCurrentScene()?.camera

        defaultShader.uploadMat4f("transformationMatrix", go.transform.toWorldMatrix())
        defaultShader.uploadFloat("uTextureScale", entity.textureScale)
        if (camera != null) {
            defaultShader.uploadVec3f("uCameraPos", camera.position)
        }

        for (part in texturedModel.parts) {
            val model = part.rawModel
            val material = part.material
            
            glBindVertexArray(model.vaoId)
            
            // Enable only available attributes
            model.enabledAttributes.forEach { glEnableVertexAttribArray(it) }

            // Base Color
            glActiveTexture(GL_TEXTURE0)
            material.baseColorTexture?.bind() ?: AssetPool.getTexture(Texture.WHITE).bind()
            defaultShader.uploadInt("u_BaseColorTexture", 0)
            defaultShader.uploadVec4f("u_BaseColorFactor", material.baseColorFactor)

            // Normal Map
            glActiveTexture(GL_TEXTURE1)
            val hasNormal = material.normalMap != null
            if (hasNormal) material.normalMap!!.bind()
            else AssetPool.getTexture(Texture.WHITE).bind() // Bind dummy
            defaultShader.uploadInt("u_NormalMap", 1)
            defaultShader.uploadBoolean("u_HasNormalMap", hasNormal)

            // Metallic Roughness
            glActiveTexture(GL_TEXTURE2)
            val hasMR = material.metallicRoughnessTexture != null
            if (hasMR) material.metallicRoughnessTexture!!.bind()
            else AssetPool.getTexture(Texture.WHITE).bind() // Bind dummy
            defaultShader.uploadInt("u_MetallicRoughnessTexture", 2)
            defaultShader.uploadBoolean("u_HasMetallicRoughnessTexture", hasMR)
            defaultShader.uploadFloat("u_MetallicFactor", material.metallicFactor)
            defaultShader.uploadFloat("u_RoughnessFactor", material.roughnessFactor)

            // AO
            glActiveTexture(GL_TEXTURE3)
            val hasAO = material.aoTexture != null
            material.aoTexture?.bind() ?: AssetPool.getTexture(Texture.WHITE).bind()
            defaultShader.uploadInt("u_AOTexture", 3)
            defaultShader.uploadBoolean("u_HasAOTexture", hasAO)

            // Emissive
            glActiveTexture(GL_TEXTURE4)
            val hasEmissive = material.emissiveTexture != null
            if (hasEmissive) material.emissiveTexture!!.bind()
            else AssetPool.getTexture(Texture.WHITE).bind() // Bind dummy
            defaultShader.uploadInt("u_EmissiveTexture", 4)
            defaultShader.uploadBoolean("u_HasEmissiveTexture", hasEmissive)
            defaultShader.uploadVec3f("u_EmissiveFactor", material.emissiveFactor)

            // Alpha
            val alphaInt = when(material.alphaMode) {
                "OPAQUE" -> 0
                "MASK" -> 1
                "BLEND" -> 2
                else -> 0
            }
            defaultShader.uploadInt("u_AlphaMode", alphaInt)
            defaultShader.uploadFloat("u_AlphaCutoff", material.alphaCutoff)

            val skeleton = entity.gameObject.getComponent<com.pafoid.skate.engine.animation.Skeleton>() ?: entity.model.skeleton
            val hasSkin = skeleton != null
            defaultShader.uploadBoolean("u_HasSkin", hasSkin)
            if (skeleton != null) {
                defaultShader.uploadMat4fArray("u_JointMatrices", skeleton.getMatrixPalette())
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
