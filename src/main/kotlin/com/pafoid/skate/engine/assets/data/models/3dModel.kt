package com.pafoid.skate.engine.assets.data.models

import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class `3dModel`(
    var path: String = "",
    @Transient var mesh: List<MeshPart> = emptyList(),
    @Contextual var skeleton: Skeleton? = null,
)
