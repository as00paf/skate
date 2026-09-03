package com.pafoid.skate.engine.render.data

import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform

data class Renderable2D(val spriteRenderer: SpriteRenderer, val transform: Transform)