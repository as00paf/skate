package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.components.AmbientLightComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.render.fog.FogUniforms
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import com.pafoid.skate.engine.render.shadow.CascadeSplits
import com.pafoid.skate.engine.render.shadow.LocalShadows
import com.pafoid.skate.engine.render.shadow.ShadowBias
import com.pafoid.skate.engine.render.shadow.ShadowFrame
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY

/**
 * Uploads the global (non point/spot) lighting uniforms: sun, ambient, shadow and fog.
 * Point and spot lights go through [com.pafoid.skate.engine.render.lighting.LightBuffer].
 */
class LightingUniformsLoader {

    /**
     * Uploads all lighting uniforms to the specified shader.
     *
     * @param shader The shader to upload uniforms to
     * @param ambientLightComponent Component containing ambient light state (optional)
     * @param directionalLight The directional light config
     * @param environmentComponent Component containing fog settings (optional)
     * @param shadows This frame's shadow maps from the shadow pass, or null to disable shadows
     */
    fun loadLightingUniforms(
        shader: Shader,
        ambientLightComponent: AmbientLightComponent?,
        directionalLight: DirectionalLightComponent?,
        environmentComponent: EnvironmentComponent? = null,
        shadows: ShadowFrame? = null,
    ) {
        // Directional light (sun) - single unified light source
        if (directionalLight != null) {
            shader.uploadVec3f(Uniforms.SUN_DIRECTION, directionalLight.direction)
            sunColor.set(directionalLight.color).mul(directionalLight.intensity)
            shader.uploadVec3f(Uniforms.SUN_COLOR, sunColor)
        } else {
            shader.uploadVec3f(Uniforms.SUN_DIRECTION, ZERO)
            shader.uploadVec3f(Uniforms.SUN_COLOR, ZERO)
        }

        // Ambient light
        if (ambientLightComponent != null) {
            ambient.set(ambientLightComponent.lightColor).mul(ambientLightComponent.intensity)
        } else {
            ambient.zero()
        }
        shader.uploadVec3f(Uniforms.AMBIENT_LIGHT, ambient)

        loadShadowUniforms(shader, shadows)

        // Fog (fog.glsl), shared with the sky dome and particles
        FogUniforms.upload(shader, environmentComponent)
    }

    /**
     * Uploads cascaded and local shadow uniforms from [frame] and binds both shadow arrays. A null [frame] (shadow pass
     * disabled or nothing to shadow) turns shadows off. The sampler units are always set so the shadow samplers never
     * alias the material samplers on unit 0.
     */
    fun loadShadowUniforms(shader: Shader, frame: ShadowFrame?) {
        shader.uploadInt(Uniforms.CASCADE_SHADOW_MAP, Uniforms.SHADOW_TEXTURE_UNIT)
        shader.uploadInt(Uniforms.LOCAL_SHADOW_MAP, Uniforms.LOCAL_SHADOW_TEXTURE_UNIT)
        val cascades = if (frame != null && frame.cascadeTextureId != 0) frame.cascadeCount else 0
        val local = if (frame != null && frame.localTextureId != 0) frame.localSlots.size else 0
        shader.uploadInt(Uniforms.CASCADE_COUNT, cascades)
        shader.uploadInt(Uniforms.SHADOWED_LIGHT_COUNT, local)
        if (frame == null || (cascades == 0 && local == 0)) return

        val pcf = frame.quality.pcfRadius
        shader.uploadInt(Uniforms.PCF_RADIUS, pcf)

        if (cascades > 0) {
            for (i in 0 until cascades) {
                val fit = frame.cascades[i]
                shader.uploadMat4f(CASCADE_MATRIX_NAMES[i], fit.matrix)
                vec4Set(splits, i, frame.splits[i + 1])
                vec4Set(normalOffsets, i, ShadowBias.normalOffset(fit.texelWorldSize, pcf, frame.extraNormalOffset))
                vec4Set(depthBiases, i, ShadowBias.depthBias(fit.depthRange, frame.extraDepthBias))
            }
            shader.uploadVec4f(Uniforms.CASCADE_SPLITS, splits)
            shader.uploadVec4f(Uniforms.CASCADE_NORMAL_OFFSET, normalOffsets)
            shader.uploadVec4f(Uniforms.CASCADE_DEPTH_BIAS, depthBiases)
            shader.uploadFloat(Uniforms.SHADOW_TEXEL_SIZE, 1f / frame.cascadeResolution)
            shader.uploadFloat(Uniforms.SHADOW_FADE_START, frame.splits[cascades] * FADE_START_FRACTION)
            glActiveTexture(GL_TEXTURE0 + Uniforms.SHADOW_TEXTURE_UNIT)
            glBindTexture(GL_TEXTURE_2D_ARRAY, frame.cascadeTextureId)
        }

        if (local > 0) {
            lightIndices.fill(-1)
            lightBases.fill(0)
            for ((s, slot) in frame.localSlots.withIndex()) {
                lightIndices[s] = slot.lightIndex
                lightBases[s] = slot.baseLayer
                shader.uploadFloat(LIGHT_TEXEL_NAMES[s], LocalShadows.texelWorldSize(1f, slot.tanHalfFov, frame.localResolution))
                for (layer in slot.baseLayer until slot.baseLayer + slot.layerCount) {
                    shader.uploadMat4f(LOCAL_MATRIX_NAMES[layer], frame.localMatrices[layer])
                }
            }
            shader.uploadIntArray(Uniforms.SHADOW_LIGHT_INDEX, lightIndices)
            shader.uploadIntArray(Uniforms.SHADOW_LIGHT_BASE, lightBases)
            shader.uploadFloat(Uniforms.LOCAL_SHADOW_TEXEL_SIZE, 1f / frame.localResolution)
            glActiveTexture(GL_TEXTURE0 + Uniforms.LOCAL_SHADOW_TEXTURE_UNIT)
            glBindTexture(GL_TEXTURE_2D_ARRAY, frame.localTextureId)
        }
        glActiveTexture(GL_TEXTURE0)
    }

    /**
     * Uploads only the camera position uniform to the shader.
     *
     * @param shader The shader to upload the uniform to
     * @param cameraPosition The camera position
     */
    fun loadCameraPosition(shader: Shader, cameraPosition: Vector3f) {
        shader.uploadVec3f(Uniforms.CAMERA_POSITION, cameraPosition)
    }

    private val sunColor = Vector3f()
    private val ambient = Vector3f()
    private val splits = Vector4f()
    private val normalOffsets = Vector4f()
    private val depthBiases = Vector4f()
    private val lightIndices = IntArray(LocalShadows.MAX_SHADOWED_LIGHTS)
    private val lightBases = IntArray(LocalShadows.MAX_SHADOWED_LIGHTS)

    private fun vec4Set(v: Vector4f, i: Int, value: Float) {
        v.setComponent(i, value)
    }

    companion object {
        /** Sun shadows fade out over the last 10% of the shadow distance instead of ending in a hard line. */
        const val FADE_START_FRACTION = 0.9f
        private val ZERO = Vector3f()
        private val CASCADE_MATRIX_NAMES = Array(CascadeSplits.MAX_CASCADES) { "${Uniforms.CASCADE_MATRICES}[$it]" }
        private val LOCAL_MATRIX_NAMES = Array(LocalShadows.MAX_LAYERS) { "${Uniforms.LOCAL_SHADOW_MATRICES}[$it]" }
        private val LIGHT_TEXEL_NAMES = Array(LocalShadows.MAX_SHADOWED_LIGHTS) { "${Uniforms.SHADOW_LIGHT_TEXEL}[$it]" }

        /** Turns shadows off for draws that reuse the default shader outside the scene (thumbnails). */
        fun disableShadows(shader: Shader) {
            shader.uploadInt(Uniforms.CASCADE_SHADOW_MAP, Uniforms.SHADOW_TEXTURE_UNIT)
            shader.uploadInt(Uniforms.LOCAL_SHADOW_MAP, Uniforms.LOCAL_SHADOW_TEXTURE_UNIT)
            shader.uploadInt(Uniforms.CASCADE_COUNT, 0)
            shader.uploadInt(Uniforms.SHADOWED_LIGHT_COUNT, 0)
        }
    }
}
