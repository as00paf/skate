package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.floor

class GridLines(
    private val debugRenderer: DebugRenderer,
    private val sceneManager: SceneManager
) : System(priority = ExecutionPriority.LATE) {  // Late system - rendering

    private val majorStep = 1.0f
    private val minorStep = 0.1f
    private val majorColor = Vector3f(0.3f, 0.3f, 0.3f)
    private val minorColor = Vector3f(0.15f, 0.15f, 0.15f)
    private val gridSize = 40 // Total lines in each direction around the camera

    override fun editorUpdate(dt: Float) {
        val scene = sceneManager.currentScene ?: return
        val camPos = scene.camera.position

        // Snap the grid center to the nearest major step to keep the lines aligned to the world origin
        val centerX = (floor(camPos.x / majorStep) * majorStep)
        val centerZ = (floor(camPos.z / majorStep) * majorStep)

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
            val isMajorZ = (abs(worldZ % majorStep) < 0.01f || abs(worldZ % majorStep - majorStep) < 0.01f)
            debugRenderer.addLine3D(
                Vector3f(startX, -0.001f, worldZ),
                Vector3f(endX, -0.001f, worldZ),
                if (isMajorZ) majorColor else minorColor
            )

            // Z-aligned lines
            val worldX = centerX + offset
            val isMajorX = (abs(worldX % majorStep) < 0.01f || abs(worldX % majorStep - majorStep) < 0.01f)
            debugRenderer.addLine3D(
                Vector3f(worldX, -0.001f, startZ),
                Vector3f(worldX, -0.001f, endZ),
                if (isMajorX) majorColor else minorColor
            )
        }

        // Draw Origin Axes
        debugRenderer.addLine3D(
            Vector3f(-100f, 0.001f, 0f),
            Vector3f(100f, 0.001f, 0f),
            Vector3f(1f, 0.2f, 0.2f)
        ) // X-Axis (Red)
        debugRenderer.addLine3D(
            Vector3f(0f, 0.001f, -100f),
            Vector3f(0f, 0.001f, 100f),
            Vector3f(0.2f, 1f, 0.2f)
        ) // Z-Axis (Green)
    }
}