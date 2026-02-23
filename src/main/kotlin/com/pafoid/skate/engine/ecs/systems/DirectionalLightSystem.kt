package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
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
 * - Uploads light uniforms to shaders via [LightingUniformsLoader]
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
            updateLightSpaceMatrix(light)
        }
    }

    /**
     * Computes the light space matrix for shadow mapping.
     *
     * The light space matrix transforms world positions into light clip space,
     * where depth comparison against the shadow map is performed.
     */
    private fun updateLightSpaceMatrix(light: DirectionalLightComponent) {
        // Calculate light position (directional light at infinity)
        // We use a point far away in the opposite direction of the light
        lightPosition.set(light.direction).mul(-100f)

        // Target is the origin (or could be camera position for cascaded shadows)
        lightTarget.set(0f, 0f, 0f)

        // Create view matrix (light looking at scene)
        lightView.setLookAt(lightPosition, lightTarget, lightUp)

        // Create orthographic projection for directional light
        lightProjection.setOrtho(
            light.orthoLeft,
            light.orthoRight,
            light.orthoBottom,
            light.orthoTop,
            light.orthoNear,
            light.orthoFar
        )

        // Combine projection and view
        light.lightSpaceMatrix.set(lightProjection).mul(lightView)
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
}
