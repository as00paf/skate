package com.pafoid.skate.editor.imgui.systems

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.components.GridLines
import imgui.ImGui
import imgui.type.ImBoolean

fun GridLines.imgui(stringManager: StringManager) {
    ImGui.text(stringManager.getString("lbl.grid.header"))
    ImGui.separator()

    // Visibility toggles
    val showGrid = ImBoolean(showGrid)
    if (ImGui.checkbox(stringManager.getString("lbl.grid.show_grid"), showGrid)) {
        this.showGrid = showGrid.get()
    }

    val showOriginAxes = ImBoolean(showOriginAxes)
    if (ImGui.checkbox(stringManager.getString("lbl.grid.show_origin_axes"), showOriginAxes)) {
        this.showOriginAxes = showOriginAxes.get()
    }

    // Axis thickness slider (only if axes are enabled)
    if (this.showOriginAxes) {
        ImGui.pushItemWidth(120f)
        val thicknessArr = floatArrayOf(originAxesThickness)
        if (ImGui.sliderFloat("Axis Thickness", thicknessArr, 0.02f, 0.2f, "%.3f")) {
            originAxesThickness = thicknessArr[0]
        }
        ImGui.popItemWidth()
    }

    ImGui.separator()

    // Grid spacing settings
    ImGui.text(stringManager.getString("lbl.grid.spacing"))
    ImGui.pushItemWidth(120f)

    val majorStepArr = floatArrayOf(majorStep)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.grid.major_step"),
            majorStepArr,
            0.1f,
            0.1f,
            10.0f,
            "%.1f m"
        )
    ) {
        majorStep = majorStepArr[0].coerceAtLeast(0.1f)
    }

    val minorStepArr = floatArrayOf(minorStep)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.grid.minor_step"),
            minorStepArr,
            0.01f,
            0.01f,
            1.0f,
            "%.2f m"
        )
    ) {
        minorStep = minorStepArr[0].coerceIn(0.01f, majorStep)
    }
    ImGui.popItemWidth()

    ImGui.separator()

    // LOD settings
    ImGui.text(stringManager.getString("lbl.grid.lod_settings"))
    ImGui.pushItemWidth(120f)

    val lodCloseArr = floatArrayOf(lodCloseDistance)
    if (ImGui.dragFloat(stringManager.getString("lbl.grid.lod_close"), lodCloseArr, 0.1f, 0.1f, 50.0f, "%.1f m")) {
        lodCloseDistance = lodCloseArr[0].coerceAtMost(lodFarDistance)
    }

    val lodFarArr = floatArrayOf(lodFarDistance)
    if (ImGui.dragFloat(stringManager.getString("lbl.grid.lod_far"), lodFarArr, 0.1f, 0.1f, 50.0f, "%.1f m")) {
        lodFarDistance = lodFarArr[0].coerceAtLeast(lodCloseDistance)
    }
    ImGui.popItemWidth()

    ImGui.separator()

    // Extent settings
    ImGui.text(stringManager.getString("lbl.grid.extent_settings"))
    ImGui.pushItemWidth(120f)

    val minExtentArr = floatArrayOf(minExtent)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.grid.min_extent"),
            minExtentArr,
            1.0f,
            1.0f,
            50.0f,
            "%.1f m"
        )
    ) {
        minExtent = minExtentArr[0].coerceAtMost(maxExtent)
    }

    val maxExtentArr = floatArrayOf(maxExtent)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.grid.max_extent"),
            maxExtentArr,
            1.0f,
            1.0f,
            200.0f,
            "%.1f m"
        )
    ) {
        maxExtent = maxExtentArr[0].coerceAtLeast(minExtent)
    }
    ImGui.popItemWidth()

    ImGui.separator()

    // Color settings
    ImGui.text(stringManager.getString("lbl.grid.colors"))

    val majorColorArr = floatArrayOf(majorColor.x, majorColor.y, majorColor.z)
    if (ImGui.colorEdit3(stringManager.getString("lbl.grid.major_color"), majorColorArr)) {
        majorColor.set(majorColorArr[0], majorColorArr[1], majorColorArr[2])
    }

    val minorColorArr = floatArrayOf(minorColor.x, minorColor.y, minorColor.z)
    if (ImGui.colorEdit3(stringManager.getString("lbl.grid.minor_color"), minorColorArr)) {
        minorColor.set(minorColorArr[0], minorColorArr[1], minorColorArr[2])
    }

    ImGui.separator()

    // Z-fighting offset
    ImGui.text(stringManager.getString("lbl.grid.z_fighting"))
    ImGui.pushItemWidth(120f)

    val yOffsetArr = floatArrayOf(gridYOffset)
    if (ImGui.dragFloat(stringManager.getString("lbl.grid.y_offset"), yOffsetArr, 0.01f, -1.0f, 0.0f, "%.2f m")) {
        gridYOffset = yOffsetArr[0].coerceIn(-1.0f, 0.0f)
    }
    ImGui.popItemWidth()

    ImGui.separator()

    // Advanced features
    ImGui.text(stringManager.getString("lbl.grid.advanced"))

    // Center marker
    val showCenterMarker = ImBoolean(showCenterMarker)
    if (ImGui.checkbox(stringManager.getString("lbl.grid.show_center_marker"), showCenterMarker)) {
        this.showCenterMarker = showCenterMarker.get()
    }

    val centerMarkerColorArr =
        floatArrayOf(centerMarkerColor.x, centerMarkerColor.y, centerMarkerColor.z)
    if (ImGui.colorEdit3(stringManager.getString("lbl.grid.center_marker_color"), centerMarkerColorArr)) {
        centerMarkerColor.set(centerMarkerColorArr[0], centerMarkerColorArr[1], centerMarkerColorArr[2])
    }

    val centerMarkerDistArr = floatArrayOf(centerMarkerDistance)
    if (ImGui.dragFloat(
            stringManager.getString("lbl.grid.center_marker_distance"),
            centerMarkerDistArr,
            1.0f,
            5.0f,
            100.0f,
            "%.0f m"
        )
    ) {
        centerMarkerDistance = centerMarkerDistArr[0].coerceAtLeast(5.0f)
    }

    ImGui.separator()

    // Edge fading
    val edgeFadeEnabled = ImBoolean(edgeFadeEnabled)
    if (ImGui.checkbox(stringManager.getString("lbl.grid.edge_fade_enabled"), edgeFadeEnabled)) {
        this.edgeFadeEnabled = edgeFadeEnabled.get()
    }

    val edgeFadeStartArr = floatArrayOf(edgeFadeStart)
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
        edgeFadeStart = edgeFadeStartArr[0].coerceIn(0.0f, 1.0f)
    }
    ImGui.popItemWidth()

    ImGui.separator()

    // Secondary grid
    val secondaryGridEnabled = ImBoolean(secondaryGridEnabled)
    if (ImGui.checkbox(stringManager.getString("lbl.grid.secondary_grid_enabled"), secondaryGridEnabled)) {
        this.secondaryGridEnabled = secondaryGridEnabled.get()
    }

    val secondaryGridYArr = floatArrayOf(secondaryGridY)
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
        secondaryGridY = secondaryGridYArr[0]
    }
    ImGui.popItemWidth()

    val secondaryGridColorArr =
        floatArrayOf(secondaryGridColor.x, secondaryGridColor.y, secondaryGridColor.z)
    if (ImGui.colorEdit3(stringManager.getString("lbl.grid.secondary_grid_color"), secondaryGridColorArr)) {
        secondaryGridColor.set(secondaryGridColorArr[0], secondaryGridColorArr[1], secondaryGridColorArr[2])
    }

    ImGui.separator()

    // Snap visualization
    val snapVisEnabled = ImBoolean(snapVisualizationEnabled)
    if (ImGui.checkbox(stringManager.getString("lbl.grid.snap_visualization_enabled"), snapVisEnabled)) {
        snapVisualizationEnabled = snapVisEnabled.get()
    }

    val snapMarkerColorArr =
        floatArrayOf(snapMarkerColor.x, snapMarkerColor.y, snapMarkerColor.z)
    if (ImGui.colorEdit3(stringManager.getString("lbl.grid.snap_marker_color"), snapMarkerColorArr)) {
        snapMarkerColor.set(snapMarkerColorArr[0], snapMarkerColorArr[1], snapMarkerColorArr[2])
    }

    ImGui.separator()

    // Reset button
    if (ImGui.button(stringManager.getString("lbl.grid.reset_to_defaults"))) {
        resetToDefaults()
    }
}