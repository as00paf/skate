package com.pafoid.skate.engine.render.fog

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector3f

/** Uploads the `fog.glsl` uniforms from the scene's [EnvironmentComponent]; the one place fog settings reach shaders. */
object FogUniforms {
    private val DEFAULT_COLOR = Vector3f(0.8f, 0.8f, 0.8f)

    fun upload(shader: Shader, env: EnvironmentComponent?) {
        val mode = FogMath.shaderMode(env)
        shader.uploadInt(Uniforms.FOG_MODE, mode)
        shader.uploadVec3f(Uniforms.FOG_COLOR, env?.fogColor ?: DEFAULT_COLOR)
        if (mode == 0 || env == null) return
        shader.uploadFloat(Uniforms.FOG_DENSITY, env.fogDensity)
        shader.uploadFloat(Uniforms.FOG_START, env.fogStart)
        shader.uploadFloat(Uniforms.FOG_END, env.fogEnd)
        shader.uploadFloat(Uniforms.FOG_HEIGHT, env.fogHeight)
        shader.uploadFloat(Uniforms.FOG_HEIGHT_FALLOFF, env.fogHeightFalloff)
    }
}
