package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.ShadowMap
import com.pafoid.skate.engine.render.renderer.ShadowRenderer

/**
 * Shadow mapping render pass.
 *
 * Renders all shadow-casting objects into the shadow map depth texture.
 * This pass must be executed before the geometry pass so that shadows
 * can be sampled during main scene rendering.
 *
 * ## Render Pipeline
 *
 * 1. Bind shadow map framebuffer
 * 2. Clear depth buffer
 * 3. Set viewport to shadow map resolution
 * 4. Render all objects with castShadow enabled
 * 5. Unbind shadow map
 *
 * @param shadowRenderer The shadow renderer for drawing objects
 * @param shadowMap The shadow map depth texture
 * @param logger Logger for debug output
 */
class ShadowPass(
    private val shadowRenderer: ShadowRenderer,
    private val shadowMap: ShadowMap,
    private val logger: LoggerService
) : BaseRenderPass() {

    override val name: String = "ShadowPass"
    override val description: String = "Renders shadow map from light's perspective"
    override val outputs: Set<String> = setOf("ShadowMap")
    override val canDisable: Boolean = true  // Shadows can be disabled for performance

    override fun execute(scene: Scene) {
        // Get directional light system from scene
        val lightComponent = scene.getComponent<DirectionalLightComponent>()

        // Skip if shadows are disabled or no light system
        if (lightComponent?.castShadows == false) {
            logger.log("[ShadowPass] Skipped: castShadows=${lightComponent.castShadows}")
            return
        }

        // Bind shadow map framebuffer
        shadowMap.bind()

        // Clear depth buffer
        shadowMap.clear()

        // Get light space matrix from directional light config
        val lightSpaceMatrix = lightComponent?.lightSpaceMatrix

        // Render all shadow casters
        lightSpaceMatrix?.let {
            shadowRenderer.render(
                gameObjects = scene.gameObjects.filter { it.isVisible },
                lightSpaceMatrix = lightSpaceMatrix,
            )
        }

        // Unbind shadow map
        shadowMap.unbind()
    }
}
