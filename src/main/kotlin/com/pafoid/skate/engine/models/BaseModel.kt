package com.pafoid.skate.engine.models

import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.models.Material
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.scenes.components.Component
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f

@Serializable
abstract class BaseModel(
    open val mesh: List<@Contextual MeshPart>,
): Component() {

    constructor(rawModel: RawModel, texture: Texture) : this(listOf(MeshPart(rawModel, Material(baseColorTexture = texture))))
    constructor(rawModel: RawModel, material: Material) : this(listOf(MeshPart(rawModel, material)))
    constructor(rawModel: RawModel, material: Material, inverseBindMatrices: List<Matrix4f>) : this(listOf(MeshPart(rawModel, material, inverseBindMatrices)))
}