package com.pafoid.skate.engine.ecs.config

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

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
@Serializable
data class GridConfig(
    var majorStep: Float = 10.0f,  // Godot default: 10m spacing for major lines
    var minorStep: Float = 1.0f,   // Godot default: 1m spacing for minor lines
    @Contextual var majorColor: Vector3f = Vector3f(0.25f, 0.25f, 0.25f),  // Even darker to match Godot's subtle grid
    @Contextual var minorColor: Vector3f = Vector3f(0.15f, 0.15f, 0.15f),  // Very subtle minor lines
    var minExtent: Float = 50.0f,  // Godot shows a very large grid by default
    var maxExtent: Float = 500.0f,  // Much larger maximum for far camera distances
    var lodCloseDistance: Float = 50.0f,  // Minor lines visible much further (Godot: ~50m)
    var lodFarDistance: Float = 200.0f,  // Minor lines fade out at very far distance
    var showGrid: Boolean = true,
    var showOriginAxes: Boolean = true,
    var gridYOffset: Float = 0.0f,
    var showCenterMarker: Boolean = false,  // Disabled by default (Godot doesn't have this)
    @Contextual var centerMarkerColor: Vector3f = Vector3f(1.0f, 1.0f, 0.0f),
    var centerMarkerDistance: Float = 30.0f,
    var edgeFadeEnabled: Boolean = false,  // Disabled - Godot doesn't fade grid edges
    var edgeFadeStart: Float = 0.7f,
    var secondaryGridEnabled: Boolean = false,
    var secondaryGridY: Float = 2.0f,
    @Contextual var secondaryGridColor: Vector3f = Vector3f(0.0f, 0.8f, 0.8f),
    var snapVisualizationEnabled: Boolean = false,  // Disabled by default (Godot doesn't have this)
    @Contextual var snapMarkerColor: Vector3f = Vector3f(0.0f, 1.0f, 0.0f),
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