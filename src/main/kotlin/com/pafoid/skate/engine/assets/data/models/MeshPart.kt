package com.pafoid.skate.engine.assets.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f

@Serializable
data class MeshPart(
    var vaoId: Int = -1,
    var vertexCount: Int = 0,
    val vertices: FloatArray = floatArrayOf(),
    val texCoords: FloatArray = floatArrayOf(),
    val texCoords1: FloatArray = floatArrayOf(),
    val normals: FloatArray = floatArrayOf(),
    val tangents: FloatArray = floatArrayOf(),
    val colors: FloatArray = floatArrayOf(),
    val joints: IntArray = intArrayOf(),
    val weights: FloatArray = floatArrayOf(),
    val indices: IntArray = intArrayOf(),
    val material: Material = Material(),
    @Transient var inverseBindMatrices: List<Matrix4f> = emptyList(),
    @Transient val enabledAttributes: MutableList<Int> = mutableListOf(0, 1, 2)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MeshPart

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
        if (vaoId != other.vaoId) return false
        if (vertexCount != other.vertexCount) return false
        if (inverseBindMatrices != other.inverseBindMatrices) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 0
        result = 31 * result + vaoId.hashCode()
        result = 31 * result + vertexCount.hashCode()
        result = 31 * result + vertices.contentHashCode()
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
        result = 31 * result + inverseBindMatrices.hashCode()
        return result
    }
}