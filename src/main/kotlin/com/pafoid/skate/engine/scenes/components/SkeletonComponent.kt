package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.animation.Skeleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class SkeletonComponent(
    val skeleton: Skeleton? = null
) : Component() {

    override fun update(dt: Float) {
        skeleton?.update()
    }
}