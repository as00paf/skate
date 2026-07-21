package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.config.GridConfig
import kotlinx.serialization.Serializable

@Serializable
class GridLines : Component() {

    var config: GridConfig = GridConfig()

}