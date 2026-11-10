package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.Assets

enum class MaterialType(val displayName: String, val texturePath: String) {
    CONCRETE("Concrete", EngineDefaults.Textures.CONCRETE_SIMPLE),
    WOOD_BROWN("Wood (Brown)", EngineDefaults.Textures.WOOD_BROWN),
    WOOD_LIGHT("Wood (Light)", EngineDefaults.Textures.WOOD_LIGHT),
    WOOD_TAN("Wood (Tan)", EngineDefaults.Textures.WOOD_TAN),
    WOOD_DARK("Wood (Dark)", EngineDefaults.Textures.WOOD_DARK),
    METAL("Metal", EngineDefaults.Textures.METAL) // Fallback
}