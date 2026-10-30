package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.VAOLoader
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Post Process Pass Tests")
class PostProcessPassTest {

    private fun createPostProcessPass(
        settings: PostProcessSettings = PostProcessSettings(),
    ): PostProcessPass {
        val frameBuffer = mockk<FrameBuffer>(relaxed = true)
        val bloomShader = mockk<Shader>()
        val tonemapShader = mockk<Shader>()
        val gammaShader = mockk<Shader>()
        val fxaaShader = mockk<Shader>()
        val ssaoShader = mockk<Shader>()
        val vaoLoader = mockk<VAOLoader>(relaxed = true)
        return PostProcessPass(
            frameBuffer = frameBuffer,
            useFbo = true,
            bloomShader = bloomShader,
            tonemapShader = tonemapShader,
            gammaShader = gammaShader,
            fxaaShader = fxaaShader,
            ssaoShader = ssaoShader,
            vaoLoader = vaoLoader,
            settings = settings,
        )
    }

    @Test
    @DisplayName("should resolve enabled effects in canonical pipeline order")
    fun shouldResolveEnabledEffectsInCanonicalPipelineOrder() {
        val pass = createPostProcessPass()

        assertEquals(
            listOf(
                PostEffect.BLOOM,
                PostEffect.TONEMAPPING,
                PostEffect.GAMMA_CORRECTION,
                PostEffect.FXAA,
            ),
            pass.resolvedEffects()
        )
    }

    @Test
    @DisplayName("should resolve no effects when the post process pass is disabled")
    fun shouldResolveNoEffectsWhenPassIsDisabled() {
        val pass = createPostProcessPass()
        pass.toggleEnable()

        assertFalse(pass.isEnabled)
        assertTrue(pass.resolvedEffects().isEmpty())
    }

    @Test
    @DisplayName("should restore resolved effects when the pass is re-enabled")
    fun shouldRestoreResolvedEffectsWhenPassIsReEnabled() {
        val pass = createPostProcessPass()
        pass.toggleEnable()
        pass.toggleEnable()

        assertTrue(pass.isEnabled)
        assertEquals(
            listOf(
                PostEffect.BLOOM,
                PostEffect.TONEMAPPING,
                PostEffect.GAMMA_CORRECTION,
                PostEffect.FXAA,
            ),
            pass.resolvedEffects()
        )
    }

    @Test
    @DisplayName("should not allow disabling the post process pass when canDisable is false")
    fun shouldNotAllowDisablingPostProcessPassWhenCanDisableIsFalse() {
        val pass = FixedPostProcessPass(
            frameBuffer = mockk<FrameBuffer>(relaxed = true),
            useFbo = true,
            bloomShader = mockk<Shader>(),
            tonemapShader = mockk<Shader>(),
            gammaShader = mockk<Shader>(),
            fxaaShader = mockk<Shader>(),
            ssaoShader = mockk<Shader>(),
            vaoLoader = mockk<VAOLoader>(relaxed = true),
        )

        pass.toggleEnable()

        assertTrue(pass.isEnabled)
        assertEquals(4, pass.resolvedEffects().size)
    }

    @Test
    @DisplayName("should toggle individual effects through the pass")
    fun shouldToggleIndividualEffectsThroughThePass() {
        val pass = createPostProcessPass()

        assertTrue(pass.isEffectEnabled(PostEffect.BLOOM))
        assertFalse(pass.isEffectEnabled(PostEffect.SSAO))

        assertTrue(pass.toggleEffect(PostEffect.SSAO))
        assertTrue(pass.isEffectEnabled(PostEffect.SSAO))
        assertFalse(pass.toggleEffect(PostEffect.SSAO))
        assertFalse(pass.isEffectEnabled(PostEffect.SSAO))
    }

    @Test
    @DisplayName("should skip execution when no post processing effect is enabled")
    fun shouldSkipExecutionWhenNoPostProcessingEffectIsEnabled() {
        val settings = PostProcessSettings().apply {
            PostEffect.DEFAULT_PIPELINE.forEach { effect -> setEnabled(effect, false) }
        }
        val pass = createPostProcessPass(settings)

        pass.execute(mockk<Scene>(relaxed = true))

        // No effects enabled -> nothing to render, and no GL state was touched.
        assertTrue(pass.resolvedEffects().isEmpty())
    }

    @Test
    @DisplayName("should skip execution when the post process pass is disabled")
    fun shouldSkipExecutionWhenPostProcessPassIsDisabled() {
        val pass = createPostProcessPass()
        pass.toggleEnable()
        PostEffect.DEFAULT_PIPELINE.forEach { pass.toggleEffect(it) }

        pass.execute(mockk<Scene>(relaxed = true))

        assertTrue(pass.resolvedEffects().isEmpty())
    }

    private class FixedPostProcessPass(
        frameBuffer: FrameBuffer,
        useFbo: Boolean,
        bloomShader: Shader,
        tonemapShader: Shader,
        gammaShader: Shader,
        fxaaShader: Shader,
        ssaoShader: Shader,
        vaoLoader: VAOLoader,
    ) : PostProcessPass(
        frameBuffer = frameBuffer,
        useFbo = useFbo,
        bloomShader = bloomShader,
        tonemapShader = tonemapShader,
        gammaShader = gammaShader,
        fxaaShader = fxaaShader,
        ssaoShader = ssaoShader,
        vaoLoader = vaoLoader,
    ) {
        override val canDisable: Boolean = false
    }
}
