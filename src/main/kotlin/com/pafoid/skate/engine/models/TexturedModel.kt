package com.pafoid.skate.engine.models

import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.scenes.components.Component
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f

@Serializable
data class TexturedModel (
    val mesh: List<MeshPart>,
    @Contextual val skeleton: Skeleton? = null,
): Component() {

    constructor(rawModel: RawModel, texture: Texture) : this(listOf(MeshPart(rawModel, Material(baseColorTexture = texture))))
    constructor(rawModel: RawModel, material: Material) : this(listOf(MeshPart(rawModel, material)))
    constructor(rawModel: RawModel, material: Material, inverseBindMatrices: List<Matrix4f>) : this(listOf(MeshPart(rawModel, material, inverseBindMatrices)))

}
