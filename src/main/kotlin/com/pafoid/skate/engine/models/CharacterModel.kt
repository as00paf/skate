package com.pafoid.skate.engine.models

import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.scenes.components.Component
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class CharacterModel (
    val mesh: List<MeshPart>,
    @Contextual val skeleton: Skeleton // Should not change
): Component() {
}
