package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.GameObject
import org.joml.Vector3f

interface IRenderer {
    var useFbo: Boolean
    fun render(scene: Scene, activeGameObject: GameObject? = null, hoveredGameObject: GameObject? = null)
    fun readPixel(x: Int, y: Int): Int
    fun clearColor(sky: Vector3f)
    fun destroy()
}
