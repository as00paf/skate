package com.pafoid.skate.engine.assets.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class AlphaMode {
    OPAQUE,
    MASK,
    BLEND
}
