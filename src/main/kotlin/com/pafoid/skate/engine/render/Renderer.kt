package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
import com.pafoid.skate.engine.toMatrix
import com.pafoid.skate.engine.utils.Color
import org.lwjgl.opengl.GL30.*

class Renderer(
    private val defaultShader: Shader,
    private val batchShader: Shader
) {
    private val clearColor = Color.GRAY
    private val renderer2D = Renderer2D()

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
        clearColor()
        if (scene.gameObjects.isEmpty()) return

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

        // Using a fresh renderer2D for this frame to avoid state persistence issues (temporary)
        val activeRenderer2D = Renderer2D()
        activeRenderer2D.bindShader(batchShader)
        activeRenderer2D.bindCamera(camera)

        scene.gameObjects.forEach { go ->
            go.getComponent<Entity>()?.let { entity ->
                renderEntity(entity)
            }
            go.getComponent<SpriteRenderer>()?.let { sprite ->
                activeRenderer2D.add(go)
            }
        }
        
        defaultShader.stop() // renderEntity uses shader, so stop before 2D
        
        // Render 2D
        activeRenderer2D.render() 
    }

    private fun renderEntity(entity: Entity) {
        val texturedModel = entity.model
        val model = texturedModel.rawModel

        glBindVertexArray(model.vaoId)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)
        glEnableVertexAttribArray(2)
        defaultShader.uploadMat4f("transformationMatrix", entity.transform.toMatrix())
        
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
    }

}