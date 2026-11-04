package com.pafoid.skate.engine.render.fog

import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.config.FogCycle
import com.pafoid.skate.engine.ecs.config.FogMode
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.exp

/** REN-09: the fog math shared by models, sky and particles (`shaders/fog.glsl` mirrors [FogMath]). */
class FogMathTest {
    private val eps = 1e-4f

    @Test
    fun FogMath_LinearRampsFromStartToEnd() {
        assertEquals(0f, FogMath.distanceAmount(FogMode.LINEAR, 5f, 0f, 10f, 30f), eps)
        assertEquals(0.5f, FogMath.distanceAmount(FogMode.LINEAR, 20f, 0f, 10f, 30f), eps)
        assertEquals(1f, FogMath.distanceAmount(FogMode.LINEAR, 99f, 0f, 10f, 30f), eps)
    }

    @Test
    fun FogMath_ExpAndExp2MatchTheirCurves() {
        assertEquals(1f - exp(-0.05f * 20f), FogMath.distanceAmount(FogMode.EXP, 20f, 0.05f, 0f, 0f), eps)
        assertEquals(1f - exp(-1f), FogMath.distanceAmount(FogMode.EXP2, 20f, 0.05f, 0f, 0f), eps)
        // exp2 stays clearer than exp near the camera and catches up further away
        assertTrue(FogMath.distanceAmount(FogMode.EXP2, 5f, 0.05f, 0f, 0f) < FogMath.distanceAmount(FogMode.EXP, 5f, 0.05f, 0f, 0f))
        assertEquals(0f, FogMath.distanceAmount(FogMode.EXP, 0f, 0.05f, 0f, 0f), eps)
    }

    @Test
    fun FogMath_HeightFogIsUniformWithoutFalloffAndThinsAbove() {
        val cam = Vector3f(0f, 2f, 0f)
        assertEquals(1f, FogMath.heightWeight(cam, Vector3f(0f, 50f, -100f), 0f, 0f), eps)
        // Level ray: weight is the density at the camera height
        assertEquals(exp(-0.2f * 2f), FogMath.heightWeight(cam, Vector3f(0f, 2f, -50f), 0f, 0.2f), eps)
        val up = FogMath.heightWeight(cam, Vector3f(0f, 40f, -50f), 0f, 0.2f)
        val down = FogMath.heightWeight(cam, Vector3f(0f, 0f, -50f), 0f, 0.2f)
        assertTrue(up < exp(-0.2f * 2f) && exp(-0.2f * 2f) < down, "rays upward see less fog than rays toward the ground")
        assertEquals(1f, FogMath.heightWeight(Vector3f(0f, -5f, 0f), Vector3f(0f, -5f, -10f), 0f, 0.2f), eps, "capped at 1 below the fog height")
    }

    @Test
    fun FogMath_ShaderModeFollowsRenderFogAndSettings() {
        val env = EnvironmentComponent()
        assertEquals(0, FogMath.shaderMode(env), "density 0: no fog")
        env.fogDensity = 0.02f
        assertEquals(3, FogMath.shaderMode(env))
        env.fogMode = FogMode.EXP
        assertEquals(2, FogMath.shaderMode(env))
        env.fogMode = FogMode.LINEAR
        assertEquals(1, FogMath.shaderMode(env))
        env.renderFog = false
        assertEquals(0, FogMath.shaderMode(env))
        assertEquals(0, FogMath.shaderMode(null))
    }

    @Test
    fun FogMath_SkyAtTheFarPlaneMatchesGeometryAtTheFarPlane() {
        val env = EnvironmentComponent(fogDensity = 0.01f, fogHeightFalloff = 0.05f)
        val cam = Vector3f(0f, 1.7f, 0f)
        val far = 1000f
        // The sky shader fogs the point at the far plane along each view ray, as geometry there is fogged
        val dir = Vector3f(0.3f, 0.05f, -1f).normalize()
        val skyPoint = Vector3f(dir).mul(far).add(cam)
        assertEquals(FogMath.amount(env, cam, skyPoint), FogMath.amount(env, cam, Vector3f(skyPoint)), eps)
        assertTrue(FogMath.amount(env, cam, skyPoint) > FogMath.amount(env, cam, Vector3f(dir).mul(20f).add(cam)))
    }

    @Test
    fun FogCycle_StepsThroughEveryModeAndBackToOff() {
        val env = EnvironmentComponent(renderFog = false)
        val steps = List(5) { FogCycle.next(env) }
        assertEquals(listOf(FogCycle.Step.LINEAR, FogCycle.Step.EXP, FogCycle.Step.EXP2, FogCycle.Step.HEIGHT, FogCycle.Step.OFF), steps)
        assertEquals(0, FogMath.shaderMode(env))
        FogCycle.next(env)
        assertEquals(1, FogMath.shaderMode(env), "linear step renders fog")
    }
}
