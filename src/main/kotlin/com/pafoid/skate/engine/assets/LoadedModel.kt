package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.animation.Skeleton

data class PreLoadedModel(
    val parts: List<PreLoadedMeshPart>,
    val skeleton: Skeleton? = null,
)
