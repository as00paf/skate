package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.render.data.RenderBatch3D
import com.pafoid.skate.engine.render.data.Renderable3D

class TextureRenderer3D {
    private val batches = mutableListOf<RenderBatch3D>()

    lateinit var shader: Shader
    lateinit var camera: CameraComponent

    fun addAll(renderables: List<Renderable3D>) {
        renderables.forEach { add(it) }
    }

    fun add(renderable: Renderable3D) {
        var added = false
        for (batch in batches) {
            if (batch.hasRoom()) {
                val texture = renderable.spriteRenderer.sprite.texture
                if (texture == null || (batch.hasTexture(texture) || batch.hasTextureRoom())) {
                    batch.addSprite(renderable)
                    added = true
                    break
                }
            }
        }

        if (!added) {
            val newBatch = RenderBatch3D(1000, this)
            newBatch.start()
            batches.add(newBatch)
            newBatch.addSprite(renderable)
        }
    }

    fun bindShader(shader: Shader) {
        this.shader = shader
    }

    fun bindCamera(camera: CameraComponent) {
        this.camera = camera
    }

    fun render() {
        batches.forEach { batch ->
            batch.render(shader)
        }
    }

    fun destroy() {
        // Destroy batches
        batches.forEach { it.destroy() }
        batches.clear()
    }

    fun clear() {
        batches.forEach { batches ->
            batches.clear()
        }
    }
}