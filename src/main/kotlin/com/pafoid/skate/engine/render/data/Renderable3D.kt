package com.pafoid.skate.engine.render.data

import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import org.joml.Matrix4f

data class Renderable3D(val spriteRenderer: SpriteRenderer, val position: Matrix4f)