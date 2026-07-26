package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.render.renderer.ThumbnailRenderer
import org.lwjgl.opengl.GL11.glDeleteTextures

class ThumbnailCache(
    private val thumbnailRenderer: ThumbnailRenderer
) {
    private val thumbnails = mutableMapOf<String, Int>()

    fun getThumbnail(id: String, model: TexturedModel): Int {
        thumbnails[id]?.let { return it }

        val texId = thumbnailRenderer.renderThumbnail(model)
        thumbnails[id] = texId
        return texId
    }

    fun destroy() {
        thumbnails.values.forEach { texId ->
            if (texId != 0) {
                glDeleteTextures(texId)
            }
        }
        thumbnails.clear()

        thumbnailRenderer.destroy()
    }
}
