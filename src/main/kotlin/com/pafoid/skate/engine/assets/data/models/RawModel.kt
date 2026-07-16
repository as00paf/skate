package com.pafoid.skate.engine.assets.data.models

import kotlinx.serialization.Serializable

@Serializable
data class RawModel(//TODO: remove?
    val vaoId: Int, 
    val vertexCount: Int, 
    val vertices: FloatArray = floatArrayOf(),
    val enabledAttributes: List<Int> = listOf(0, 1, 2)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawModel

        if (vaoId != other.vaoId) return false
        if (vertexCount != other.vertexCount) return false
        if (!vertices.contentEquals(other.vertices)) return false
        if (enabledAttributes != other.enabledAttributes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = vaoId
        result = 31 * result + vertexCount
        result = 31 * result + vertices.contentHashCode()
        result = 31 * result + enabledAttributes.hashCode()
        return result
    }
}