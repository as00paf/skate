package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import imgui.ImGui
import imgui.type.ImBoolean
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.tan

/**
 * Mutable configuration data class for the GridLines system.
 *
 * @param majorStep Spacing between major grid lines (default: 10.0 m - Godot default)
 * @param minorStep Spacing between minor grid lines (default: 1.0 m - Godot default)
 * @param majorColor Color of major grid lines (default: dark gray 0.25)
 * @param minorColor Color of minor grid lines (default: very dark gray 0.15)
 * @param minExtent Minimum grid extent when camera is close (default: 50.0 m)
 * @param maxExtent Maximum grid extent when camera is far (default: 500.0 m)
 * @param lodCloseDistance Distance where minor lines start fading (default: 50.0 m)
 * @param lodFarDistance Distance where minor lines are fully hidden (default: 200.0 m)
 * @param showGrid Toggle grid visibility (default: true)
 * @param showOriginAxes Toggle origin axes visibility (default: true)
 * @param gridYOffset Y offset for the grid plane (default: 0.0f)
 *     Grid is rendered at this Y position. Default is world origin (0.0) to align with origin axes.
 *     Adjust if you need grid at a different height (e.g., -0.01f to prevent Z-fighting with ground plane).
 * @param showCenterMarker Toggle center marker visibility (default: false - Godot doesn't have this)
 * @param centerMarkerColor Color of the center marker crosshair (default: yellow)
 * @param centerMarkerDistance Maximum camera distance to show center marker (default: 30.0)
 * @param edgeFadeEnabled Enable fading at grid edges (default: false - Godot doesn't fade edges)
 * @param edgeFadeStart Distance from center where fading starts (0.0-1.0, default: 0.7f)
 * @param secondaryGridEnabled Enable secondary grid plane (default: false)
 * @param secondaryGridY Y position of secondary grid (default: 2.0f)
 * @param secondaryGridColor Color of secondary grid lines (default: cyan)
 * @param snapVisualizationEnabled Enable grid snap visualization (default: false - Godot doesn't have this)
 * @param snapMarkerColor Color of snap point marker (default: bright green)
 * @param originAxesThickness Thickness of origin axis lines when using quad-based rendering (default: 0.08f)
 */
data class GridConfig(
    var majorStep: Float = 10.0f,  // Godot default: 10m spacing for major lines
    var minorStep: Float = 1.0f,   // Godot default: 1m spacing for minor lines
    var majorColor: Vector3f = Vector3f(0.25f, 0.25f, 0.25f),  // Even darker to match Godot's subtle grid
    var minorColor: Vector3f = Vector3f(0.15f, 0.15f, 0.15f),  // Very subtle minor lines
    var minExtent: Float = 50.0f,  // Godot shows a very large grid by default
    var maxExtent: Float = 500.0f,  // Much larger maximum for far camera distances
    var lodCloseDistance: Float = 50.0f,  // Minor lines visible much further (Godot: ~50m)
    var lodFarDistance: Float = 200.0f,  // Minor lines fade out at very far distance
    var showGrid: Boolean = true,
    var showOriginAxes: Boolean = true,
    var gridYOffset: Float = 0.0f,
    var showCenterMarker: Boolean = false,  // Disabled by default (Godot doesn't have this)
    var centerMarkerColor: Vector3f = Vector3f(1.0f, 1.0f, 0.0f),
    var centerMarkerDistance: Float = 30.0f,
    var edgeFadeEnabled: Boolean = false,  // Disabled - Godot doesn't fade grid edges
    var edgeFadeStart: Float = 0.7f,
    var secondaryGridEnabled: Boolean = false,
    var secondaryGridY: Float = 2.0f,
    var secondaryGridColor: Vector3f = Vector3f(0.0f, 0.8f, 0.8f),
    var snapVisualizationEnabled: Boolean = false,  // Disabled by default (Godot doesn't have this)
    var snapMarkerColor: Vector3f = Vector3f(0.0f, 1.0f, 0.0f),
    var originAxesThickness: Float = 0.04f  // Thickness of axis lines (quad-based rendering)
) {
    /**
     * Resets all configuration values to their defaults.
     */
    fun resetToDefaults() {
        majorStep = 10.0f
        minorStep = 1.0f
        majorColor = Vector3f(0.25f, 0.25f, 0.25f)
        minorColor = Vector3f(0.15f, 0.15f, 0.15f)
        minExtent = 50.0f
        maxExtent = 500.0f
        lodCloseDistance = 50.0f
        lodFarDistance = 200.0f
        showGrid = true
        showOriginAxes = true
        gridYOffset = 0.0f
        showCenterMarker = false
        centerMarkerColor = Vector3f(1.0f, 1.0f, 0.0f)
        centerMarkerDistance = 30.0f
        edgeFadeEnabled = false
        edgeFadeStart = 0.7f
        secondaryGridEnabled = false
        secondaryGridY = 2.0f
        secondaryGridColor = Vector3f(0.0f, 0.8f, 0.8f)
        snapVisualizationEnabled = false
        snapMarkerColor = Vector3f(0.0f, 1.0f, 0.0f)
        originAxesThickness = 0.04f
    }
}

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
 * - ImGui configuration panel for real-time tuning
 *
 * @param debugRenderer Renderer for debug lines
 * @param sceneManager Manager for accessing current scene and camera
 * @param config Grid configuration (optional, uses defaults if not provided)
 * @param stringManager String manager for localization
 */
class GridLines(
    private val debugRenderer: DebugRenderer,
    private val sceneManager: SceneManager,
    private val stringManager: StringManager,
) : System(priority = ExecutionPriority.LATE) {

    private val config: GridConfig = GridConfig()

    // Cached vectors to reduce allocations
    private val tempVec1 = Vector3f()
    private val tempVec2 = Vector3f()
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

    override fun editorUpdate(dt: Float) {
        if (!config.showGrid) return

        val scene = sceneManager.currentScene ?: return
        val camPos = scene.camera.position

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

    /**
     * Renders the ImGui configuration panel for the grid system.
     *
     * Provides real-time controls for:
     * - Grid visibility and origin axes toggle
     * - Major/minor step size sliders
     * - LOD distance thresholds
     * - Color pickers for major/minor lines
     * - Grid Y offset for Z-fighting adjustment
     * - Reset to defaults button
     */
    override fun imgui() {
        ImGui.text(stringManager.getString("lbl.grid.header"))
        ImGui.separator()

        // Visibility toggles
        val showGrid = ImBoolean(config.showGrid)
        if (ImGui.checkbox(stringManager.getString("lbl.grid.show_grid"), showGrid)) {
            config.showGrid = showGrid.get()
        }

        val showOriginAxes = ImBoolean(config.showOriginAxes)
        if (ImGui.checkbox(stringManager.getString("lbl.grid.show_origin_axes"), showOriginAxes)) {
            config.showOriginAxes = showOriginAxes.get()
        }

        // Axis thickness slider (only if axes are enabled)
        if (config.showOriginAxes) {
            ImGui.pushItemWidth(120f)
            val thicknessArr = floatArrayOf(config.originAxesThickness)
            if (ImGui.sliderFloat("Axis Thickness", thicknessArr, 0.02f, 0.2f, "%.3f")) {
                config.originAxesThickness = thicknessArr[0]
            }
            ImGui.popItemWidth()
        }

        ImGui.separator()

        // Grid spacing settings
        ImGui.text(stringManager.getString("lbl.grid.spacing"))
        ImGui.pushItemWidth(120f)

        val majorStepArr = floatArrayOf(config.majorStep)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.grid.major_step"),
                majorStepArr,
                0.1f,
                0.1f,
                10.0f,
                "%.1f m"
            )
        ) {
            config.majorStep = majorStepArr[0].coerceAtLeast(0.1f)
        }

        val minorStepArr = floatArrayOf(config.minorStep)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.grid.minor_step"),
                minorStepArr,
                0.01f,
                0.01f,
                1.0f,
                "%.2f m"
            )
        ) {
            config.minorStep = minorStepArr[0].coerceIn(0.01f, config.majorStep)
        }
        ImGui.popItemWidth()

        ImGui.separator()

        // LOD settings
        ImGui.text(stringManager.getString("lbl.grid.lod_settings"))
        ImGui.pushItemWidth(120f)

        val lodCloseArr = floatArrayOf(config.lodCloseDistance)
        if (ImGui.dragFloat(stringManager.getString("lbl.grid.lod_close"), lodCloseArr, 0.1f, 0.1f, 50.0f, "%.1f m")) {
            config.lodCloseDistance = lodCloseArr[0].coerceAtMost(config.lodFarDistance)
        }

        val lodFarArr = floatArrayOf(config.lodFarDistance)
        if (ImGui.dragFloat(stringManager.getString("lbl.grid.lod_far"), lodFarArr, 0.1f, 0.1f, 50.0f, "%.1f m")) {
            config.lodFarDistance = lodFarArr[0].coerceAtLeast(config.lodCloseDistance)
        }
        ImGui.popItemWidth()

        ImGui.separator()

        // Extent settings
        ImGui.text(stringManager.getString("lbl.grid.extent_settings"))
        ImGui.pushItemWidth(120f)

        val minExtentArr = floatArrayOf(config.minExtent)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.grid.min_extent"),
                minExtentArr,
                1.0f,
                1.0f,
                50.0f,
                "%.1f m"
            )
        ) {
            config.minExtent = minExtentArr[0].coerceAtMost(config.maxExtent)
        }

        val maxExtentArr = floatArrayOf(config.maxExtent)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.grid.max_extent"),
                maxExtentArr,
                1.0f,
                1.0f,
                200.0f,
                "%.1f m"
            )
        ) {
            config.maxExtent = maxExtentArr[0].coerceAtLeast(config.minExtent)
        }
        ImGui.popItemWidth()

        ImGui.separator()

        // Color settings
        ImGui.text(stringManager.getString("lbl.grid.colors"))

        val majorColorArr = floatArrayOf(config.majorColor.x, config.majorColor.y, config.majorColor.z)
        if (ImGui.colorEdit3(stringManager.getString("lbl.grid.major_color"), majorColorArr)) {
            config.majorColor.set(majorColorArr[0], majorColorArr[1], majorColorArr[2])
        }

        val minorColorArr = floatArrayOf(config.minorColor.x, config.minorColor.y, config.minorColor.z)
        if (ImGui.colorEdit3(stringManager.getString("lbl.grid.minor_color"), minorColorArr)) {
            config.minorColor.set(minorColorArr[0], minorColorArr[1], minorColorArr[2])
        }

        ImGui.separator()

        // Z-fighting offset
        ImGui.text(stringManager.getString("lbl.grid.z_fighting"))
        ImGui.pushItemWidth(120f)

        val yOffsetArr = floatArrayOf(config.gridYOffset)
        if (ImGui.dragFloat(stringManager.getString("lbl.grid.y_offset"), yOffsetArr, 0.01f, -1.0f, 0.0f, "%.2f m")) {
            config.gridYOffset = yOffsetArr[0].coerceIn(-1.0f, 0.0f)
        }
        ImGui.popItemWidth()

        ImGui.separator()

        // Advanced features
        ImGui.text(stringManager.getString("lbl.grid.advanced"))

        // Center marker
        val showCenterMarker = ImBoolean(config.showCenterMarker)
        if (ImGui.checkbox(stringManager.getString("lbl.grid.show_center_marker"), showCenterMarker)) {
            config.showCenterMarker = showCenterMarker.get()
        }

        val centerMarkerColorArr =
            floatArrayOf(config.centerMarkerColor.x, config.centerMarkerColor.y, config.centerMarkerColor.z)
        if (ImGui.colorEdit3(stringManager.getString("lbl.grid.center_marker_color"), centerMarkerColorArr)) {
            config.centerMarkerColor.set(centerMarkerColorArr[0], centerMarkerColorArr[1], centerMarkerColorArr[2])
        }

        val centerMarkerDistArr = floatArrayOf(config.centerMarkerDistance)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.grid.center_marker_distance"),
                centerMarkerDistArr,
                1.0f,
                5.0f,
                100.0f,
                "%.0f m"
            )
        ) {
            config.centerMarkerDistance = centerMarkerDistArr[0].coerceAtLeast(5.0f)
        }

        ImGui.separator()

        // Edge fading
        val edgeFadeEnabled = ImBoolean(config.edgeFadeEnabled)
        if (ImGui.checkbox(stringManager.getString("lbl.grid.edge_fade_enabled"), edgeFadeEnabled)) {
            config.edgeFadeEnabled = edgeFadeEnabled.get()
        }

        val edgeFadeStartArr = floatArrayOf(config.edgeFadeStart)
        ImGui.pushItemWidth(120f)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.grid.edge_fade_start"),
                edgeFadeStartArr,
                0.01f,
                0.0f,
                1.0f,
                "%.2f"
            )
        ) {
            config.edgeFadeStart = edgeFadeStartArr[0].coerceIn(0.0f, 1.0f)
        }
        ImGui.popItemWidth()

        ImGui.separator()

        // Secondary grid
        val secondaryGridEnabled = ImBoolean(config.secondaryGridEnabled)
        if (ImGui.checkbox(stringManager.getString("lbl.grid.secondary_grid_enabled"), secondaryGridEnabled)) {
            config.secondaryGridEnabled = secondaryGridEnabled.get()
        }

        val secondaryGridYArr = floatArrayOf(config.secondaryGridY)
        ImGui.pushItemWidth(120f)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.grid.secondary_grid_y"),
                secondaryGridYArr,
                0.1f,
                -10.0f,
                10.0f,
                "%.1f m"
            )
        ) {
            config.secondaryGridY = secondaryGridYArr[0]
        }
        ImGui.popItemWidth()

        val secondaryGridColorArr =
            floatArrayOf(config.secondaryGridColor.x, config.secondaryGridColor.y, config.secondaryGridColor.z)
        if (ImGui.colorEdit3(stringManager.getString("lbl.grid.secondary_grid_color"), secondaryGridColorArr)) {
            config.secondaryGridColor.set(secondaryGridColorArr[0], secondaryGridColorArr[1], secondaryGridColorArr[2])
        }

        ImGui.separator()

        // Snap visualization
        val snapVisEnabled = ImBoolean(config.snapVisualizationEnabled)
        if (ImGui.checkbox(stringManager.getString("lbl.grid.snap_visualization_enabled"), snapVisEnabled)) {
            config.snapVisualizationEnabled = snapVisEnabled.get()
        }

        val snapMarkerColorArr =
            floatArrayOf(config.snapMarkerColor.x, config.snapMarkerColor.y, config.snapMarkerColor.z)
        if (ImGui.colorEdit3(stringManager.getString("lbl.grid.snap_marker_color"), snapMarkerColorArr)) {
            config.snapMarkerColor.set(snapMarkerColorArr[0], snapMarkerColorArr[1], snapMarkerColorArr[2])
        }

        ImGui.separator()

        // Reset button
        if (ImGui.button(stringManager.getString("lbl.grid.reset_to_defaults"))) {
            config.resetToDefaults()
        }
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