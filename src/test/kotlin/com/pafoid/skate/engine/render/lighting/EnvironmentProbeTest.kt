package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.PI

/** REN-27: when the probe re-bakes, what it gives the mesh shader, and the sky rotation / light scale it shares. */
class EnvironmentProbeTest {
    private val device = FakeRhiDevice()
    private val probe = EnvironmentProbe(mockk<Shader>(relaxed = true)) { device }
    private val shader = mockk<Shader>(relaxed = true)

    private fun skyTexture() = Texture(1024, 512).also {
        it.rhi = device.createTexture(TextureDesc(TextureType.TEXTURE_2D, PixelFormat.RGBA8, 1024, 512, mipLevels = 11))
    }

    private val environment = EnvironmentComponent(skyTexture = skyTexture())

    @BeforeEach
    fun setUp() { RhiContext.device = device }

    @AfterEach
    fun tearDown() { RhiContext.device = null }

    @Test
    fun bakesOnce_ThenOnlyWhenTheSkyChanges() {
        probe.update(environment, null)
        probe.update(environment, null)
        assertEquals(1, probe.bakeCount)

        probe.update(environment, DayNightCycleComponent(timeOfDay = 18f)) // rotation only: a uniform
        assertEquals(1, probe.bakeCount)

        environment.skyExposure = 1.5f
        probe.update(environment, null)
        environment.fogColor.set(0.1f, 0.2f, 0.3f)
        probe.update(environment, null)
        environment.skyTint.set(1f, 0.9f, 0.8f)
        probe.update(environment, null)
        environment.skyTexture = skyTexture()
        probe.update(environment, null)
        assertEquals(5, probe.bakeCount)
    }

    @Test
    fun bind_GivesTheShaderTheEnvironment() {
        probe.update(environment, null)
        probe.bind(shader)
        verify { shader.uploadInt(Uniforms.ENV_ENABLED, 1) }
        verify { shader.uploadInt(Uniforms.ENV_PREFILTERED, Uniforms.ENV_PREFILTERED_UNIT) }
        verify { shader.uploadInt(Uniforms.ENV_BRDF_LUT, Uniforms.ENV_BRDF_LUT_UNIT) }
        verify { shader.uploadFloat(Uniforms.ENV_MAX_LOD, (EnvironmentBake.LEVELS - 1).toFloat()) }
        verify { shader.uploadFloat(Uniforms.ENV_REFLECTION, 1f) }
        verify(exactly = 9) { shader.uploadVec4f(match { it.startsWith(Uniforms.ENV_SH + "[") }, any()) }
    }

    @Test
    fun noSky_NoImageBasedLighting() {
        for (env in listOf(null, EnvironmentComponent(), EnvironmentComponent(skyTexture = skyTexture(), renderSky = false))) {
            probe.update(env, null)
            probe.bind(shader)
        }
        verify(exactly = 3) { shader.uploadInt(Uniforms.ENV_ENABLED, 0) }
        assertEquals(0, probe.bakeCount)
    }

    @Test
    fun skyRotation_FollowsTheTimeOfDayAndTheOffset() {
        val env = EnvironmentComponent()
        assertEquals(0f, EnvironmentProbe.skyRotation(env, null), 1e-6f, "noon, no offset")
        assertEquals(-PI.toFloat() / 2f, EnvironmentProbe.skyRotation(env, DayNightCycleComponent(timeOfDay = 18f)), 1e-5f)
        env.skyRotation = 90f
        assertEquals(PI.toFloat() / 2f, EnvironmentProbe.skyRotation(env, null), 1e-5f)
    }

    @Test
    fun lightScale_DimsAtNightWithTheCycle() {
        assertEquals(1f, EnvironmentProbe.environmentLightScale(null))
        assertEquals(1f, EnvironmentProbe.environmentLightScale(DayNightCycleComponent(timeOfDay = 12f)))
        assertEquals(EnvironmentProbe.NIGHT_LIGHT, EnvironmentProbe.environmentLightScale(DayNightCycleComponent(timeOfDay = 23f)))
    }

    @Test
    fun captureLod_AveragesTheSkyDownToTheCaptureWidth() {
        assertEquals(2f, EnvironmentProbe.captureLod(1024, 11), 1e-5f)
        assertEquals(0f, EnvironmentProbe.captureLod(128, 8), 1e-5f)
        assertEquals(1f, EnvironmentProbe.captureLod(4096, 2), 1e-5f, "clamped to the texture's mips")
    }
}
