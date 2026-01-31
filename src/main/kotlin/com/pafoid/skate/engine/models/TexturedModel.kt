package com.pafoid.skate.engine.models

import com.pafoid.skate.engine.animation.Animation
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.scenes.components.Component
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import org.joml.Vector4f

@Serializable
data class Material(
    @Transient var baseColorTexture: Texture? = null,
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

    @kotlinx.serialization.Contextual var baseColorFactor: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    var metallicFactor: Float = 0f,
    var roughnessFactor: Float = 0.5f,
    @kotlinx.serialization.Contextual var emissiveFactor: org.joml.Vector3f = org.joml.Vector3f(0f, 0f, 0f),
    var doubleSided: Boolean = false,
    var alphaMode: String = "OPAQUE",
    var alphaCutoff: Float = 0.5f
)

@Serializable
data class MeshPart(
    val rawModel: RawModel, 
    val material: Material,
    val inverseBindMatrices: List<@kotlinx.serialization.Contextual Matrix4f> = emptyList()
)

@Serializable
data class TexturedModel (
    val parts: List<MeshPart>,
    @Transient val skeleton: Skeleton? = null,
    @Transient val animations: List<Animation> = emptyList()
): Component() {
    constructor(rawModel: RawModel, texture: Texture) : this(listOf(MeshPart(rawModel, Material(baseColorTexture = texture))))
    constructor(rawModel: RawModel, material: Material) : this(listOf(MeshPart(rawModel, material)))
    constructor(rawModel: RawModel, material: Material, inverseBindMatrices: List<Matrix4f>) : this(listOf(MeshPart(rawModel, material, inverseBindMatrices)))
    
    // For backward compatibility
    val rawModel: RawModel get() = parts[0].rawModel
    val texture: Texture get() = parts[0].material.baseColorTexture ?: AssetPool.getTexture(Assets.Textures.WHITE)
}
