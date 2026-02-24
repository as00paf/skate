package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.config.DirectionalLightConfig
import com.pafoid.skate.engine.render.Camera
import imgui.ImGui
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * System responsible for updating the directional light.
 *
 * This system runs at [ExecutionPriority.EARLY] after [DayNightCycleSystem]
 * to ensure day/night state is ready before computing light properties.
 *
 * ## Responsibilities
 *
 * - Reads sun direction, color, and intensity from [DayNightCycleSystem.config]
 * - Updates [DirectionalLightConfig] with current sun data
 * - Computes light space matrix for shadow mapping
 * - Supports dynamic orthographic bounds adjustment based on camera view
 *
 * ## Shadow Mapping
 *
 * The light space matrix is computed as:
 * ```
 * lightSpaceMatrix = lightProjection * lightView
 * ```
 *
 * Where:
 * - `lightView` = lookAt matrix from light position to target
 * - `lightProjection` = orthographic projection for directional light shadows
 *
 * ## Orthographic Bounds
 *
 * Default bounds are tuned for skate level size:
 * - left/right: -20 to 20 (40m width)
 * - bottom/top: -20 to 20 (40m height)
 * - near/far: 0.1 to 100 (depth range)
 *
 * These can be adjusted via ImGui or programmatically for different scene scales.
 *
 * ## Usage
 *
 * ```kotlin
 * // System automatically updates directional light each frame
 * val system = DirectionalLightSystem()
 * scene.addSystem(system)
 *
 * // Access configuration
 * system.config.direction.set(0f, -1f, 0f)
 * system.config.shadowDistance = 50f
 * ```
 */
class DirectionalLightSystem(
    initialConfig: DirectionalLightConfig = DirectionalLightConfig(),
) : System(priority = ExecutionPriority.EARLY) {

    // System-owned configuration
    val config = initialConfig

    private val lightView = Matrix4f()
    private val lightProjection = Matrix4f()
    private val lightTarget = Vector3f()
    private val lightUp = Vector3f(0f, 1f, 0f)
    private val lightPosition = Vector3f()

    // Dynamic bounds adjustment
    private var autoAdjustBounds = false
    private var boundsScale = 1.0f

    override fun update(dt: Float) {
        // Find day/night cycle system
        val dayNightSystem = scene.systemManager.getSystem<DayNightCycleSystem>()
        val dayNight = dayNightSystem?.config

        // Update light from day/night cycle
        if (dayNight != null) {
            config.direction.set(dayNight.sunDirection)
            config.color.set(dayNight.sunColor)
            config.intensity = dayNight.sunIntensity
        }

        // Compute light space matrix for shadow mapping
        if (config.castShadows) {
            val camera = scene.camera
            if (autoAdjustBounds) {
                adjustOrthoBoundsForCamera(scene)
            }
            updateLightSpaceMatrix(camera)
        }
    }

    /**
     * Computes the light space matrix for shadow mapping.
     *
     * The light space matrix transforms world positions into light clip space,
     * where depth comparison against the shadow map is performed.
     */
    private fun updateLightSpaceMatrix(camera: Camera? = null) {
        // Calculate light position (directional light at infinity)
        // We use a point far away in the opposite direction of the light
        lightPosition.set(config.direction).mul(-100f)

        // Target is the origin (or camera position for cascaded shadows)
        if (camera != null && config.autoCalculateBounds) {
            lightTarget.set(camera.position)
        } else {
            lightTarget.set(0f, 0f, 0f)
        }

        // Create view matrix (light looking at scene)
        lightView.setLookAt(lightPosition, lightTarget, lightUp)

        // Calculate orthographic bounds
        var left: Float
        var right: Float
        var bottom: Float
        var top: Float

        if (config.autoCalculateBounds) {
            // Auto-calculate from shadow distance
            val halfDistance = config.shadowDistance * 0.5f
            left = -halfDistance * boundsScale
            right = halfDistance * boundsScale
            bottom = -halfDistance * boundsScale
            top = halfDistance * boundsScale
        } else {
            // Use manual bounds
            left = config.orthoLeft * boundsScale
            right = config.orthoRight * boundsScale
            bottom = config.orthoBottom * boundsScale
            top = config.orthoTop * boundsScale
        }

        // Stabilize projection to reduce shimmering (texel snapping)
        if (config.stabilizeProjection && camera != null) {
            // Snap orthographic bounds to texel-sized increments
            // This prevents the shadow map from shimmering as the camera moves
            val shadowMapSize = 4096f // Assuming 4096x4096 shadow map
            val texelSize = (right - left) / shadowMapSize

            // Snap camera position to texel grid in light space
            val lightViewPos = lightView.transform(org.joml.Vector4f(camera.position, 1.0f), org.joml.Vector4f())
            lightViewPos.x = Math.round(lightViewPos.x / texelSize) * texelSize
            lightViewPos.y = Math.round(lightViewPos.y / texelSize) * texelSize

            // Recalculate view matrix with snapped position
            val snappedLightPos = lightView.invert().transform(lightViewPos, org.joml.Vector4f())
            lightPosition.set(snappedLightPos.x, snappedLightPos.y, snappedLightPos.z)
                .add(org.joml.Vector3f(config.direction).mul(-100f))
            lightView.setLookAt(lightPosition, lightTarget, lightUp)
        }

        // Create orthographic projection for directional light
        lightProjection.setOrtho(
            left,
            right,
            bottom,
            top,
            config.orthoNear,
            config.orthoFar
        )

        // Combine projection and view
        config.lightSpaceMatrix.set(lightProjection).mul(lightView)
    }

    /**
     * Adjusts orthographic bounds based on camera view frustum.
     *
     * This ensures the shadow map covers the visible area efficiently,
     * reducing wasted shadow map resolution on areas outside the camera view.
     */
    private fun adjustOrthoBoundsForCamera(scene: Scene) {
        val camera = scene.camera ?: return

        // Calculate frustum size at far plane
        val fovRad = Math.toRadians(camera.fov.toDouble()).toFloat()
        val farHeight = (camera.farPlane * Math.tan(fovRad / 2.0).toFloat() * 2.0f)
        val aspectRatio = camera.viewportWidth.toFloat() / camera.viewportHeight.toFloat().coerceAtLeast(0.001f)
        val farWidth = farHeight * aspectRatio

        // Adjust bounds to cover frustum from light's perspective
        // Use a conservative estimate based on light direction
        val lightDirLength = Math.abs(config.direction.y.toDouble()).toFloat().coerceAtLeast(0.1f)
        val scale = (farWidth / lightDirLength).coerceAtMost(50f)

        boundsScale = scale.coerceIn(0.5f, 3.0f)
    }

    /**
     * Renders ImGui interface for debugging and tuning.
     */
    override fun imgui() {
        if (ImGui.collapsingHeader("Directional Light")) {
            if (ImGui.checkbox("Auto Adjust Bounds", autoAdjustBounds)) {
                autoAdjustBounds = !autoAdjustBounds
            }

            ImGui.separator()
            ImGui.text("Shadow Distance")

            val shadowDistanceArr = floatArrayOf(config.shadowDistance)
            if (ImGui.dragFloat("Shadow Distance (m)", shadowDistanceArr, 0.1f, 10f, 200f)) {
                config.shadowDistance = shadowDistanceArr[0]
            }

            val autoCalcBounds = config.autoCalculateBounds
            if (ImGui.checkbox("Auto Calculate Bounds", autoCalcBounds)) {
                config.autoCalculateBounds = !autoCalcBounds
            }

            if (!config.autoCalculateBounds) {
                ImGui.separator()
                ImGui.text("Orthographic Bounds (Manual)")

                val orthoLeft = floatArrayOf(config.orthoLeft)
                if (ImGui.dragFloat("Left", orthoLeft, 0.1f, -100f, 0f)) {
                    config.orthoLeft = orthoLeft[0]
                }

                val orthoRight = floatArrayOf(config.orthoRight)
                if (ImGui.dragFloat("Right", orthoRight, 0.1f, 0f, 100f)) {
                    config.orthoRight = orthoRight[0]
                }

                val orthoBottom = floatArrayOf(config.orthoBottom)
                if (ImGui.dragFloat("Bottom", orthoBottom, 0.1f, -100f, 0f)) {
                    config.orthoBottom = orthoBottom[0]
                }

                val orthoTop = floatArrayOf(config.orthoTop)
                if (ImGui.dragFloat("Top", orthoTop, 0.1f, 0f, 100f)) {
                    config.orthoTop = orthoTop[0]
                }
            }

            ImGui.separator()
            ImGui.text("Current Scale: %.2f".format(boundsScale))
            ImGui.text("Effective Shadow Coverage: %.1fm".format(config.shadowDistance * boundsScale))

            ImGui.separator()
            ImGui.text("Shadow Quality")

            val stabilizeProj = config.stabilizeProjection
            if (ImGui.checkbox("Stabilize Projection (Reduce Shimmering)", stabilizeProj)) {
                config.stabilizeProjection = !stabilizeProj
            }

            val depthBiasArr = floatArrayOf(config.depthBias)
            if (ImGui.dragFloat("Depth Bias", depthBiasArr, 0.0001f, 0.0f, 0.1f, "%.4f")) {
                config.depthBias = depthBiasArr[0]
            }

            val slopeBiasArr = floatArrayOf(config.slopeScaledBias)
            if (ImGui.dragFloat("Slope-Scaled Bias", slopeBiasArr, 0.001f, 0.0f, 0.1f, "%.3f")) {
                config.slopeScaledBias = slopeBiasArr[0]
            }
        }
    }
}
