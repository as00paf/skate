package com.pafoid.skate.engine.assets.data.models

import org.joml.Matrix4f
import java.nio.ByteBuffer

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
    val embeddedTextures: Map<String, ByteBuffer>,
    val inverseBindMatrices: List<Matrix4f> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PreLoadedMeshPart

        if (drawMode != other.drawMode) return false
        if (!vertices.contentEquals(other.vertices)) return false
        if (!texCoords.contentEquals(other.texCoords)) return false
        if (!texCoords1.contentEquals(other.texCoords1)) return false
        if (!normals.contentEquals(other.normals)) return false
        if (!tangents.contentEquals(other.tangents)) return false
        if (!colors.contentEquals(other.colors)) return false
        if (!joints.contentEquals(other.joints)) return false
        if (!weights.contentEquals(other.weights)) return false
        if (!indices.contentEquals(other.indices)) return false
        if (material != other.material) return false
        if (embeddedTextures != other.embeddedTextures) return false
        if (inverseBindMatrices != other.inverseBindMatrices) return false

        return true
    }

    override fun hashCode(): Int {
        var result = drawMode
        result = 31 * result + vertices.contentHashCode()
        result = 31 * result + texCoords.contentHashCode()
        result = 31 * result + texCoords1.contentHashCode()
        result = 31 * result + normals.contentHashCode()
        result = 31 * result + tangents.contentHashCode()
        result = 31 * result + colors.contentHashCode()
        result = 31 * result + joints.contentHashCode()
        result = 31 * result + weights.contentHashCode()
        result = 31 * result + indices.contentHashCode()
        result = 31 * result + material.hashCode()
        result = 31 * result + embeddedTextures.hashCode()
        result = 31 * result + inverseBindMatrices.hashCode()
        return result
    }
}