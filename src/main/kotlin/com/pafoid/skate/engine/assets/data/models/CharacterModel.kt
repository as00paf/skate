package com.pafoid.skate.engine.assets.data.models

import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class CharacterModel (
    val characterModelMesh: List<MeshPart>,
    @Contextual val skeleton: Skeleton,
): BaseModel(characterModelMesh) {

    override val mesh: List<MeshPart>
        get() = characterModelMesh
}