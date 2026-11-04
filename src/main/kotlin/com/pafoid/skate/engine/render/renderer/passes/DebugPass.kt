package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.GroundDepthPull
import com.pafoid.skate.engine.render.utils.withBlendState
import com.pafoid.skate.engine.render.utils.withCullFace
import com.pafoid.skate.engine.render.utils.withDepthFunc
import com.pafoid.skate.engine.render.utils.withDepthMask
import com.pafoid.skate.engine.render.utils.withDepthTest
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_LEQUAL
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.tan

/**
 * Debug visualization render pass.
 * 
 * Renders debug lines, triangles, and other visualization aids (grid, physics colliders, editor camera and light
 * visuals) after post-processing. They depth-test against the scene, so models hide what is behind them.
 * 
 * @param debugRenderer The debug renderer for drawing lines and shapes
 * @param bindTarget Binds the presented render target (the viewport FBO or the default framebuffer) and its
 * viewport. Called before drawing because earlier passes (shadow, picking) bind their own targets. Debug geometry is
 * drawn after post-processing, so it is not tonemapped, and it depth-tests against the scene depth that the geometry
 * pass left in that target.
 * @param targetHeight Height in pixels of the presented target, for the grid's depth pull.
 * @param drawState Runs the draw inside the GL state debug geometry needs ([withDebugDrawState]); injectable so the
 * pass can be tested without a GL context.
 */
class DebugPass(
    private val debugRenderer: DebugRenderer,
    private val cameraManager: CameraManager,
    private val bindTarget: () -> Unit = {},
    private val drawState: (draw: () -> Unit) -> Unit = ::withDebugDrawState,
    private val targetHeight: () -> Int = { 1080 },
) : BaseRenderPass() {

    override val name: String = "DebugPass"
    override val description: String = "Renders debug visualization (grid lines, physics, gizmos, etc.)"

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

    override fun prepare() {
        debugRenderer.beginFrame()
    }

    override fun execute(scene: Scene) {
        bindTarget()
        scene.getComponent<GridLines>()?.let { renderGridLines(scene, it) }

        // Pixel size drives the grid's grazing-angle depth pull (GroundDepthPull, REN-19)
        val camera = cameraManager.camera
        debugRenderer.pixelAngle = GroundDepthPull.pixelAngle(Math.toRadians(camera.fov.toDouble()).toFloat() * camera.zoom, targetHeight())
        drawState { debugRenderer.draw() }
    }

    private fun renderGridLines(scene: Scene, gridLines: GridLines) {
        if (!gridLines.showGrid || scene.isRunning) return

        val camPos = cameraManager.transform.worldMatrix.getTranslation(Vector3f())

        val cameraDistance = abs(camPos.y)
        val extent = calculateGridExtent(gridLines.minExtent, gridLines.maxExtent, cameraDistance)
        val minorLineAlpha =
            calculateMinorLineAlpha(gridLines.lodCloseDistance, gridLines.lodFarDistance, cameraDistance)

        // Calculate grid center (snapped to major step)
        val centerX = floor(camPos.x / gridLines.majorStep) * gridLines.majorStep
        val centerZ = floor(camPos.z / gridLines.majorStep) * gridLines.majorStep
        val numLines = (extent / gridLines.minorStep).toInt()

        // Get camera frustum bounds for culling (optional optimization)
        val frustumMargin = extent * 1.2f // Slightly larger than grid extent

        // Render primary grid
        renderGridLines(
            minorStep = gridLines.minorStep,
            majorStep = gridLines.majorStep,
            edgeFadeEnabled = gridLines.edgeFadeEnabled,
            edgeFadeStart = gridLines.edgeFadeStart,
            camPos = camPos,
            centerY = gridLines.gridYOffset,
            centerX = centerX,
            centerZ = centerZ,
            extent = extent,
            numLines = numLines,
            frustumMargin = frustumMargin,
            minorLineAlpha = minorLineAlpha,
            lineColorMajor = gridLines.majorColor,
            lineColorMinor = gridLines.minorColor
        )

        // Render secondary grid if enabled
        if (gridLines.secondaryGridEnabled) {
            renderGridLines(
                minorStep = gridLines.minorStep,
                majorStep = gridLines.majorStep,
                edgeFadeEnabled = gridLines.edgeFadeEnabled,
                edgeFadeStart = gridLines.edgeFadeStart,
                camPos = camPos,
                centerY = gridLines.secondaryGridY,
                centerX = centerX,
                centerZ = centerZ,
                extent = extent,
                numLines = numLines,
                frustumMargin = frustumMargin,
                minorLineAlpha = minorLineAlpha,
                lineColorMajor = gridLines.secondaryGridColor,
                lineColorMinor = gridLines.secondaryGridColor.withAlpha(0.5f),
            )
        }

        // Render snap visualization (before axes, so axes appear on top)
        if (gridLines.snapVisualizationEnabled) {
            renderSnapVisualization(gridLines.majorStep, gridLines.snapMarkerColor, camPos, extent)
        }

        // Render center marker at world origin (before axes, so axes appear on top)
        if (gridLines.showCenterMarker && cameraDistance < gridLines.centerMarkerDistance) {
            renderCenterMarker(gridLines.centerMarkerColor, gridLines.centerMarkerDistance, cameraDistance)
        }

        // Render origin axes LAST so they appear on top of grid lines
        if (gridLines.showOriginAxes && cameraDistance < 50f) {
            renderOriginAxes(gridLines.originAxesThickness, cameraDistance)
        }
    }

    private fun renderGridLines(
        minorStep: Float,
        majorStep: Float,
        edgeFadeEnabled: Boolean,
        edgeFadeStart: Float,
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
            val offset = i * minorStep

            // Draw Z-aligned lines (constant Z, varying X)
            val worldZ = centerZ + offset
            val isMajorZ = isMajorLine(worldZ, majorStep)

            // Frustum culling: skip lines far outside camera view
            if (worldZ < camPos.z - frustumMargin || worldZ > camPos.z + frustumMargin) {
                continue
            }

            if (isMajorZ || minorLineAlpha > 0f) {
                var color = if (isMajorZ) lineColorMajor else lineColorMinor

                // Apply edge fading if enabled
                if (edgeFadeEnabled) {
                    val fadeAlpha = calculateEdgeFade(edgeFadeStart, worldZ, centerZ, extent)
                    color = color.withAlpha(fadeAlpha)
                }

                lineStart.set(xMin, centerY, worldZ)
                lineEnd.set(xMax, centerY, worldZ)
                debugRenderer.addGroundLine3D(lineStart, lineEnd, color)
            }

            // Draw X-aligned lines (constant X, varying Z)
            val worldX = centerX + offset
            val isMajorX = isMajorLine(worldX, majorStep)

            // Frustum culling: skip lines far outside camera view
            if (worldX < camPos.x - frustumMargin || worldX > camPos.x + frustumMargin) {
                continue
            }

            if (isMajorX || minorLineAlpha > 0f) {
                var color = if (isMajorX) lineColorMajor else lineColorMinor

                // Apply edge fading if enabled
                if (edgeFadeEnabled) {
                    val fadeAlpha = calculateEdgeFade(edgeFadeStart, worldX, centerX, extent)
                    color = color.withAlpha(fadeAlpha)
                }

                lineStart.set(worldX, centerY, zMin)
                lineEnd.set(worldX, centerY, zMax)
                debugRenderer.addGroundLine3D(lineStart, lineEnd, color)
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
    private fun calculateEdgeFade(edgeFadeStart: Float, position: Float, center: Float, extent: Float): Float {
        val distanceFromCenter = abs(position - center)
        val normalizedDistance = distanceFromCenter / extent

        // Start fading at edgeFadeStart (0.0-1.0)
        if (normalizedDistance <= edgeFadeStart) return 1.0f
        if (normalizedDistance >= 1.0f) return 0.0f

        // Smooth fade from edgeFadeStart to edge
        val fadeT = (normalizedDistance - edgeFadeStart) / (1.0f - edgeFadeStart)
        return 1.0f - smoothstep(fadeT)
    }

    /**
     * Renders a center marker crosshair at world origin.
     *
     * @param cameraDistance Distance from camera to grid plane
     */
    private fun renderCenterMarker(centerMarkerColor: Vector3f, centerMarkerDistance: Float, cameraDistance: Float) {
        val markerSize = (2.0f * (centerMarkerDistance - cameraDistance) / centerMarkerDistance)
            .coerceIn(0.5f, 2.0f)

        // X-axis crosshair (at grid level)
        lineStart.set(-markerSize, 0.0f, 0f)
        lineEnd.set(markerSize, 0.0f, 0f)
        debugRenderer.addGroundLine3D(lineStart, lineEnd, centerMarkerColor)

        // Z-axis crosshair (at grid level)
        lineStart.set(0f, 0.0f, -markerSize)
        lineEnd.set(0f, 0.0f, markerSize)
        debugRenderer.addGroundLine3D(lineStart, lineEnd, centerMarkerColor)
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
    private fun renderOriginAxes(thickness: Float, cameraDistance: Float) {
        val axisLength = (100f * (50f - cameraDistance) / 50f).coerceIn(5f, 100f)

        // All axes at grid level (Y=0)
        val gridY = 0.0f

        // X-axis (red) - lies flat on grid plane, rendered thick using quad ribbon
        debugRenderer.addThickLineQuad3D(
            from = Vector3f(-axisLength, gridY, 0f),
            to = Vector3f(axisLength, gridY, 0f),
            color = xAxisColor,
            thickness = thickness,
            ground = true,
        )

        // Z-axis (blue) - lies flat on grid plane, rendered thick using quad ribbon
        debugRenderer.addThickLineQuad3D(
            from = Vector3f(0f, gridY, -axisLength),
            to = Vector3f(0f, gridY, axisLength),
            color = zAxisColor,
            thickness = thickness,
            ground = true,
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
    private fun renderSnapVisualization(majorStep: Float, snapMarkerColor: Vector3f, camPos: Vector3f, extent: Float) {
        // Calculate nearest grid intersection from camera position (projected to X-Z plane)
        val snapX = floor(camPos.x / majorStep) * majorStep
        val snapZ = floor(camPos.z / majorStep) * majorStep

        // Only show snap marker when camera is close to the grid
        val cameraDistance = abs(camPos.y)
        if (cameraDistance > 20f) return

        // Draw a small box/cross at the snap point (at grid level)
        val snapSize = 0.3f
        val snapY = 0.0f  // At grid level

        // Draw snap point cross
        lineStart.set(snapX - snapSize, snapY, snapZ)
        lineEnd.set(snapX + snapSize, snapY, snapZ)
        debugRenderer.addLine3D(lineStart, lineEnd, snapMarkerColor)

        lineStart.set(snapX, snapY, snapZ - snapSize)
        lineEnd.set(snapX, snapY, snapZ + snapSize)
        debugRenderer.addLine3D(lineStart, lineEnd, snapMarkerColor)
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
    private fun calculateGridExtent(minExtent: Float, maxExtent: Float, cameraDistance: Float): Float {
        val clampedDistance = cameraDistance.coerceAtLeast(0f)
        val calculatedExtent = clampedDistance * tanHalfFov * padding
        return calculatedExtent.coerceIn(minExtent, maxExtent)
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
    private fun calculateMinorLineAlpha(lodCloseDistance: Float, lodFarDistance: Float, cameraDistance: Float): Float {
        if (cameraDistance <= lodCloseDistance) return 1.0f
        if (cameraDistance >= lodFarDistance) return 0.0f

        val t = (cameraDistance - lodCloseDistance) / (lodFarDistance - lodCloseDistance)
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

    /**
     * Extension function to scale a Vector3f color by an alpha factor.
     * This simulates alpha blending for debug line rendering.
     *
     * @param alpha Alpha value 0.0-1.0
     * @return New Vector3f with scaled RGB values
     */
    private fun Vector3f.withAlpha(alpha: Float): Vector3f { // TODO: move
        return Vector3f(x * alpha, y * alpha, z * alpha)
    }
}

/**
 * GL state for debug geometry, set explicitly instead of inherited from the previous pass: depth test on (LEQUAL, so
 * lines on a surface win ties) with depth writes, no blending, and no face culling so thick-line quads show from both
 * sides.
 */
fun withDebugDrawState(draw: () -> Unit) {
    withDepthTest(true) {
        withDepthFunc(GL_LEQUAL) {
            withDepthMask(true) {
                withBlendState(false) {
                    withCullFace(false) {
                        draw()
                    }
                }
            }
        }
    }
}
