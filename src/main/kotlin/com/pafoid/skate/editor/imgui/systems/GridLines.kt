package com.pafoid.skate.editor.imgui.systems

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.GridLines
import imgui.ImGui
import imgui.type.ImBoolean


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
fun GridLines.imgui(stringManager: StringManager) {
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