package com.pafoid.skate.engine.assets.data

import com.pafoid.skate.engine.render.rhi.RhiTexture
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Texture(
    var width: Int = 0,
    var height: Int = 0,
    var depth: Int = 1,
    var channels: Int = 0,
    var flip: Boolean = false,
    var filePath: String? = null,
) {
    /** Native handle of [rhi] for ImGui images; -1 before upload. */
    @Transient var texId: Int = -1

    /** GPU texture that draws bind; null before upload. */
    @Transient var rhi: RhiTexture? = null

    /** Points this (deserialized, path-only) texture at [loaded]'s GPU texture. */
    fun adopt(loaded: Texture) {
        texId = loaded.texId
        rhi = loaded.rhi
    }
}
