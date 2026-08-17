package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class GridLines(
    var majorStep: Float = 10.0f,  // Godot default= 10m spacing for major lines
    var minorStep: Float = 1.0f,   // Godot default= 1m spacing for minor lines
    @Contextual
    val majorColor: Vector3f = Vector3f(0.25f, 0.25f, 0.25f),  // Even darker to match Godot's subtle grid
    @Contextual
    val minorColor: Vector3f = Vector3f(0.15f, 0.15f, 0.15f),  // Very subtle minor lines
    var minExtent: Float = 50.0f,  // Godot shows a very large grid by default
    var maxExtent: Float = 500.0f,  // Much larger maximum for far camera distances
    var lodCloseDistance: Float = 50.0f,  // Minor lines visible much further (Godot= ~50m)
    var lodFarDistance: Float = 200.0f,  // Minor lines fade out at very far distance
    var showGrid: Boolean = true,
    var showOriginAxes: Boolean = true,
    var gridYOffset: Float = 0.0f,
    var showCenterMarker: Boolean = false,  // Disabled by default (Godot doesn't have this)
    @Contextual
    val centerMarkerColor: Vector3f = Vector3f(1.0f, 1.0f, 0.0f),
    var centerMarkerDistance: Float = 30.0f,
    var edgeFadeEnabled: Boolean = false,  // Disabled - Godot doesn't fade grid edges
    var edgeFadeStart: Float = 0.7f,
    var secondaryGridEnabled: Boolean = false,
    var secondaryGridY: Float = 2.0f,
    @Contextual
    val secondaryGridColor: Vector3f = Vector3f(0.0f, 0.8f, 0.8f),
    var snapVisualizationEnabled: Boolean = false,  // Disabled by default
    @Contextual
    val snapMarkerColor: Vector3f = Vector3f(0.0f, 1.0f, 0.0f),
    var originAxesThickness: Float = 0.04f  // Thickness of axis lines (quad-based rendering)
) : SceneComponent() {

    fun resetToDefaults() {
        majorStep = 10.0f
        minorStep = 1.0f
        majorColor.set(0.25f, 0.25f, 0.25f)
        minorColor.set(0.15f, 0.15f, 0.15f)
        minExtent = 50.0f
        maxExtent = 500.0f
        lodCloseDistance = 50.0f
        lodFarDistance = 200.0f
        showGrid = true
        showOriginAxes = true
        gridYOffset = 0.0f
        showCenterMarker = false
        centerMarkerColor.set(1.0f, 1.0f, 0.0f)
        centerMarkerDistance = 30.0f
        edgeFadeEnabled = false
        edgeFadeStart = 0.7f
        secondaryGridEnabled = false
        secondaryGridY = 2.0f
        secondaryGridColor.set(0.0f, 0.8f, 0.8f)
        snapVisualizationEnabled = false
        snapMarkerColor.set(0.0f, 1.0f, 0.0f)
        originAxesThickness = 0.04f
    }

}