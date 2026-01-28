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
    private val majorColor = Vector3f(0.3f, 0.3f, 0.3f)
    private val minorColor = Vector3f(0.15f, 0.15f, 0.15f)
    private val gridSize = 40 // Total lines in each direction around the camera

    override fun editorUpdate(dt: Float) {
        val scene = SceneManager.getCurrentScene() ?: return
        val camPos = scene.camera.position
        
        // Snap the grid center to the nearest major step to keep the lines aligned to the world origin
        val centerX = (floor(camPos.x / majorStep) * majorStep).toFloat()
        val centerZ = (floor(camPos.z / majorStep) * majorStep).toFloat()
        
        val halfRange = gridSize * minorStep
        val startX = centerX - halfRange
        val endX = centerX + halfRange
        val startZ = centerZ - halfRange
        val endZ = centerZ + halfRange

        // Draw lines
        for (i in -gridSize..gridSize) {
            val offset = i * minorStep
            
            // X-aligned lines
            val worldZ = centerZ + offset
            val isMajorZ = (Math.abs(worldZ % majorStep) < 0.01f || Math.abs(worldZ % majorStep - majorStep) < 0.01f)
            DebugDraw.addLine3D(
                Vector3f(startX, -0.001f, worldZ), 
                Vector3f(endX, -0.001f, worldZ), 
                if (isMajorZ) majorColor else minorColor
            )

            // Z-aligned lines
            val worldX = centerX + offset
            val isMajorX = (Math.abs(worldX % majorStep) < 0.01f || Math.abs(worldX % majorStep - majorStep) < 0.01f)
            DebugDraw.addLine3D(
                Vector3f(worldX, -0.001f, startZ), 
                Vector3f(worldX, -0.001f, endZ), 
                if (isMajorX) majorColor else minorColor
            )
        }
        
        // Draw Origin Axes
        DebugDraw.addLine3D(Vector3f(-100f, 0.001f, 0f), Vector3f(100f, 0.001f, 0f), Vector3f(1f, 0.2f, 0.2f)) // X-Axis (Red)
        DebugDraw.addLine3D(Vector3f(0f, 0.001f, -100f), Vector3f(0f, 0.001f, 100f), Vector3f(0.2f, 1f, 0.2f)) // Z-Axis (Green)
    }
}