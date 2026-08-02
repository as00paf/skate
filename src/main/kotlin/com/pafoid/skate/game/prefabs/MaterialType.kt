package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.Assets

enum class MaterialType(val displayName: String, val texturePath: String) {
    CONCRETE("Concrete", Assets.Bundled.CONCRETE_SIMPLE),
    WOOD_BROWN("Wood (Brown)", Assets.Bundled.WOOD_BROWN),
    WOOD_LIGHT("Wood (Light)", Assets.Bundled.WOOD_LIGHT),
    WOOD_TAN("Wood (Tan)", Assets.Bundled.WOOD_TAN),
    WOOD_DARK("Wood (Dark)", Assets.Bundled.WOOD_DARK),
    METAL("Metal", Assets.Bundled.METAL) // Fallback
}