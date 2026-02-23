package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Component that stores directional light data.
 *
 * This component is updated by [DirectionalLightSystem] and read by:
 * - [LightingUniformsLoader] for uploading light uniforms to shaders
 * - Shadow mapping systems for light space matrix calculation
 *
 * ## Usage
 *
 * ```kotlin
 * // In DirectionalLightSystem or lighting code
 * val light = gameObject.getComponent<DirectionalLightComponent>() ?: return
 *
 * // Get light direction for calculations
 * val direction = light.direction
 *
 * // Get light color and intensity for shading
 * val color = light.color
 * val intensity = light.intensity
 *
 * // Get light space matrix for shadow mapping
 * val lightSpace = light.lightSpaceMatrix
 * ```
 */
@Serializable
class DirectionalLightComponent : Component() {

    // =========================================================================
    // LIGHT PROPERTIES
    // =========================================================================

    /**
     * Direction the light is pointing (normalized vector).
     * Points FROM the light TO the scene.
     * Example: (0, -1, 0) = light shining straight down from above.
     */
    @Contextual
    var direction = Vector3f(0f, -1f, 0f)

    /**
     * Color of the light.
     * Default: warm sunlight (1.0, 0.95, 0.8)
     * Range: typically 0.0 - 1.0 per channel, but can exceed 1.0 for HDR.
     */
    @Contextual
    var color = Vector3f(1f, 0.95f, 0.8f)

    /**
     * Intensity (brightness) of the light.
     * Default: 1.0
     * Range: 0.0 (off) to 10.0+ (very bright for HDR).
     */
    var intensity: Float = 1f

    // =========================================================================
    // SHADOW MAPPING
    // =========================================================================

    /**
     * Light space matrix for shadow mapping.
     * Computed as: lightProjection * lightView
     * Updated each frame by DirectionalLightSystem.
     */
    @Contextual
    var lightSpaceMatrix = Matrix4f()

    /**
     * Orthographic projection bounds for shadow mapping.
     * Defines the view volume captured in the shadow map.
     * These are auto-calculated from shadowDistance if autoCalculateBounds is true.
     */
    var orthoLeft: Float = -20f
    var orthoRight: Float = 20f
    var orthoBottom: Float = -20f
    var orthoTop: Float = 20f
    var orthoNear: Float = 0.1f
    var orthoFar: Float = 100f

    /**
     * Maximum distance from camera that shadows are rendered.
     * Controls the size of the orthographic projection for shadow mapping.
     * Larger values = larger shadow coverage but lower resolution.
     * Default: 50 meters
     */
    var shadowDistance: Float = 50f

    /**
     * If true, orthographic bounds are automatically calculated from shadowDistance.
     * If false, manual orthoLeft/Right/Top/Bottom values are used.
     * Default: true
     */
    var autoCalculateBounds: Boolean = true

    /**
     * True if this light casts shadows.
     * When false, shadow mapping is skipped for this light.
     */
    var castShadows: Boolean = true

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
        castShadows = true
    }

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        reset()
    }
}
