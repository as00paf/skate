package com.pafoid.skate.engine.assets.data.models

import org.joml.Matrix4f

data class LoadedMeshPart(
    val model: RawModel,
    val material: Material,
    val inverseBindMatrices: List<Matrix4f>
)