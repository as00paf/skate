package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.assets.Assets

enum class MaterialType(val displayName: String, val texturePath: String) {
    CONCRETE("Concrete", Assets.Textures.CONCRETE_SIMPLE),
    WOOD_BROWN("Wood (Brown)", Assets.Textures.WOOD_BROWN),
    WOOD_LIGHT("Wood (Light)", Assets.Textures.WOOD_LIGHT),
    WOOD_TAN("Wood (Tan)", Assets.Textures.WOOD_TAN),
    WOOD_DARK("Wood (Dark)", Assets.Textures.WOOD_DARK),
    METAL("Metal", Assets.Textures.WHITE) // Fallback
}