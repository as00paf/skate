package com.pafoid.skate.engine.assets.data

import com.pafoid.skate.engine.render.GpuResource
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
    @Transient var gpu: GpuResource? = null
}
