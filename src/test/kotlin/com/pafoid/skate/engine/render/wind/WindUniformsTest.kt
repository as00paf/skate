package com.pafoid.skate.engine.render.wind

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector2f
import org.junit.jupiter.api.Test

class WindUniformsTest {
    @Test
    fun WindUniforms_UploadTheEnvironmentWindAndClock() {
        val shader = mockk<Shader>(relaxed = true)
        val env = EnvironmentComponent(windDirection = 0f, windSpeed = 8f, windGustStrength = 0.5f, windGustScale = 12f, windTurbulence = 0.7f)
        repeat(3) { env.runtime.step(0.5f, running = true, animateInEditor = false) }

        WindUniforms.upload(shader, env)

        verify { shader.uploadFloat(Uniforms.WIND_SPEED, 8f) }
        verify { shader.uploadFloat(Uniforms.WIND_TURBULENCE, 0.7f) }
        verify { shader.uploadFloat(Uniforms.WIND_TIME, 1.5f) }
        verify { shader.uploadVec2f(Uniforms.WIND_GUST, Vector2f(0.5f, 12f)) }
    }

    @Test
    fun WindUniforms_NoEnvironment_MeansNoWind() {
        val shader = mockk<Shader>(relaxed = true)
        WindUniforms.upload(shader, null)
        verify { shader.uploadFloat(Uniforms.WIND_SPEED, 0f) }
    }
}
