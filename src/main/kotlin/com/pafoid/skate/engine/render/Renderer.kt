package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
import com.pafoid.skate.engine.toMatrix
import com.pafoid.skate.engine.utils.Color
import org.lwjgl.opengl.GL30.*

class Renderer(
    private val defaultShader: Shader,
    private val batchShader: Shader,
    private val pickingShader: Shader,
    private val skyboxShader: Shader
) {
    private val clearColor = Color.GRAY
    private val renderer2D = Renderer2D()
    private val pickingTexture = PickingTexture(1920, 1080) // TODO: Match window size
    private val skyboxRenderer = SkyboxRenderer(skyboxShader, VAOLoader())

    init {
        renderer2D.bindShader(batchShader)
    }

    private fun loadProjectionMatrix(camera: Camera) {
        defaultShader.start()
        defaultShader.uploadMat4f("projectionMatrix", camera.createProjectionMatrix())
        defaultShader.stop()
    }

    private fun loadViewMatrix(camera: Camera) {
        defaultShader.start()
        defaultShader.uploadMat4f("viewMatrix", camera.createViewMatrix())
        defaultShader.stop()
    }

    fun render(scene: Scene) {
        DebugDraw.beginFrame()

        // 1. Picking Pass
        pickingTexture.enableWriting()
        glViewport(0, 0, 1920, 1080)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        
        renderer2D.bindCamera(scene.camera)
        render2D(scene, pickingShader)
        
        pickingTexture.disableWriting()

        // 2. Regular Pass
        Window.getFrameBuffer().bind()
        clearColor()
        
        val camera = scene.camera
        val light = scene.light

        // 2D Rendering Setup
        renderer2D.bindCamera(camera)
        
        // 3D Rendering Setup
        loadProjectionMatrix(camera)
        loadViewMatrix(camera)

        defaultShader.start()
        defaultShader.uploadVec3f("lightPosition", light.position)
        defaultShader.uploadVec3f("lightColor", light.color)
        defaultShader.uploadVec3f("uAmbientLight", scene.ambientLight)

        scene.gameObjects.forEach { go ->
            go.getComponent<Entity>()?.let { entity ->
                renderEntity(entity)
            }
        }
        
        defaultShader.stop()
        
        // Render 2D
        render2D(scene, batchShader)
        
        // Render Skybox
        scene.cubemap?.let {
            skyboxRenderer.render(camera, it)
        }

        // 3. Debug Pass
        DebugDraw.draw()
        
        Window.getFrameBuffer().unbind()
    }

    private fun render2D(scene: Scene, shader: Shader) {
        val activeRenderer2D = Renderer2D()
        activeRenderer2D.bindShader(shader)
        activeRenderer2D.bindCamera(scene.camera)

        scene.gameObjects.forEach { go ->
            go.getComponent<SpriteRenderer>()?.let { sprite ->
                activeRenderer2D.add(go)
            }
        }
        activeRenderer2D.render()
    }

    fun readPixel(x: Int, y: Int): Int {
        return pickingTexture.readPixel(x, y)
    }

    private fun renderEntity(entity: Entity) {
        val texturedModel = entity.model
        val model = texturedModel.rawModel

        glBindVertexArray(model.vaoId)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)
        glEnableVertexAttribArray(2)
        defaultShader.uploadMat4f("transformationMatrix", entity.transform.toMatrix())
        defaultShader.uploadFloat("uShininess", entity.shininess)
        defaultShader.uploadFloat("uReflectivity", entity.reflectivity)
        
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, texturedModel.texture.getId())
        glDrawElements(GL_TRIANGLES, model.vertexCount, GL_UNSIGNED_INT, 0)
        glDisableVertexAttribArray(0)
        glDisableVertexAttribArray(1)
        glDisableVertexAttribArray(2)
        glBindVertexArray(0)
    }

    fun clearColor() {
        glEnable(GL_DEPTH_TEST)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glClearColor(clearColor.x, clearColor.y, clearColor.z, clearColor.w)
    }

    fun destroy() {
        defaultShader.destroy()
        batchShader.destroy()
        skyboxShader.destroy()
        pickingShader.destroy()
    }

}