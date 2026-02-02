package com.pafoid.skate.engine.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f

@Serializable
data class MeshPart(
    val rawModel: RawModel,
    val material: Material,
    val inverseBindMatrices: List<@Contextual Matrix4f> = emptyList()
)