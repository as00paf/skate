package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.render.renderer.ThumbnailRenderer
import org.lwjgl.opengl.GL11.glDeleteTextures

/**
 * Cache for rendered model thumbnails.
 *
 * Delegates rendering to [ThumbnailRenderer] and caches the resulting
 * texture IDs by string identifier. This class has NO OpenGL calls -
 * it's a pure cache layer.
 */
class ThumbnailCache(
    private val thumbnailRenderer: ThumbnailRenderer
) {
    private val thumbnails = mutableMapOf<String, Int>()

    /**
     * Gets or creates a thumbnail texture for the given model.
     * @param id Unique cache key (typically "${modelPath}_${materialName}")
     * @param model The textured model to render
     * @return OpenGL texture ID containing the thumbnail
     */
    fun getThumbnail(id: String, model: TexturedModel): Int {
        thumbnails[id]?.let { return it }

        val texId = thumbnailRenderer.renderThumbnail(model)
        thumbnails[id] = texId
        return texId
    }

    /**
     * Cleans up all cached thumbnail textures.
     * Call this when shutting down or switching projects.
     */
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
