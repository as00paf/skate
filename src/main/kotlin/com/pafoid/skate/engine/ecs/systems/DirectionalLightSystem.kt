package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.config.ExecutionPriority
import com.pafoid.skate.engine.render.Camera
import imgui.ImGui
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * System responsible for updating the directional light.
 *
 * This system runs at [ExecutionPriority.EARLY] after [DayNightCycleSystem]
 * to ensure day/night state is ready before computing light properties.
 *
 * @param stringManager String manager for localized UI strings
 */
class DirectionalLightSystem(
    private val stringManager: StringManager
) : System(priority = ExecutionPriority.EARLY) {

    // System-owned configuration
    var config: DirectionalLightComponent? = null

    private val lightView = Matrix4f()
    private val lightProjection = Matrix4f()
    private val lightTarget = Vector3f()
    private val lightUp = Vector3f(0f, 1f, 0f)
    private val lightPosition = Vector3f()

    override fun update(dt: Float) {
        // Find day/night cycle system
        val dayNight = scene.getComponent<DayNightCycleComponent>() ?: return
        config = scene.getComponent<DirectionalLightComponent>() ?: return

        // Update light from day/night cycle
        config?.direction?.set(dayNight.sunDirection)
        config?.color?.set(dayNight.sunColor)
        config?.intensity = dayNight.sunIntensity

        // Compute light space matrix for shadow mapping
        if (config?.castShadows == true) {
            updateLightSpaceMatrix(scene.camera)
        }
    }

    /**
     * Computes the light space matrix for shadow mapping.
     */
    private fun updateLightSpaceMatrix(camera: Camera? = null) {
        // Choose up vector based on light direction to prevent lookAt failure (high noon)
        val config = config ?: return
        if (Math.abs(config.direction.y) > 0.99f) {
            lightUp.set(0f, 0f, 1f)
        } else {
            lightUp.set(0f, 1f, 0f)
        }

        if (camera != null && config.autoCalculateBounds) {
            // Calculate the camera's view frustum corners limited by shadowDistance
            val tempProj = Matrix4f()
            val aspectRatio =
                if (camera.viewportHeight > 0) camera.viewportWidth.toFloat() / camera.viewportHeight else 16f / 9f
            tempProj.perspective(
                Math.toRadians(camera.fov.toDouble()).toFloat() * camera.zoom,
                aspectRatio,
                camera.nearPlane,
                config.shadowDistance
            )

            val invCameraViewProj = Matrix4f(tempProj).mul(camera.createViewMatrix()).invert()

            val frustumCorners = arrayOf(
                Vector4f(-1f, -1f, -1f, 1f),
                Vector4f(1f, -1f, -1f, 1f),
                Vector4f(-1f, 1f, -1f, 1f),
                Vector4f(1f, 1f, -1f, 1f),
                Vector4f(-1f, -1f, 1f, 1f),
                Vector4f(1f, -1f, 1f, 1f),
                Vector4f(-1f, 1f, 1f, 1f),
                Vector4f(1f, 1f, 1f, 1f)
            )

            val frustumCenter = Vector3f()
            val worldCorners = Array(8) { Vector3f() }

            for (i in 0 until 8) {
                val corner = Vector4f(frustumCorners[i]) // Copy to avoid modifying the array if we ever reused it
                invCameraViewProj.transform(corner)
                corner.div(corner.w) // Perspective divide
                worldCorners[i].set(corner.x, corner.y, corner.z)
                frustumCenter.add(worldCorners[i])
            }
            frustumCenter.div(8f)

            // Find the bounding sphere radius
            var radius = 0f
            for (corner in worldCorners) {
                val dist = corner.distance(frustumCenter)
                if (dist > radius) {
                    radius = dist
                }
            }
            // Round radius up to nearest 1 unit to stabilize projection size
            radius = Math.ceil(radius.toDouble()).toFloat()

            // Set bounds based on bounding sphere (guarantees coverage regardless of rotation)
            val left = -radius
            val right = radius
            val bottom = -radius
            val top = radius

            // Set target
            lightTarget.set(frustumCenter)

            if (config.stabilizeProjection) {
                val shadowMapSize = 4096f // Assuming 4096x4096 shadow map
                val texelSize = (radius * 2f) / shadowMapSize

                val fixedEye = Vector3f(config.direction).mul(-1f)
                val fixedView = Matrix4f().setLookAt(fixedEye, Vector3f(0f, 0f, 0f), lightUp)

                val targetInLightSpace = fixedView.transform(Vector4f(lightTarget, 1.0f))
                targetInLightSpace.x = Math.floor((targetInLightSpace.x / texelSize).toDouble()).toFloat() * texelSize
                targetInLightSpace.y = Math.floor((targetInLightSpace.y / texelSize).toDouble()).toFloat() * texelSize

                val snappedTarget = fixedView.invert().transform(targetInLightSpace)
                lightTarget.set(snappedTarget.x, snappedTarget.y, snappedTarget.z)
            }

            // Create view matrix
            // For directional light, we pull the light position back along the light direction.
            // Using a moderate buffer to cover objects outside frustum casting shadows in
            val buffer = 50f
            val distance = radius + buffer
            lightPosition.set(config.direction).mul(-distance).add(lightTarget)
            lightView.setLookAt(lightPosition, lightTarget, lightUp)

            // Keep the depth range tight (~150m) so that depth bias precision is maintained.
            val zNear = 0.1f
            val zFar = distance + radius + 10f

            lightProjection.setOrtho(
                left, right,
                bottom, top,
                zNear, zFar
            )
            
        } else {
            // Manual bounds path
            lightTarget.set(if (camera != null) camera.position else Vector3f())

            val left = config.orthoLeft
            val right = config.orthoRight
            val bottom = config.orthoBottom
            val top = config.orthoTop

            // Optional stabilization
            if (config.stabilizeProjection && camera != null) {
                val shadowMapSize = 4096f
                val texelSize = (right - left) / shadowMapSize

                val fixedEye = Vector3f(config.direction).mul(-1f)
                val fixedView = Matrix4f().setLookAt(fixedEye, Vector3f(0f, 0f, 0f), lightUp)

                val targetInLightSpace = fixedView.transform(Vector4f(lightTarget, 1.0f))
                targetInLightSpace.x = Math.floor((targetInLightSpace.x / texelSize).toDouble()).toFloat() * texelSize
                targetInLightSpace.y = Math.floor((targetInLightSpace.y / texelSize).toDouble()).toFloat() * texelSize

                val snappedTarget = fixedView.invert().transform(targetInLightSpace)
                lightTarget.set(snappedTarget.x, snappedTarget.y, snappedTarget.z)
            }

            // Create view matrix
            val distance = 500f // Large distance for manual bounds
            lightPosition.set(config.direction).mul(-distance).add(lightTarget)
            lightView.setLookAt(lightPosition, lightTarget, lightUp)

            val zNear = 1.0f
            val zFar = distance + 500f

            lightProjection.setOrtho(
                left, right,
                bottom, top,
                zNear, zFar
            )
        }

        // Combine projection and view
        config.lightSpaceMatrix.set(lightProjection).mul(lightView)
    }

    /**
     * Renders ImGui interface for debugging and tuning.
     */
    override fun imgui() {
        val config = config ?: return

        ImGui.separator()
        ImGui.text(stringManager.getString("lbl.directional_light.shadow_distance"))

        val shadowDistanceArr = floatArrayOf(config.shadowDistance)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.directional_light.shadow_distance_m"),
                shadowDistanceArr,
                0.1f,
                10f,
                200f
            )
        ) {
            config.shadowDistance = shadowDistanceArr[0]
        }

        val autoCalcBounds = config.autoCalculateBounds
        if (ImGui.checkbox(stringManager.getString("lbl.directional_light.auto_calculate_bounds"), autoCalcBounds)) {
            config.autoCalculateBounds = !autoCalcBounds
        }

        if (!config.autoCalculateBounds) {
            ImGui.separator()
            ImGui.text(stringManager.getString("lbl.directional_light.orthographic_bounds"))

            val orthoLeft = floatArrayOf(config.orthoLeft)
            if (ImGui.dragFloat(stringManager.getString("lbl.directional_light.left"), orthoLeft, 0.1f, -100f, 0f)) {
                config.orthoLeft = orthoLeft[0]
            }

            val orthoRight = floatArrayOf(config.orthoRight)
            if (ImGui.dragFloat(stringManager.getString("lbl.directional_light.right"), orthoRight, 0.1f, 0f, 100f)) {
                config.orthoRight = orthoRight[0]
            }

            val orthoBottom = floatArrayOf(config.orthoBottom)
            if (ImGui.dragFloat(
                    stringManager.getString("lbl.directional_light.bottom"),
                    orthoBottom,
                    0.1f,
                    -100f,
                    0f
                )
            ) {
                config.orthoBottom = orthoBottom[0]
            }

            val orthoTop = floatArrayOf(config.orthoTop)
            if (ImGui.dragFloat(stringManager.getString("lbl.directional_light.top"), orthoTop, 0.1f, 0f, 100f)) {
                config.orthoTop = orthoTop[0]
            }
        }

        ImGui.separator()
        ImGui.text(stringManager.getString("lbl.directional_light.shadow_quality"))

        val stabilizeProj = config.stabilizeProjection
        if (ImGui.checkbox(stringManager.getString("lbl.directional_light.stabilize_projection"), stabilizeProj)) {
            config.stabilizeProjection = !stabilizeProj
        }

        val depthBiasArr = floatArrayOf(config.depthBias)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.directional_light.depth_bias"),
                depthBiasArr,
                0.0001f,
                0.0f,
                0.1f,
                "%.4f"
            )
        ) {
            config.depthBias = depthBiasArr[0]
        }

        val slopeBiasArr = floatArrayOf(config.slopeScaledBias)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.directional_light.slope_scaled_bias"),
                slopeBiasArr,
                0.001f,
                0.0f,
                0.1f,
                "%.3f"
            )
        ) {
            config.slopeScaledBias = slopeBiasArr[0]
        }
    }
}
