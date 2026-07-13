package com.pafoid.skate.engine.assets.data.models

import com.pafoid.skate.engine.assets.data.models.animations.Skeleton

//TODO: remove
data class PreLoadedModel(
    val parts: List<MeshPart>,
    val skeleton: Skeleton? = null,
)
