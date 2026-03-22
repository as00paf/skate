package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.tan

/**
 * System responsible for rendering a 3D grid in the editor viewport.
 *
 * This system renders a horizontal grid plane (X-Z) that follows the camera
 * with an infinite scrolling effect, similar to Godot Engine's 3D editor grid.
 *
 * Features:
 * - Dynamic grid extent based on camera distance
 * - Major/minor line distinction for visual clarity
 * - Origin axes (X=red, Y=green, Z=blue) at world origin
 * - LOD system to hide minor lines when camera is far
 *
 * @param debugRenderer Renderer for debug lines
 * @param sceneManager Manager for accessing current scene and camera
 */
class GridLines(
    private val debugRenderer: DebugRenderer,
    private val sceneManager: SceneManager
) : System(priority = ExecutionPriority.LATE) {  // Late system - rendering

    // Grid configuration constants
    private val majorStep = 1.0f
    private val minorStep = 0.1f
    private val majorColor = Vector3f(0.4f, 0.4f, 0.4f)
    private val minorColor = Vector3f(0.25f, 0.25f, 0.25f)

    // Grid extent configuration
    private val minExtent = 10.0f
    private val maxExtent = 100.0f
    private val fov = 45f
    private val padding = 1.5f

    // LOD configuration
    private val lodFarDistance = 20.0f

    // Cached vectors to reduce allocations
    private val tempVec1 = Vector3f()
    private val tempVec2 = Vector3f()

    override fun editorUpdate(dt: Float) {
        val scene = sceneManager.currentScene ?: return
        val camPos = scene.camera.position

        val cameraDistance = abs(camPos.y)
        val extent = calculateGridExtent(cameraDistance, fov, padding, minExtent, maxExtent)
        val showMinorLines = cameraDistance < lodFarDistance

        val centerX = floor(camPos.x / majorStep) * majorStep
        val centerZ = floor(camPos.z / majorStep) * majorStep
        val numLines = (extent / minorStep).toInt()

        for (i in -numLines..numLines) {
            val offset = i * minorStep

            val worldZ = centerZ + offset
            val isMajorZ = isMajorLine(worldZ, majorStep)

            if (!isMajorZ && !showMinorLines) continue

            tempVec1.set(centerX - extent, -0.001f, worldZ)
            tempVec2.set(centerX + extent, -0.001f, worldZ)
            debugRenderer.addLine3D(tempVec1, tempVec2, if (isMajorZ) majorColor else minorColor)

            val worldX = centerX + offset
            val isMajorX = isMajorLine(worldX, majorStep)

            if (!isMajorX && !showMinorLines) continue

            tempVec1.set(worldX, -0.001f, centerZ - extent)
            tempVec2.set(worldX, -0.001f, centerZ + extent)
            debugRenderer.addLine3D(tempVec1, tempVec2, if (isMajorX) majorColor else minorColor)
        }

        if (cameraDistance < 50f) {
            val axisLength = (20f * (50f - cameraDistance) / 50f).coerceIn(5f, 20f)

            tempVec1.set(-axisLength, 0.001f, 0f)
            tempVec2.set(axisLength, 0.001f, 0f)
            debugRenderer.addLine3D(tempVec1, tempVec2, Vector3f(1f, 0.2f, 0.2f))

            tempVec1.set(0f, 0.001f, -axisLength)
            tempVec2.set(0f, 0.001f, axisLength)
            debugRenderer.addLine3D(tempVec1, tempVec2, Vector3f(0.2f, 1f, 0.2f))

            tempVec1.set(0f, -axisLength, 0.001f)
            tempVec2.set(0f, axisLength, 0.001f)
            debugRenderer.addLine3D(tempVec1, tempVec2, Vector3f(0.2f, 0.2f, 1f))
        }
    }

    /**
     * Calculates the grid extent based on camera distance.
     *
     * Formula: extent = cameraDistance * tan(fov/2) * padding
     * This ensures the grid always fills the viewport regardless of camera height.
     *
     * @param cameraDistance Distance from camera to grid plane
     * @param fov Camera field of view in degrees
     * @param padding Extra padding factor to ensure grid fills viewport
     * @param minExtent Minimum grid extent (when camera is very close)
     * @param maxExtent Maximum grid extent (when camera is very far)
     * @return Calculated grid extent clamped between min and max
     */
    private fun calculateGridExtent(
        cameraDistance: Float,
        fov: Float,
        padding: Float,
        minExtent: Float,
        maxExtent: Float
    ): Float {
        val clampedDistance = cameraDistance.coerceAtLeast(0f)
        val calculatedExtent = clampedDistance * tan(Math.toRadians((fov / 2).toDouble())).toFloat() * padding
        return calculatedExtent.coerceIn(minExtent, maxExtent)
    }

    /**
     * Determines if a position is on a major grid line.
     *
     * @param position World position to check
     * @param majorStep Major line spacing interval
     * @return true if position is on a major line, false otherwise
     */
    private fun isMajorLine(position: Float, majorStep: Float): Boolean {
        val remainder = abs(position % majorStep)
        val tolerance = 0.02f
        return (remainder < tolerance || remainder > majorStep - tolerance)
    }
}