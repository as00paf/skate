package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.scene.SceneData
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector3f

/**
 * Responsible for uploading lighting uniforms to shaders.
 */
class LightingUniformsLoader {

    /**
     * Uploads all lighting uniforms to the specified shader.
     *
     * @param shader The shader to upload uniforms to
     * @param camera The camera for position and view matrix
     * @param sceneData The scene data containing lighting configuration
     */
    fun loadLightingUniforms(
        shader: Shader,
        camera: Camera,
        sceneData: SceneData
    ) {
        // Directional light (sun)
        shader.uploadVec3f(Uniforms.SUN_DIRECTION, sceneData.sun.direction)
        val finalSunColor = if (sceneData.useSun) {
            Vector3f(sceneData.sun.color).mul(sceneData.sun.intensity)
        } else {
            Vector3f(0f, 0f, 0f)
        }
        shader.uploadVec3f(Uniforms.SUN_COLOR, finalSunColor)

        // Ambient light
        val ambient = if (sceneData.useAmbient) sceneData.ambientLight else Vector3f(0f, 0f, 0f)
        shader.uploadVec3f(Uniforms.AMBIENT_LIGHT, ambient)

        // Fog
        shader.uploadVec3f(Uniforms.FOG_COLOR, sceneData.fogColor)
        shader.uploadFloat(Uniforms.FOG_DENSITY, sceneData.fogDensity)
        shader.uploadFloat(Uniforms.FOG_GRADIENT, sceneData.fogGradient)
    }

    /**
     * Uploads only the camera position uniform to the shader.
     *
     * @param shader The shader to upload the uniform to
     * @param camera The camera providing the position
     */
    fun loadCameraPosition(shader: Shader, camera: Camera) {
        shader.uploadVec3f(Uniforms.CAMERA_POSITION, camera.position)
    }
}
