package com.pafoid.skate.engine.models

import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.scenes.components.Component
import org.joml.Vector4f

data class Material(
    var baseColorTexture: Texture? = null,
    var normalMap: Texture? = null,
    var metallicRoughnessTexture: Texture? = null,
    var aoTexture: Texture? = null,
    var emissiveTexture: Texture? = null,

    // Background loading paths
    var baseColorPath: String? = null,
    var normalMapPath: String? = null,
    var metallicRoughnessPath: String? = null,
    var aoPath: String? = null,
    var emissivePath: String? = null,

    var baseColorFactor: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    var metallicFactor: Float = 0f,
    var roughnessFactor: Float = 0.5f,
    var emissiveFactor: org.joml.Vector3f = org.joml.Vector3f(0f, 0f, 0f),
    var doubleSided: Boolean = false,
    var alphaMode: String = "OPAQUE",
    var alphaCutoff: Float = 0.5f
)

data class MeshPart(
    val rawModel: RawModel, 
    val material: Material,
    val inverseBindMatrices: List<org.joml.Matrix4f> = emptyList()
)

data class TexturedModel (
    val parts: List<MeshPart>,
    val skeleton: com.pafoid.skate.engine.animation.Skeleton? = null,
    val animations: List<com.pafoid.skate.engine.animation.Animation> = emptyList()
): Component() {
    constructor(rawModel: RawModel, texture: Texture) : this(listOf(MeshPart(rawModel, Material(baseColorTexture = texture))))
    constructor(rawModel: RawModel, material: Material) : this(listOf(MeshPart(rawModel, material)))
    constructor(rawModel: RawModel, material: Material, inverseBindMatrices: List<org.joml.Matrix4f>) : this(listOf(MeshPart(rawModel, material, inverseBindMatrices)))
    
    // For backward compatibility
    val rawModel: RawModel get() = parts[0].rawModel
    val texture: Texture get() = parts[0].material.baseColorTexture ?: com.pafoid.skate.engine.assets.AssetPool.getTexture(com.pafoid.skate.engine.assets.Texture.WHITE)
}
