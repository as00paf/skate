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
 * @param majorStep Spacing between major grid lines (default: 1.0)
 * @param minorStep Spacing between minor grid lines (default: 0.1)
 * @param majorColor Color of major grid lines (default: dark gray)
 * @param minorColor Color of minor grid lines (default: lighter gray)
 * @param minExtent Minimum grid extent when camera is close (default: 10.0)
 * @param maxExtent Maximum grid extent when camera is far (default: 100.0)
 * @param lodCloseDistance Distance where minor lines start fading (default: 5.0)
 * @param lodFarDistance Distance where minor lines are fully hidden (default: 20.0)
 * @param showGrid Toggle grid visibility (default: true)
 * @param showOriginAxes Toggle origin axes visibility (default: true)
 * @param gridYOffset Y offset to prevent Z-fighting with ground plane (default: -0.1f)
 *     Negative values place grid below world origin. Adjust if grid conflicts with terrain.
 */
data class GridConfig(
    var majorStep: Float = 1.0f,
    var minorStep: Float = 0.1f,
    var majorColor: Vector3f = Vector3f(0.4f, 0.4f, 0.4f),
    var minorColor: Vector3f = Vector3f(0.25f, 0.25f, 0.25f),
    var minExtent: Float = 10.0f,
    var maxExtent: Float = 100.0f,
    var lodCloseDistance: Float = 5.0f,
    var lodFarDistance: Float = 20.0f,
    var showGrid: Boolean = true,
    var showOriginAxes: Boolean = true,
    var gridYOffset: Float = -0.1f
) {
    /**
     * Resets all configuration values to their defaults.
     */
    fun resetToDefaults() {
        majorStep = 1.0f
        minorStep = 0.1f
        majorColor = Vector3f(0.4f, 0.4f, 0.4f)
        minorColor = Vector3f(0.25f, 0.25f, 0.25f)
        minExtent = 10.0f
        maxExtent = 100.0f
        lodCloseDistance = 5.0f
        lodFarDistance = 20.0f
        showGrid = true
        showOriginAxes = true
        gridYOffset = -0.1f
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
    private val config: GridConfig = GridConfig(),
    private val stringManager: StringManager
) : System(priority = ExecutionPriority.LATE) {

    // Cached vectors to reduce allocations
    private val tempVec1 = Vector3f()
    private val tempVec2 = Vector3f()
    private val lineStart = Vector3f()
    private val lineEnd = Vector3f()

    // Cached constants
    private val fovRadians = Math.toRadians(22.5).toFloat() // 45/2 degrees
    private val tanHalfFov = tan(fovRadians)
    private val padding = 1.5f

    // Origin axes colors (cached to avoid allocations)
    private val xAxisColor = Vector3f(1f, 0.2f, 0.2f)
    private val yAxisColor = Vector3f(0.2f, 1f, 0.2f)
    private val zAxisColor = Vector3f(0.2f, 0.2f, 1f)

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
                val color = if (isMajorZ) config.majorColor else config.minorColor
                lineStart.set(xMin, config.gridYOffset, worldZ)
                lineEnd.set(xMax, config.gridYOffset, worldZ)
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
                val color = if (isMajorX) config.majorColor else config.minorColor
                lineStart.set(worldX, config.gridYOffset, zMin)
                lineEnd.set(worldX, config.gridYOffset, zMax)
                debugRenderer.addLine3D(lineStart, lineEnd, color)
            }
        }

        // Render origin axes when camera is nearby
        if (config.showOriginAxes && cameraDistance < 50f) {
            val axisLength = (20f * (50f - cameraDistance) / 50f).coerceIn(5f, 20f)

            // X-axis (red)
            lineStart.set(-axisLength, 0.001f, 0f)
            lineEnd.set(axisLength, 0.001f, 0f)
            debugRenderer.addLine3D(lineStart, lineEnd, xAxisColor)

            // Z-axis (blue) - note: in X-Z plane, Z is depth
            lineStart.set(0f, 0.001f, -axisLength)
            lineEnd.set(0f, 0.001f, axisLength)
            debugRenderer.addLine3D(lineStart, lineEnd, zAxisColor)

            // Y-axis (green) - vertical axis
            lineStart.set(0f, -axisLength, 0.001f)
            lineEnd.set(0f, axisLength, 0.001f)
            debugRenderer.addLine3D(lineStart, lineEnd, yAxisColor)
        }
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

        ImGui.separator()

        // Grid spacing settings
        ImGui.text(stringManager.getString("lbl.grid.spacing"))
        ImGui.pushItemWidth(120f)

        val majorStepArr = floatArrayOf(config.majorStep)
        if (ImGui.dragFloat(stringManager.getString("lbl.grid.major_step"), majorStepArr, 0.1f, 0.1f, 10.0f, "%.1f")) {
            config.majorStep = majorStepArr[0].coerceAtLeast(0.1f)
        }

        val minorStepArr = floatArrayOf(config.minorStep)
        if (ImGui.dragFloat(stringManager.getString("lbl.grid.minor_step"), minorStepArr, 0.01f, 0.01f, 1.0f, "%.2f")) {
            config.minorStep = minorStepArr[0].coerceIn(0.01f, config.majorStep)
        }
        ImGui.popItemWidth()

        ImGui.separator()

        // LOD settings
        ImGui.text(stringManager.getString("lbl.grid.lod_settings"))
        ImGui.pushItemWidth(120f)

        val lodCloseArr = floatArrayOf(config.lodCloseDistance)
        if (ImGui.dragFloat(stringManager.getString("lbl.grid.lod_close"), lodCloseArr, 0.1f, 0.1f, 50.0f, "%.1f")) {
            config.lodCloseDistance = lodCloseArr[0].coerceAtMost(config.lodFarDistance)
        }

        val lodFarArr = floatArrayOf(config.lodFarDistance)
        if (ImGui.dragFloat(stringManager.getString("lbl.grid.lod_far"), lodFarArr, 0.1f, 0.1f, 50.0f, "%.1f")) {
            config.lodFarDistance = lodFarArr[0].coerceAtLeast(config.lodCloseDistance)
        }
        ImGui.popItemWidth()

        ImGui.separator()

        // Extent settings
        ImGui.text(stringManager.getString("lbl.grid.extent_settings"))
        ImGui.pushItemWidth(120f)

        val minExtentArr = floatArrayOf(config.minExtent)
        if (ImGui.dragFloat(stringManager.getString("lbl.grid.min_extent"), minExtentArr, 1.0f, 1.0f, 50.0f, "%.1f")) {
            config.minExtent = minExtentArr[0].coerceAtMost(config.maxExtent)
        }

        val maxExtentArr = floatArrayOf(config.maxExtent)
        if (ImGui.dragFloat(stringManager.getString("lbl.grid.max_extent"), maxExtentArr, 1.0f, 1.0f, 200.0f, "%.1f")) {
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
        if (ImGui.dragFloat(stringManager.getString("lbl.grid.y_offset"), yOffsetArr, 0.01f, -1.0f, 0.0f, "%.2f")) {
            config.gridYOffset = yOffsetArr[0].coerceIn(-1.0f, 0.0f)
        }
        ImGui.popItemWidth()

        ImGui.separator()

        // Reset button
        if (ImGui.button(stringManager.getString("lbl.grid.reset_to_defaults"))) {
            config.resetToDefaults()
        }
    }
}