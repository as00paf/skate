package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.data.RenderBatch

class Renderer2D {
    private val batches: MutableList<RenderBatch> = ArrayList()

    lateinit var shader: Shader
    lateinit var camera: Camera

    fun add(go: GameObject) {
        val spr = go.getComponent<SpriteRenderer>()
        if (spr != null) {
            add(spr)
        }
    }

    private fun add(spr: SpriteRenderer) {
        var added = false
        for (batch in batches) {
            if (batch.hasRoom() && batch.zIndex() == 0) { // TODO: Handle z-index properly
                val texture = spr.getTexture()
                if (texture == null || (batch.hasTexture(texture) || batch.hasTextureRoom())) {
                    batch.addSprite(spr)
                    added = true
                    break
                }
            }
        }

        if (!added) {
            val newBatch = RenderBatch(1000, 0, this)
            newBatch.start()
            batches.add(newBatch)
            newBatch.addSprite(spr)
            batches.sort()
        }
    }
    
    fun bindShader(shader: Shader) {
        this.shader = shader
    }
    
    fun bindCamera(camera: Camera) {
        this.camera = camera
    }

    fun render() {
        // shader.start() is called inside batch.render() because it sets uniforms
        for (batch in batches) {
            batch.render(shader)
        }
    }
    
    fun destroy() {
        // destroy batches
    }

    fun clear() {
        for (batch in batches) {
            batch.clear()
        }
    }
}