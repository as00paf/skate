package com.pafoid.skate.engine.models

import com.pafoid.skate.engine.animation.Animation
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.scenes.components.Component
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f

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
    val texture: Texture? get() = parts[0].material.baseColorTexture
}
