package com.pafoid.skate.engine.render.water

import com.pafoid.skate.engine.render.renderer.passes.PostProcessQuality
import com.pafoid.skate.engine.render.renderer.passes.PostProcessQualityPreset
import com.pafoid.skate.engine.render.renderer.passes.PostProcessSettings
import com.pafoid.skate.engine.render.rhi.BlitMask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** REN-29 task 8: SSR steps follow the quality preset; the shader falls back to IBL. Task 7: the depth blit mask. */
class WaterSsrQualityTest {
    @Test
    fun Ssr_StepsFollowTheQualityPreset() {
        val expected = mapOf(
            PostProcessQuality.LOW to 0,
            PostProcessQuality.MEDIUM to 16,
            PostProcessQuality.HIGH to 32,
            PostProcessQuality.ULTRA to 32,
        )
        for ((quality, steps) in expected) {
            val settings = PostProcessSettings()
            PostProcessQualityPreset.apply(quality, settings)
            assertEquals(steps, settings.ssrSteps, quality.name)
            assertEquals(steps, settings.copy().ssrSteps, "copied")
        }
        assertEquals(PostProcessSettings.SSR_STEPS_MEDIUM, PostProcessSettings().apply { ssrSteps = 0; reset() }.ssrSteps)
    }

    @Test
    fun Ssr_ShaderMarchesTheDepthCopy_AndFallsBackToIbl() {
        val glsl = File("assets/Shaders/water.glsl").readText()
        assertTrue("if (!uSsrEnabled || uSsrSteps <= 0) return vec4(0.0);" in glsl)
        assertTrue("mix(envReflection, ssr.rgb, ssr.a)" in glsl)
        assertTrue("texture(uSceneDepth" in glsl)
        // The surface never samples the scene attachments it draws into: only the copies are declared
        assertFalse("uSceneDepthLive" in glsl)
    }

    @Test
    fun BlitMask_DepthOnlyWhereAsked() {
        assertTrue(BlitMask.COLOR.color && !BlitMask.COLOR.depth)
        assertTrue(BlitMask.COLOR_DEPTH.color && BlitMask.COLOR_DEPTH.depth)
        assertTrue(!BlitMask.DEPTH.color && BlitMask.DEPTH.depth)
    }
}
