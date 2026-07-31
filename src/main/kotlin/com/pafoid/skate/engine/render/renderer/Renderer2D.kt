package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.CameraComponent
import com.pafoid.skate.engine.render.data.RenderBatch

class Renderer2D {
    // Batches grouped by z-index for proper layering
    private val batchesByZIndex = mutableMapOf<Int, MutableList<RenderBatch>>()

    lateinit var shader: Shader
    lateinit var camera: CameraComponent

    fun add(go: GameObject) {
        val spr = go.getComponent<SpriteRenderer>()
        if (spr != null) {
            add(spr)
        }
    }

    private fun add(spr: SpriteRenderer) {
        val zIndex = spr.zIndex
        val batches = batchesByZIndex.getOrPut(zIndex) { mutableListOf() }

        var added = false
        for (batch in batches) {
            if (batch.hasRoom()) {
                val texture = spr.sprite.texture
                if (texture == null || (batch.hasTexture(texture) || batch.hasTextureRoom())) {
                    batch.addSprite(spr)
                    added = true
                    break
                }
            }
        }

        if (!added) {
            val newBatch = RenderBatch(1000, zIndex, this)
            newBatch.start()
            batches.add(newBatch)
            newBatch.addSprite(spr)
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
