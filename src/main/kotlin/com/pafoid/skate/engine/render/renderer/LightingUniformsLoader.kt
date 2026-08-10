package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.AmbientLightComponent
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture

/**
 * Responsible for uploading lighting uniforms to shaders.
 */
class LightingUniformsLoader {

    /**
     * Uploads all lighting uniforms to the specified shader.
     *
     * @param shader The shader to upload uniforms to
     * @param ambientLightComponent Component containing ambient light state (optional)
     * @param directionalLight The directional light config
     * @param environmentComponent Component containing fog settings (optional)
     * @param shadowMapTextureId The shadow map depth texture ID (optional)
     */
    fun loadLightingUniforms(
        shader: Shader,
        ambientLightComponent: AmbientLightComponent?,
        directionalLight: DirectionalLightComponent?,
        pointLightComponents: List<GameObject>,
        spotLightComponents: List<GameObject>,
        environmentComponent: EnvironmentComponent? = null,
        shadowMapTextureId: Int = 0,
        shadowMapResolution: Float = 0f,
    ) {
        // Directional light (sun) - single unified light source
        if (directionalLight != null) {
            shader.uploadVec3f(Uniforms.SUN_DIRECTION, directionalLight.direction)
            val finalSunColor = Vector3f(directionalLight.color).mul(directionalLight.intensity)
            shader.uploadVec3f(Uniforms.SUN_COLOR, finalSunColor)

            // Upload light space matrix for shadow mapping
            shader.uploadMat4f(Uniforms.LIGHT_SPACE_MATRIX, directionalLight.lightSpaceMatrix)
        }

        // Ambient light
        val ambient = if (ambientLightComponent != null) {
            Vector3f(ambientLightComponent.lightColor).mul(ambientLightComponent.intensity)
        } else {
            Vector3f(0f)
        }
        shader.uploadVec3f(Uniforms.AMBIENT_LIGHT, ambient)

        // Point Lights
        val numPointLights = minOf(pointLightComponents.size, MAX_POINT_LIGHTS)
        shader.uploadInt(Uniforms.POINT_LIGHTS_COUNT, numPointLights)
        for (i in 0 until numPointLights) {
            val go = pointLightComponents[i]
            val transform = go.getComponent<Transform>() ?: continue
            val light = go.getComponent<PointLightComponent>() ?: continue

            shader.uploadVec3f("${Uniforms.POINT_LIGHTS}[$i].position", transform.translation)
            shader.uploadVec3f("${Uniforms.POINT_LIGHTS}[$i].color", Vector3f(light.color).mul(light.intensity))
            shader.uploadFloat("${Uniforms.POINT_LIGHTS}[$i].constant", light.constant)
            shader.uploadFloat("${Uniforms.POINT_LIGHTS}[$i].linear", light.linear)
            shader.uploadFloat("${Uniforms.POINT_LIGHTS}[$i].quadratic", light.quadratic)
        }

        // Spot Lights
        val numSpotLights = minOf(spotLightComponents.size, MAX_SPOT_LIGHTS)
        shader.uploadInt(Uniforms.SPOT_LIGHTS_COUNT, numSpotLights)
        for (i in 0 until numSpotLights) {
            val go = spotLightComponents[i]
            val transform = go.getComponent<Transform>() ?: continue
            val light = go.getComponent<SpotLightComponent>() ?: continue

            shader.uploadVec3f("${Uniforms.SPOT_LIGHTS}[$i].position", transform.translation)
            shader.uploadVec3f("${Uniforms.SPOT_LIGHTS}[$i].color", Vector3f(light.color).mul(light.intensity))
            shader.uploadFloat("${Uniforms.SPOT_LIGHTS}[$i].constant", light.constant)
            shader.uploadFloat("${Uniforms.SPOT_LIGHTS}[$i].linear", light.linear)
            shader.uploadFloat("${Uniforms.SPOT_LIGHTS}[$i].quadratic", light.quadratic)
        }

        // Shadow map texture (if available)
        if (shadowMapTextureId != 0) {
            shader.uploadInt(Uniforms.SHADOW_MAP, Uniforms.SHADOW_TEXTURE_UNIT)
            // Upload texel size for PCF (assuming square shadow map)
            // This will be set by the caller based on actual shadow map resolution
            shader.uploadFloat(Uniforms.SHADOW_MAP_TEXEL_SIZE, 1.0f / shadowMapResolution)
            // Upload shadow bias uniforms
            if (directionalLight != null) {
                shader.uploadFloat(Uniforms.SHADOW_DEPTH_BIAS, directionalLight.depthBias)
                shader.uploadFloat(Uniforms.SHADOW_SLOPE_SCALED_BIAS, directionalLight.slopeScaledBias)
            } else {
                shader.uploadFloat(Uniforms.SHADOW_DEPTH_BIAS, 0.001f)
                shader.uploadFloat(Uniforms.SHADOW_SLOPE_SCALED_BIAS, 0.002f)
            }

            // Bind shadow map texture to texture unit
            glActiveTexture(GL_TEXTURE0 + Uniforms.SHADOW_TEXTURE_UNIT)
            glBindTexture(GL_TEXTURE_2D, shadowMapTextureId)
        }

        // Fog - use EnvironmentComponent if available and enabled, otherwise use defaults
        // When renderFog is false, upload zero density to disable fog effect
        val fogEnabled = environmentComponent?.renderFog == true
        shader.uploadVec3f(Uniforms.FOG_COLOR, environmentComponent?.fogColor ?: Vector3f(0.8f, 0.8f, 0.8f))
        shader.uploadFloat(Uniforms.FOG_DENSITY, if (fogEnabled) environmentComponent?.fogDensity ?: 0.0f else 0.0f)
        shader.uploadFloat(Uniforms.FOG_GRADIENT, environmentComponent?.fogGradient ?: 1.5f)
    }

    /**
     * Uploads only the camera position uniform to the shader.
     *
     * @param shader The shader to upload the uniform to
     * @param camera The camera providing the position
     */
    fun loadCameraPosition(shader: Shader, camera: CameraComponent) {
        shader.uploadVec3f(Uniforms.CAMERA_POSITION, camera.position)
    }

    companion object {
        const val MAX_POINT_LIGHTS = 8
        const val MAX_SPOT_LIGHTS = 8
    }
}
