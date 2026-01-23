package com.pafoid.skate.engine.models

import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.scenes.components.Component

data class TexturedModel (val rawModel: RawModel, val texture: Texture): Component()