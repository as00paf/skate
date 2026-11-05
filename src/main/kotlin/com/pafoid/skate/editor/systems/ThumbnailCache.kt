package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.render.renderer.ThumbnailRenderer
import com.pafoid.skate.engine.render.rhi.RhiTexture

/** Model thumbnails for the asset browser, rendered once per id and kept until [destroy]. */
class ThumbnailCache(
    private val thumbnailRenderer: ThumbnailRenderer
) {
    private val thumbnails = mutableMapOf<String, RhiTexture>()

    /** ImGui texture handle of [model]'s thumbnail ([RhiTexture.nativeHandle]). */
    fun getThumbnail(id: String, model: `3dModel`): Int =
        thumbnails.getOrPut(id) { thumbnailRenderer.renderThumbnail(model) }.nativeHandle.toInt()

    fun destroy() {
        thumbnails.values.forEach { it.close() }
        thumbnails.clear()

        thumbnailRenderer.destroy()
    }
}
