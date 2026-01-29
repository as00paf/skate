package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.models.Material
import com.pafoid.skate.engine.models.RawModel
import org.joml.Matrix4f

data class LoadedMeshPart(
    val model: RawModel,
    val material: Material,
    val inverseBindMatrices: List<Matrix4f>
)