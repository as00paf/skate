package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.render.data.RenderBatch2D
import com.pafoid.skate.engine.render.data.Renderable2D
import com.pafoid.skate.engine.render.ui.UiBatchRenderer
import com.pafoid.skate.engine.render.ui.UiDrawList

class Renderer2D {
    // Batches grouped by z-index for proper layering
    private val batchesByZIndex = mutableMapOf<Int, MutableList<RenderBatch2D>>()

    /** Runtime-UI primitives (scissor stack, 9-slice, glyph quads); drawn after world batches by [renderUi]. */
    val ui = UiDrawList()
    private var uiRenderer: UiBatchRenderer? = null

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
            val newBatch = RenderBatch2D(1000, zIndex, this)
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

    /** Draws and clears the recorded UI primitives in screen pixels (origin top-left). */
    fun renderUi(screenWidth: Int, screenHeight: Int) {
        if (ui.quadCount > 0) {
            val r = uiRenderer ?: UiBatchRenderer().also { uiRenderer = it }
            r.render(ui, shader, screenWidth, screenHeight)
        }
        ui.clear()
    }

    fun destroy() {
        uiRenderer?.destroy()
        uiRenderer = null
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