package com.pafoid.skate.engine.models

import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.scenes.components.Component

data class MeshPart(val rawModel: RawModel, val texture: Texture)

data class TexturedModel (val parts: List<MeshPart>): Component() {
    constructor(rawModel: RawModel, texture: Texture) : this(listOf(MeshPart(rawModel, texture)))
    
    // For backward compatibility during migration
    val rawModel: RawModel get() = parts[0].rawModel
    val texture: Texture get() = parts[0].texture
}
