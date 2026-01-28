package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.floor
import kotlin.math.max

class GridLines : Component() {
    private val majorStep = 1.0f
    private val minorStep = 0.1f
    private val majorColor = Vector3f(0.4f, 0.4f, 0.4f)
    private val minorColor = Vector3f(0.2f, 0.2f, 0.2f)
    private val gridSize = 50 // lines in each direction

    override fun editorUpdate(dt: Float) {
        val scene = SceneManager.getCurrentScene() ?: return
        
        // Draw Minor Grid (0.1m)
        for (i in -gridSize..gridSize) {
            val pos = i * minorStep
            val isMajor = (i % 10 == 0)
            val color = if (isMajor) majorColor else minorColor
            
            val startX = -gridSize * minorStep
            val endX = gridSize * minorStep
            val startZ = -gridSize * minorStep
            val endZ = gridSize * minorStep

            // Lines along X
            DebugDraw.addLine3D(Vector3f(startX, 0f, pos), Vector3f(endX, 0f, pos), color)
            // Lines along Z
            DebugDraw.addLine3D(Vector3f(pos, 0f, startZ), Vector3f(pos, 0f, endZ), color)
        }
    }
}