package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Configuration and state for the directional light system.
 *
 * This data class is owned by [DirectionalLightSystem] and stores both
 * configuration parameters and computed state values.
 *
 * ## Light Properties
 *
 * - [direction]: Direction the light is pointing
 * - [color]: Light color
 * - [intensity]: Light brightness
 *
 * ## Shadow Mapping Properties
 *
 * - [lightSpaceMatrix]: Light's view-projection matrix (computed)
 * - [orthoLeft/Right/Bottom/Top]: Orthographic projection bounds
 * - [orthoNear/Far]: Depth range
 * - [shadowDistance]: Maximum shadow rendering distance
 * - [autoCalculateBounds]: Auto-calculate bounds from shadowDistance
 * - [stabilizeProjection]: Enable texel snapping to reduce shimmering
 *
 * ## Quality Settings
 *
 * - [depthBias]: Constant depth bias to prevent shadow acne
 * - [slopeScaledBias]: Slope-scaled bias multiplier
 *
 * ## Flags
 * - [castShadows]: Whether this light casts shadows
 */
@Serializable
data class DirectionalLightComponent(
    // =========================================================================
    // LIGHT PROPERTIES
    // =========================================================================

    /**
     * Direction the light is pointing (normalized vector).
     * Points FROM the light TO the scene.
     * Example: (0, -1, 0) = light shining straight down from above.
     * Default: (0, -1, 0)
     */
    @Contextual
    var direction: Vector3f = Vector3f(0f, -1f, 0f),

    /**
     * Color of the light.
     * Default: warm sunlight (1.0, 0.95, 0.8)
     * Range: typically 0.0 - 1.0 per channel, but can exceed 1.0 for HDR.
     */
    @Contextual
    var color: Vector3f = Vector3f(1f, 0.95f, 0.8f),

    /**
     * Intensity (brightness) of the light.
     * Default: 1.0
     * Range: 0.0 (off) to 10.0+ (very bright for HDR).
     */
    var intensity: Float = 1f,

    // =========================================================================
    // SHADOW MAPPING
    // =========================================================================

    /**
     * Light space matrix for shadow mapping.
     * Computed as: lightProjection * lightView
     * Updated each frame by DirectionalLightSystem.
     */
    @Contextual
    var lightSpaceMatrix: Matrix4f = Matrix4f(),

    /**
     * Orthographic projection bounds for shadow mapping.
     * Defines the view volume captured in the shadow map.
     * These are auto-calculated from shadowDistance if autoCalculateBounds is true.
     * Default: -20 to 20 (40m coverage)
     */
    var orthoLeft: Float = -20f,
    var orthoRight: Float = 20f,
    var orthoBottom: Float = -20f,
    var orthoTop: Float = 20f,

    /**
     * Near and far planes for orthographic projection.
     * Default: 0.1 to 100
     */
    var orthoNear: Float = 0.1f,
    var orthoFar: Float = 100f,

    /**
     * Maximum distance from camera that shadows are rendered.
     * Controls the size of the orthographic projection for shadow mapping.
     * Larger values = larger shadow coverage but lower resolution.
     * Default: 50 meters
     */
    var shadowDistance: Float = 50f,

    /**
     * If true, orthographic bounds are automatically calculated from shadowDistance.
     * If false, manual orthoLeft/Right/Top/Bottom values are used.
     * Default: true
     */
    var autoCalculateBounds: Boolean = true,

    /**
     * If true, stabilizes the shadow map projection to reduce shimmering.
     * Snaps the light's orthographic projection to texel boundaries.
     * Default: true
     */
    var stabilizeProjection: Boolean = true,

    /**
     * Depth bias for shadow comparison to prevent shadow acne.
     * Added to sampled depth before comparison.
     * Default: 0.001
     */
    var depthBias: Float = 0.001f,

    /**
     * Slope-scaled depth bias multiplier.
     * Increases bias for surfaces at steep angles to the light.
     * Default: 0.002
     */
    var slopeScaledBias: Float = 0.002f,

    /**
     * True if this light casts shadows.
     * When false, shadow mapping is skipped for this light.
     * Default: true
     */
    var castShadows: Boolean = true
) : Component() {
    /**
     * Resets all properties to defaults.
     */
    fun reset() {
        direction.set(0f, -1f, 0f)
        color.set(1f, 0.95f, 0.8f)
        intensity = 1f
        lightSpaceMatrix.identity()
        orthoLeft = -20f
        orthoRight = 20f
        orthoBottom = -20f
        orthoTop = 20f
        orthoNear = 0.1f
        orthoFar = 100f
        shadowDistance = 50f
        autoCalculateBounds = true
        stabilizeProjection = true
        depthBias = 0.001f
        slopeScaledBias = 0.002f
        castShadows = true
    }
}