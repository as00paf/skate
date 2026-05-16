package com.pafoid.skate.engine.assets.data.models

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.components.Component
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f

@Serializable
abstract class BaseModel(
    open val mesh: List<@Contextual MeshPart>,
    @Transient var sourcePath: String? = null,
): Component() {

    constructor(rawModel: RawModel, texture: Texture) : this(listOf(MeshPart(rawModel, Material(baseColorTexture = texture))))
    constructor(rawModel: RawModel, material: Material) : this(listOf(MeshPart(rawModel, material)))
    constructor(rawModel: RawModel, material: Material, inverseBindMatrices: List<Matrix4f>) : this(listOf(MeshPart(rawModel, material, inverseBindMatrices)))
}