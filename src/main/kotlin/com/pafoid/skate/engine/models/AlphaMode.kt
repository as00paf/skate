package com.pafoid.skate.engine.models

import kotlinx.serialization.Serializable

@Serializable
enum class AlphaMode {
    OPAQUE,
    MASK,
    BLEND
}
