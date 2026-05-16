package com.pafoid.skate.engine.assets.data.models

import com.pafoid.skate.engine.assets.data.Texture
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f
import org.joml.Vector4f

@Serializable
data class Material(
    var baseColorTexture: Texture? = null,
    @Transient var normalMap: Texture? = null,
    @Transient var metallicRoughnessTexture: Texture? = null,
    @Transient var aoTexture: Texture? = null,
    @Transient var emissiveTexture: Texture? = null,

    // Background loading paths
    var baseColorPath: String? = null,
    var normalMapPath: String? = null,
    var metallicRoughnessPath: String? = null,
    var aoPath: String? = null,
    var emissivePath: String? = null,

    @Contextual var baseColorFactor: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    var metallicFactor: Float = 0f,
    var roughnessFactor: Float = 0.5f,
    @Contextual var emissiveFactor: Vector3f = Vector3f(0f, 0f, 0f),
    var doubleSided: Boolean = false,
    var alphaMode: AlphaMode = AlphaMode.OPAQUE,
    var alphaCutoff: Float = 0.5f
)