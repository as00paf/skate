package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.render.data.RenderBatch
import com.pafoid.skate.engine.render.data.Renderable2D

class Renderer2D {
    // Batches grouped by z-index for proper layering
    private val batchesByZIndex = mutableMapOf<Int, MutableList<RenderBatch>>()

    lateinit var shader: Shader
    lateinit var camera: CameraComponent

    fun addAll(renderables: List<Renderable2D>) {
        renderables.forEach { add(it) }
    }

    fun add(renderable: Renderable2D) {
        val zIndex = renderable.spriteRenderer.zIndex
        val batches = batchesByZIndex.getOrPut(zIndex) { mutableListOf() }

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
            val newBatch = RenderBatch(1000, zIndex, this)
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
        // Render batches in z-index order (lowest to highest)
        val sortedZIndices = batchesByZIndex.keys.sorted()
        for (zIndex in sortedZIndices) {
            batchesByZIndex[zIndex]?.forEach { batch ->
                batch.render(shader)
            }
        }
    }

    fun destroy() {
        // Destroy batches
        batchesByZIndex.values.flatten().forEach { it.destroy() }
        batchesByZIndex.clear()
    }

    fun clear() {
        batchesByZIndex.values.forEach { batches ->
            batches.forEach { it.clear() }
        }
    }
}