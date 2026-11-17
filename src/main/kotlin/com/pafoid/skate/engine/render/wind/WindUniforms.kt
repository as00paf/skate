package com.pafoid.skate.engine.render.wind

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector2f

/** Uploads the `wind.glsl` uniforms from the scene's [EnvironmentComponent]; no environment means no wind. */
object WindUniforms {
    private val vec = Vector2f() // main thread only

    fun upload(shader: Shader, env: EnvironmentComponent?) {
        if (env == null) {
            shader.uploadFloat(Uniforms.WIND_SPEED, 0f)
            shader.uploadFloat(Uniforms.WIND_TIME, 0f)
            return
        }
        val wind = env.wind()
        shader.uploadVec2f(Uniforms.WIND_DIR, vec.set(wind.dirX, wind.dirZ))
        shader.uploadFloat(Uniforms.WIND_SPEED, wind.speed)
        shader.uploadVec2f(Uniforms.WIND_GUST, vec.set(wind.gustStrength, wind.gustScale))
        shader.uploadFloat(Uniforms.WIND_TURBULENCE, wind.turbulence)
        shader.uploadFloat(Uniforms.WIND_TIME, env.runtime.renderTime())
    }
}
