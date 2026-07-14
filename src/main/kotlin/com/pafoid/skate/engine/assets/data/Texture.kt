package com.pafoid.skate.engine.assets.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.nio.ByteBuffer

@Serializable
data class Texture(
    var width: Int = 0,
    var height: Int = 0,
    var depth: Int = 1,
    var channels: Int = 0,
    var flip: Boolean = false,
    var filePath: String? = null,
    @Transient var pixels: ByteBuffer? = null
) {
    @Transient var texId: Int = -1
}
