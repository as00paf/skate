package com.pafoid.skate.engine.models

import com.pafoid.skate.engine.animation.Animation
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.scenes.components.Component
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import java.util.Collections.emptyList

@Serializable
data class TexturedModel (
    val mesh: List<MeshPart>,
    @Contextual val skeleton: Skeleton? = null,
    @Contextual val initialAnimations: MutableList<Animation> = emptyList()
): Component() {
    @Transient private val _animations = initialAnimations.toMutableList()
    val animations: List<Animation> get() = _animations

    constructor(rawModel: RawModel, texture: Texture) : this(listOf(MeshPart(rawModel, Material(baseColorTexture = texture))))
    constructor(rawModel: RawModel, material: Material) : this(listOf(MeshPart(rawModel, material)))
    constructor(rawModel: RawModel, material: Material, inverseBindMatrices: List<Matrix4f>) : this(listOf(MeshPart(rawModel, material, inverseBindMatrices)))
    
    fun addAnimations(newAnims: List<Animation>) {
        newAnims.forEach { newAnim ->
            if (_animations.none { it.name == newAnim.name }) {
                _animations.add(newAnim)
            }
        }
    }

    // For backward compatibility
    val rawModel: RawModel get() = mesh[0].rawModel
    val texture: Texture? get() = mesh[0].material.baseColorTexture
}
