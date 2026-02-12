package com.pafoid.skate.engine.assets.data.models

import com.pafoid.skate.engine.assets.data.Texture
import kotlinx.serialization.Serializable
import org.joml.Matrix4f

@Serializable
data class TexturedModel (
    val texturedModelMesh: List<MeshPart>,
): BaseModel(texturedModelMesh) {

    constructor(rawModel: RawModel, texture: Texture) : this(listOf(MeshPart(rawModel, Material(baseColorTexture = texture))))
    constructor(rawModel: RawModel, material: Material) : this(listOf(MeshPart(rawModel, material)))
    constructor(rawModel: RawModel, material: Material, inverseBindMatrices: List<Matrix4f>) : this(listOf(MeshPart(rawModel, material, inverseBindMatrices)))

    override val mesh: List<MeshPart>
        get() = texturedModelMesh
}
