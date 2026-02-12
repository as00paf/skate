package com.pafoid.skate.engine.assets.data.models

import com.pafoid.skate.engine.assets.data.models.animations.Skeleton

data class PreLoadedModel(
    val parts: List<PreLoadedMeshPart>,
    val skeleton: Skeleton? = null,
)
