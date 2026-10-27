package com.pafoid.skate.engine.assets.data.models

import com.pafoid.skate.engine.assets.data.Texture
import kotlinx.serialization.Contextual
import com.pafoid.skate.engine.render.material.MaterialSchema.Companion.Param
import com.pafoid.skate.engine.render.material.MaterialSchema.Companion.Tex
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.concurrent.atomic.AtomicInteger
import org.joml.Vector3f
import org.joml.Vector4f

@Serializable
data class Material(
    var baseColorTexture: Texture? = null,
    var normalMap: Texture? = null,
    var metallicRoughnessTexture: Texture? = null,
    var aoTexture: Texture? = null,
    var emissiveTexture: Texture? = null,
    @Contextual val baseColor: Vector4f = Vector4f(1f),
    var baseColorFactor: Float = 1f,
    var metallicFactor: Float = 0f,
    var roughnessFactor: Float = 0.5f,
    @Contextual val emissiveFactor: Vector3f = Vector3f(0f),
    var doubleSided: Boolean = false,
    var alphaMode: AlphaMode = AlphaMode.OPAQUE,
    var alphaCutoff: Float = 0.5f
) {
    /** Runtime id used for draw sorting; not persisted. */
    @Transient
    val id: Int = nextId.incrementAndGet()

    /** Writes the schema parameter [name] (PBR schema) into [out] at [offset]. */
    fun readParam(name: String, out: FloatArray, offset: Int) {
        when (name) {
            Param.BASE_COLOR -> { out[offset] = baseColor.x; out[offset + 1] = baseColor.y; out[offset + 2] = baseColor.z; out[offset + 3] = baseColor.w }
            Param.BASE_COLOR_FACTOR -> out[offset] = baseColorFactor
            Param.METALLIC_FACTOR -> out[offset] = metallicFactor
            Param.ROUGHNESS_FACTOR -> out[offset] = roughnessFactor
            Param.EMISSIVE_FACTOR -> { out[offset] = emissiveFactor.x; out[offset + 1] = emissiveFactor.y; out[offset + 2] = emissiveFactor.z }
            Param.ALPHA_MODE -> out[offset] = alphaMode.ordinal.toFloat()
            Param.ALPHA_CUTOFF -> out[offset] = alphaCutoff
            else -> throw IllegalArgumentException("Unknown material param '$name'")
        }
    }

    fun textureFor(slotName: String): Texture? = when (slotName) {
        Tex.BASE_COLOR -> baseColorTexture
        Tex.NORMAL -> normalMap
        Tex.METALLIC_ROUGHNESS -> metallicRoughnessTexture
        Tex.AO -> aoTexture
        Tex.EMISSIVE -> emissiveTexture
        else -> null
    }

    private companion object {
        val nextId = AtomicInteger(0)
    }
}
