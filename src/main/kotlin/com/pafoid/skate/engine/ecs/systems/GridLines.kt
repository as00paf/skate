package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.config.GridConfig
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.render.CameraManager
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.tan

class GridLines(
    private val debugRenderer: DebugRenderer,
    private val cameraManager: CameraManager,
) : System(priority = ExecutionPriority.LATE) {// TODO: should be a scene component

    val config: GridConfig = GridConfig()

    // Cached vectors to reduce allocations
    private val lineStart = Vector3f()
    private val lineEnd = Vector3f()

    // Cached constants
    private val fovRadians = Math.toRadians(22.5).toFloat() // 45/2 degrees
    private val tanHalfFov = tan(fovRadians)
    private val padding = 2.5f  // Increased to match Godot's larger grid extent

    // Origin axes colors (cached to avoid allocations)
    private val xAxisColor = Vector3f(1f, 0.2f, 0.2f)
    private val yAxisColor = Vector3f(0.2f, 1f, 0.2f)
    private val zAxisColor = Vector3f(0.2f, 0.2f, 1f)

    // Center marker color (cached)
    private val centerMarkerColorCached = Vector3f(1f, 1f, 0f)

    // Snap visualization marker (cached)
    private val snapMarkerColorCached = Vector3f(0f, 1f, 0f)

    override fun update(dt: Float) {
        if (!config.showGrid || scene.isRunning) return

        val camPos = cameraManager.camera.position
        
        val cameraDistance = abs(camPos.y)
        val extent = calculateGridExtent(cameraDistance)
        val minorLineAlpha = calculateMinorLineAlpha(cameraDistance)

        // Calculate grid center (snapped to major step)
        val centerX = floor(camPos.x / config.majorStep) * config.majorStep
        val centerZ = floor(camPos.z / config.majorStep) * config.majorStep
        val numLines = (extent / config.minorStep).toInt()

        // Pre-calculate line endpoints for X and Z axes
        val xMin = centerX - extent
        val xMax = centerX + extent
        val zMin = centerZ - extent
        val zMax = centerZ + extent

        // Get camera frustum bounds for culling (optional optimization)
        val frustumMargin = extent * 1.2f // Slightly larger than grid extent

        // Render primary grid
        renderGridLines(
            camPos = camPos,
            centerY = config.gridYOffset,
            centerX = centerX,
            centerZ = centerZ,
            extent = extent,
            numLines = numLines,
            frustumMargin = frustumMargin,
            minorLineAlpha = minorLineAlpha,
            lineColorMajor = config.majorColor,
            lineColorMinor = config.minorColor
        )

        // Render secondary grid if enabled
        if (config.secondaryGridEnabled) {
            renderGridLines(
                camPos = camPos,
                centerY = config.secondaryGridY,
                centerX = centerX,
                centerZ = centerZ,
                extent = extent,
                numLines = numLines,
                frustumMargin = frustumMargin,
                minorLineAlpha = minorLineAlpha,
                lineColorMajor = config.secondaryGridColor,
                lineColorMinor = config.secondaryGridColor.withAlpha(0.5f)
            )
        }

        // Render snap visualization (before axes, so axes appear on top)
        if (config.snapVisualizationEnabled) {
            renderSnapVisualization(camPos, extent)
        }

        // Render center marker at world origin (before axes, so axes appear on top)
        if (config.showCenterMarker && cameraDistance < config.centerMarkerDistance) {
            renderCenterMarker(cameraDistance)
        }

        // Render origin axes LAST so they appear on top of grid lines
        if (config.showOriginAxes && cameraDistance < 50f) {
            renderOriginAxes(cameraDistance)
        }
    }

    /**
     * Renders grid lines with optional edge fading.
     *
     * @param camPos Camera position
     * @param centerY Y position of the grid plane
     * @param centerX Center X of the grid (snapped to major step)
     * @param centerZ Center Z of the grid (snapped to major step)
     * @param extent Grid extent from center
     * @param numLines Number of lines to render in each direction
     * @param frustumMargin Margin for frustum culling
     * @param minorLineAlpha Alpha value for minor lines (LOD)
     * @param lineColorMajor Color for major grid lines
     * @param lineColorMinor Color for minor grid lines
     */
    private fun renderGridLines(
        camPos: Vector3f,
        centerY: Float,
        centerX: Float,
        centerZ: Float,
        extent: Float,
        numLines: Int,
        frustumMargin: Float,
        minorLineAlpha: Float,
        lineColorMajor: Vector3f,
        lineColorMinor: Vector3f
    ) {
        val xMin = centerX - extent
        val xMax = centerX + extent
        val zMin = centerZ - extent
        val zMax = centerZ + extent

        for (i in -numLines..numLines) {
            val offset = i * config.minorStep

            // Draw Z-aligned lines (constant Z, varying X)
            val worldZ = centerZ + offset
            val isMajorZ = isMajorLine(worldZ, config.majorStep)

            // Frustum culling: skip lines far outside camera view
            if (worldZ < camPos.z - frustumMargin || worldZ > camPos.z + frustumMargin) {
                continue
            }

            if (isMajorZ || minorLineAlpha > 0f) {
                var color = if (isMajorZ) lineColorMajor else lineColorMinor

                // Apply edge fading if enabled
                if (config.edgeFadeEnabled) {
                    val fadeAlpha = calculateEdgeFade(worldZ, centerZ, extent)
                    color = color.withAlpha(fadeAlpha)
                }

                lineStart.set(xMin, centerY, worldZ)
                lineEnd.set(xMax, centerY, worldZ)
                debugRenderer.addLine3D(lineStart, lineEnd, color)
            }

            // Draw X-aligned lines (constant X, varying Z)
            val worldX = centerX + offset
            val isMajorX = isMajorLine(worldX, config.majorStep)

            // Frustum culling: skip lines far outside camera view
            if (worldX < camPos.x - frustumMargin || worldX > camPos.x + frustumMargin) {
                continue
            }

            if (isMajorX || minorLineAlpha > 0f) {
                var color = if (isMajorX) lineColorMajor else lineColorMinor

                // Apply edge fading if enabled
                if (config.edgeFadeEnabled) {
                    val fadeAlpha = calculateEdgeFade(worldX, centerX, extent)
                    color = color.withAlpha(fadeAlpha)
                }

                lineStart.set(worldX, centerY, zMin)
                lineEnd.set(worldX, centerY, zMax)
                debugRenderer.addLine3D(lineStart, lineEnd, color)
            }
        }
    }

    /**
     * Calculates edge fade alpha based on distance from grid center.
     *
     * @param position Current position along the axis
     * @param center Center position of the grid
     * @param extent Grid extent from center
     * @return Alpha value 0.0-1.0 for edge fading
     */
    private fun calculateEdgeFade(position: Float, center: Float, extent: Float): Float {
        val distanceFromCenter = abs(position - center)
        val normalizedDistance = distanceFromCenter / extent

        // Start fading at edgeFadeStart (0.0-1.0)
        if (normalizedDistance <= config.edgeFadeStart) return 1.0f
        if (normalizedDistance >= 1.0f) return 0.0f

        // Smooth fade from edgeFadeStart to edge
        val fadeT = (normalizedDistance - config.edgeFadeStart) / (1.0f - config.edgeFadeStart)
        return 1.0f - smoothstep(fadeT)
    }

    /**
     * Renders a center marker crosshair at world origin.
     *
     * @param cameraDistance Distance from camera to grid plane
     */
    private fun renderCenterMarker(cameraDistance: Float) {
        val markerSize = (2.0f * (config.centerMarkerDistance - cameraDistance) / config.centerMarkerDistance)
            .coerceIn(0.5f, 2.0f)

        // X-axis crosshair (at grid level)
        lineStart.set(-markerSize, 0.0f, 0f)
        lineEnd.set(markerSize, 0.0f, 0f)
        debugRenderer.addLine3D(lineStart, lineEnd, config.centerMarkerColor)

        // Z-axis crosshair (at grid level)
        lineStart.set(0f, 0.0f, -markerSize)
        lineEnd.set(0f, 0.0f, markerSize)
        debugRenderer.addLine3D(lineStart, lineEnd, config.centerMarkerColor)
    }

    /**
     * Renders origin axes at world origin.
     *
     * In Godot style:
     * - X-axis (red) lies flat on the grid plane
     * - Z-axis (blue) lies flat on the grid plane
     * - Y-axis (green) goes straight UP from the grid (not below)
     * - Axes are rendered thicker than grid lines for visibility
     * - Axes are rendered LAST to appear on top of grid lines
     *
     * @param cameraDistance Distance from camera to grid plane
     */
    private fun renderOriginAxes(cameraDistance: Float) {
        val axisLength = (100f * (50f - cameraDistance) / 50f).coerceIn(5f, 100f)
        val thickness = config.originAxesThickness

        // All axes at grid level (Y=0)
        val gridY = 0.0f

        // X-axis (red) - lies flat on grid plane, rendered thick using quad ribbon
        debugRenderer.addThickLineQuad3D(
            from = Vector3f(-axisLength, gridY, 0f),
            to = Vector3f(axisLength, gridY, 0f),
            color = xAxisColor,
            thickness = thickness
        )

        // Z-axis (blue) - lies flat on grid plane, rendered thick using quad ribbon
        debugRenderer.addThickLineQuad3D(
            from = Vector3f(0f, gridY, -axisLength),
            to = Vector3f(0f, gridY, axisLength),
            color = zAxisColor,
            thickness = thickness
        )

        // Y-axis (green) - goes straight UP from grid (not below), rendered thick using quad ribbon
        debugRenderer.addThickLineQuad3D(
            from = Vector3f(0f, gridY, 0f),
            to = Vector3f(0f, axisLength, 0f),
            color = yAxisColor,
            thickness = thickness
        )
    }

    /**
     * Renders snap visualization showing the nearest grid intersection.
     *
     * @param camPos Camera position
     * @param extent Grid extent
     */
    private fun renderSnapVisualization(camPos: Vector3f, extent: Float) {
        // Calculate nearest grid intersection from camera position (projected to X-Z plane)
        val snapX = floor(camPos.x / config.majorStep) * config.majorStep
        val snapZ = floor(camPos.z / config.majorStep) * config.majorStep

        // Only show snap marker when camera is close to the grid
        val cameraDistance = abs(camPos.y)
        if (cameraDistance > 20f) return

        // Draw a small box/cross at the snap point (at grid level)
        val snapSize = 0.3f
        val snapY = 0.0f  // At grid level

        // Draw snap point cross
        lineStart.set(snapX - snapSize, snapY, snapZ)
        lineEnd.set(snapX + snapSize, snapY, snapZ)
        debugRenderer.addLine3D(lineStart, lineEnd, config.snapMarkerColor)

        lineStart.set(snapX, snapY, snapZ - snapSize)
        lineEnd.set(snapX, snapY, snapZ + snapSize)
        debugRenderer.addLine3D(lineStart, lineEnd, config.snapMarkerColor)
    }

    /**
     * Calculates the grid extent based on camera distance.
     *
     * Formula: extent = cameraDistance * tan(fov/2) * padding
     * This ensures the grid always fills the viewport regardless of camera height.
     *
     * @param cameraDistance Distance from camera to grid plane
     * @return Calculated grid extent clamped between min and max
     */
    private fun calculateGridExtent(cameraDistance: Float): Float {
        val clampedDistance = cameraDistance.coerceAtLeast(0f)
        val calculatedExtent = clampedDistance * tanHalfFov * padding
        return calculatedExtent.coerceIn(config.minExtent, config.maxExtent)
    }

    /**
     * Calculates the alpha value for minor lines based on camera distance.
     *
     * Uses smoothstep interpolation between LOD thresholds to prevent popping:
     * - Distance < closeDistance: alpha = 1.0 (fully visible)
     * - Distance > farDistance: alpha = 0.0 (hidden)
     * - Between: smooth interpolation
     *
     * @param cameraDistance Current camera distance from grid
     * @return Alpha value 0.0-1.0 for minor line visibility
     */
    private fun calculateMinorLineAlpha(cameraDistance: Float): Float {
        if (cameraDistance <= config.lodCloseDistance) return 1.0f
        if (cameraDistance >= config.lodFarDistance) return 0.0f

        val t = (cameraDistance - config.lodCloseDistance) / (config.lodFarDistance - config.lodCloseDistance)
        return 1.0f - smoothstep(t)
    }

    /**
     * Smoothstep interpolation function.
     * Provides smooth S-curve interpolation between 0 and 1.
     */
    private fun smoothstep(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
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

/**
 * Extension function to scale a Vector3f color by an alpha factor.
 * This simulates alpha blending for debug line rendering.
 *
 * @param alpha Alpha value 0.0-1.0
 * @return New Vector3f with scaled RGB values
 */
private fun Vector3f.withAlpha(alpha: Float): Vector3f {
    return Vector3f(x * alpha, y * alpha, z * alpha)
}