package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
import com.pafoid.skate.engine.toMatrix
import com.pafoid.skate.engine.utils.Color
import org.lwjgl.opengl.GL30.*

class Renderer(
    private val shader: Shader
) {
    private val clearColor = Color.GRAY
    private val renderer2D = Renderer2D()

    private fun loadProjectionMatrix(camera: Camera) {
        shader.start()
        shader.uploadMat4f("projectionMatrix", camera.createProjectionMatrix())
        shader.stop()
    }

    private fun loadViewMatrix(camera: Camera) {
        shader.start()
        shader.uploadMat4f("viewMatrix", camera.createViewMatrix())
        shader.stop()
    }

    fun render(scene: Scene) {
        clearColor()
        if (scene.gameObjects.isEmpty()) return

        val camera = scene.camera
        val light = scene.light

        // 2D Rendering Setup
        renderer2D.bindShader(shader) // Note: This assumes 'shader' works for 2D too, or we need a specific 2D shader
        renderer2D.bindCamera(camera)
        
        // 3D Rendering Setup
        loadProjectionMatrix(camera)
        loadViewMatrix(camera)

        shader.start()
        shader.uploadVec3f("lightPosition", light.position)
        shader.uploadVec3f("lightColor", light.color)

        // Separate entities into 2D and 3D queues if necessary, 
        // but for now let's just add everything to Renderer2D and see what sticks
        // and render 3D entities explicitly.
        
        // Clear 2D batches (re-adding every frame for now, inefficient but simple for start)
        // ideally Renderer2D keeps state, but here we are iterating every frame
        // A better approach: Renderer2D manages its own list and we just call render()
        // But since Scene holds the objects, we need to sync.
        
        // For this refactor, let's just push 2D components to renderer2D
        // In a real engine, we'd add/remove on scene changes. 
        // Let's create a temporary list for this frame or just let Renderer2D handle 'add' intelligently?
        // Renderer2D.add checks if it fits in a batch. 
        
        // Problem: If we add every frame, we duplicate sprites. 
        // Fix: Renderer2D should probably clear its batches or we just don't add if already added?
        // For now, let's create a NEW Renderer2D every frame or clear it? 
        // No, creating new is bad. 
        
        // Strategy: We will iterate the scene. 
        // If it's a 3D Entity, render immediately.
        // If it's a SpriteRenderer, add to a transient Renderer2D (or clear Renderer2D first).
        
        // Refactor: Let's make Renderer2D stateful but clearable.
        // But Renderer2D.batches is private. 
        
        // Let's rely on the fact we are building this engine step-by-step.
        // We will just render 3D entities here directly, and 2D entities via Renderer2D.
        // We need to clear Renderer2D batches at start of frame? 
        // Or assume Scene calls 'add' once?
        // "MinePaf" likely added components to the renderer when they were created.
        
        // Let's adopt the "render everything in scene" approach.
        // We'll clear Renderer2D's internal state (we need a clear method) or just re-create it.
        // Re-creating is safer for now to avoid state bugs.
        val activeRenderer2D = Renderer2D()
        activeRenderer2D.bindShader(shader)
        activeRenderer2D.bindCamera(camera)

        scene.gameObjects.forEach { go ->
            go.getComponent<Entity>()?.let { entity ->
                renderEntity(entity)
            }
            go.getComponent<SpriteRenderer>()?.let { sprite ->
                activeRenderer2D.add(go)
            }
        }
        
        shader.stop() // renderEntity uses shader, so stop before 2D
        
        // Render 2D
        // Renderer2D handles its own shader start/stop
        activeRenderer2D.render() 
    }

    private fun renderEntity(entity: Entity) {
        val texturedModel = entity.model
        val model = texturedModel.rawModel

        glBindVertexArray(model.vaoId)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)
        glEnableVertexAttribArray(2)
        shader.uploadMat4f("transformationMatrix", entity.transform.toMatrix())
        
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
        shader.destroy()
    }

}