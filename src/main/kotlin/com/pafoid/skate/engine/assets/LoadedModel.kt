package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.animation.Animation
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.Material

data class LoadedMeshPart(
    val model: RawModel,
    val material: Material,
    val inverseBindMatrices: List<org.joml.Matrix4f>
)

data class PreLoadedMeshPart(
    val vertices: FloatArray,
    val texCoords: FloatArray,
    val texCoords1: FloatArray,
    val normals: FloatArray,
    val tangents: FloatArray,
    val colors: FloatArray,
    val joints: IntArray,
    val weights: FloatArray,
    val indices: IntArray,
    val material: Material,
    val drawMode: Int,
    val embeddedTextures: Map<String, java.nio.ByteBuffer>,
    val inverseBindMatrices: List<org.joml.Matrix4f> = emptyList()
)

data class PreLoadedModel(
    val parts: List<PreLoadedMeshPart>,
    val skeleton: Skeleton? = null,
    val animations: List<Animation> = emptyList()
)
