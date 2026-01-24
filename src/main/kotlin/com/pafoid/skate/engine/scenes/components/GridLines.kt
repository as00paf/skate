package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.floor
import kotlin.math.max

class GridLines : Component() {
    private val gridWidth = 0.25f
    private val gridHeight = 0.25f
    private val color = Vector3f(0.2f, 0.2f, 0.2f)

    override fun editorUpdate(dt: Float) {
        val scene = SceneManager.getCurrentScene() ?: return
        val camera = scene.camera
        val cameraPos = camera.position
        val projectionSize = camera.getProjectionSize()
        val zoom = camera.zoom

        val firstX = (floor(cameraPos.x / gridWidth) - 1) * gridWidth
        val firstY = (floor(cameraPos.y / gridHeight) - 1) * gridHeight

        val width = (projectionSize.x * zoom) + gridWidth * 4
        val height = (projectionSize.y * zoom) + gridHeight * 4

        val numVtLines = (width / gridWidth).toInt() + 2
        val numHzLines = (height / gridHeight).toInt() + 2

        val maxLines = max(numVtLines, numHzLines)

        for (i in 0 until maxLines) {
            val x = firstX + (gridWidth * i)
            val y = firstY + (gridHeight * i)

            if (i < numVtLines) {
                DebugDraw.addLine2D(Vector2f(x, firstY), Vector2f(x, firstY + height), color)
            }
            if (i < numHzLines) {
                DebugDraw.addLine2D(Vector2f(firstX, y), Vector2f(firstX + width, y), color)
            }
        }
    }
}