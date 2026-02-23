package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
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
 * - Reads sun direction, color, and intensity from [DayNightCycleComponent]
 * - Updates [DirectionalLightComponent] with current sun data
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
 * ```
 */
class DirectionalLightSystem : System(priority = ExecutionPriority.EARLY) {

    private val lightView = Matrix4f()
    private val lightProjection = Matrix4f()
    private val lightTarget = Vector3f()
    private val lightUp = Vector3f(0f, 1f, 0f)
    private val lightPosition = Vector3f()

    // Dynamic bounds adjustment
    private var autoAdjustBounds = false
    private var boundsScale = 1.0f

    override fun update(dt: Float) {
        // Find day/night cycle component
        val dayNightEntity = scene.gameObjectManager.gameObjects.find {
            it.getComponent<DayNightCycleComponent>() != null
        }
        val dayNight = dayNightEntity?.getComponent<DayNightCycleComponent>()

        // Find or create directional light entity
        val lightEntity = scene.gameObjectManager.gameObjects.find {
            it.getComponent<DirectionalLightComponent>() != null
        }

        val light = if (lightEntity == null) {
            // Create directional light entity if none exists
            createDirectionalLightEntity()
        } else {
            lightEntity.getComponent<DirectionalLightComponent>()
        } ?: return

        // Update light from day/night cycle
        if (dayNight != null) {
            light.direction.set(dayNight.sunDirection)
            light.color.set(dayNight.sunColor)
            light.intensity = dayNight.sunIntensity
        }

        // Compute light space matrix for shadow mapping
        if (light.castShadows) {
            val camera = scene.camera
            if (autoAdjustBounds) {
                adjustOrthoBoundsForCamera(light, scene)
            }
            updateLightSpaceMatrix(light, camera)
        }
    }

    /**
     * Computes the light space matrix for shadow mapping.
     *
     * The light space matrix transforms world positions into light clip space,
     * where depth comparison against the shadow map is performed.
     */
    private fun updateLightSpaceMatrix(light: DirectionalLightComponent, camera: Camera? = null) {
        // Calculate light position (directional light at infinity)
        // We use a point far away in the opposite direction of the light
        lightPosition.set(light.direction).mul(-100f)

        // Target is the origin (or camera position for cascaded shadows)
        if (camera != null && light.autoCalculateBounds) {
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

        if (light.autoCalculateBounds) {
            // Auto-calculate from shadow distance
            val halfDistance = light.shadowDistance * 0.5f
            left = -halfDistance * boundsScale
            right = halfDistance * boundsScale
            bottom = -halfDistance * boundsScale
            top = halfDistance * boundsScale
        } else {
            // Use manual bounds
            left = light.orthoLeft * boundsScale
            right = light.orthoRight * boundsScale
            bottom = light.orthoBottom * boundsScale
            top = light.orthoTop * boundsScale
        }

        // Stabilize projection to reduce shimmering (texel snapping)
        if (light.stabilizeProjection && camera != null) {
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
                .add(org.joml.Vector3f(light.direction).mul(-100f))
            lightView.setLookAt(lightPosition, lightTarget, lightUp)
        }

        // Create orthographic projection for directional light
        lightProjection.setOrtho(
            left,
            right,
            bottom,
            top,
            light.orthoNear,
            light.orthoFar
        )

        // Combine projection and view
        light.lightSpaceMatrix.set(lightProjection).mul(lightView)
    }

    /**
     * Adjusts orthographic bounds based on camera view frustum.
     *
     * This ensures the shadow map covers the visible area efficiently,
     * reducing wasted shadow map resolution on areas outside the camera view.
     */
    private fun adjustOrthoBoundsForCamera(light: DirectionalLightComponent, scene: Scene) {
        val camera = scene.camera ?: return

        // Calculate frustum size at far plane
        val fovRad = Math.toRadians(camera.fov.toDouble()).toFloat()
        val farHeight = (camera.farPlane * Math.tan(fovRad / 2.0).toFloat() * 2.0f)
        val aspectRatio = camera.viewportWidth.toFloat() / camera.viewportHeight.toFloat().coerceAtLeast(0.001f)
        val farWidth = farHeight * aspectRatio

        // Adjust bounds to cover frustum from light's perspective
        // Use a conservative estimate based on light direction
        val lightDirLength = Math.abs(light.direction.y.toDouble()).toFloat().coerceAtLeast(0.1f)
        val scale = (farWidth / lightDirLength).coerceAtMost(50f)

        boundsScale = scale.coerceIn(0.5f, 3.0f)
    }

    /**
     * Creates a new entity with DirectionalLightComponent.
     */
    private fun createDirectionalLightEntity(): DirectionalLightComponent? {
        val entity = scene.gameObjectManager.createGameObject("DirectionalLight")
        val light = DirectionalLightComponent()
        entity.addComponent(light)
        return light
    }

    /**
     * Renders ImGui interface for debugging and tuning.
     */
    override fun imgui() {
        if (ImGui.collapsingHeader("Directional Light")) {
            val lightEntity = scene.gameObjectManager.gameObjects.find {
                it.getComponent<DirectionalLightComponent>() != null
            }
            val light = lightEntity?.getComponent<DirectionalLightComponent>() ?: return

            if (ImGui.checkbox("Auto Adjust Bounds", autoAdjustBounds)) {
                autoAdjustBounds = !autoAdjustBounds
            }

            ImGui.separator()
            ImGui.text("Shadow Distance")

            val shadowDistanceArr = floatArrayOf(light.shadowDistance)
            if (ImGui.dragFloat("Shadow Distance (m)", shadowDistanceArr, 0.1f, 10f, 200f)) {
                light.shadowDistance = shadowDistanceArr[0]
            }

            val autoCalcBounds = light.autoCalculateBounds
            if (ImGui.checkbox("Auto Calculate Bounds", autoCalcBounds)) {
                light.autoCalculateBounds = !autoCalcBounds
            }

            if (!light.autoCalculateBounds) {
                ImGui.separator()
                ImGui.text("Orthographic Bounds (Manual)")

                val orthoLeft = floatArrayOf(light.orthoLeft)
                if (ImGui.dragFloat("Left", orthoLeft, 0.1f, -100f, 0f)) {
                    light.orthoLeft = orthoLeft[0]
                }

                val orthoRight = floatArrayOf(light.orthoRight)
                if (ImGui.dragFloat("Right", orthoRight, 0.1f, 0f, 100f)) {
                    light.orthoRight = orthoRight[0]
                }

                val orthoBottom = floatArrayOf(light.orthoBottom)
                if (ImGui.dragFloat("Bottom", orthoBottom, 0.1f, -100f, 0f)) {
                    light.orthoBottom = orthoBottom[0]
                }

                val orthoTop = floatArrayOf(light.orthoTop)
                if (ImGui.dragFloat("Top", orthoTop, 0.1f, 0f, 100f)) {
                    light.orthoTop = orthoTop[0]
                }
            }

            ImGui.separator()
            ImGui.text("Current Scale: %.2f".format(boundsScale))
            ImGui.text("Effective Shadow Coverage: %.1fm".format(light.shadowDistance * boundsScale))

            ImGui.separator()
            ImGui.text("Shadow Quality")

            val stabilizeProj = light.stabilizeProjection
            if (ImGui.checkbox("Stabilize Projection (Reduce Shimmering)", stabilizeProj)) {
                light.stabilizeProjection = !stabilizeProj
            }

            val depthBiasArr = floatArrayOf(light.depthBias)
            if (ImGui.dragFloat("Depth Bias", depthBiasArr, 0.0001f, 0.0f, 0.1f, "%.4f")) {
                light.depthBias = depthBiasArr[0]
            }

            val slopeBiasArr = floatArrayOf(light.slopeScaledBias)
            if (ImGui.dragFloat("Slope-Scaled Bias", slopeBiasArr, 0.001f, 0.0f, 0.1f, "%.3f")) {
                light.slopeScaledBias = slopeBiasArr[0]
            }
        }
    }
}
