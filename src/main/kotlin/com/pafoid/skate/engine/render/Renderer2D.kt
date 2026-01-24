package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.scenes.components.SpriteRenderer
import com.pafoid.skate.engine.scenes.GameObject
import java.util.Collections

class Renderer2D {
    private val batches: MutableList<RenderBatch> = ArrayList()
    
    companion object {
        lateinit var shader: Shader
        lateinit var camera: Camera
    }

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
            Collections.sort(batches)
        }
    }
    
    fun bindShader(shader: Shader) {
        Renderer2D.shader = shader
    }
    
    fun bindCamera(camera: Camera) {
        Renderer2D.camera = camera
    }

    fun render(shader: Shader = Renderer2D.shader) {
        // shader.start() is called inside batch.render() because it sets uniforms
        for (batch in batches) {
            batch.render(shader)
        }
    }
    
    fun destroy() {
        // destroy batches
    }
}