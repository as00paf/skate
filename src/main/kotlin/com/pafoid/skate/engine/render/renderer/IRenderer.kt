package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import org.joml.Vector3f

interface IRenderer {
    var useFbo: Boolean
    fun render(scene: Scene, activeGameObject: GameObject? = null, hoveredGameObject: GameObject? = null)
    fun readPixel(x: Int, y: Int): Int
    fun clearColor(sky: Vector3f)
    fun destroy()
}