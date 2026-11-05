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
    @Transient var texId: Int = -1
    /** GPU texture ([texId] is its native handle, kept for draw code and ImGui until REN-12 step 6). */
    @Transient var rhi: RhiTexture? = null
}
