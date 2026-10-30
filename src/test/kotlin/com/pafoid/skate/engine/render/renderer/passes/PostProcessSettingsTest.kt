package com.pafoid.skate.engine.render.renderer.passes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Post Process Settings Tests")
class PostProcessSettingsTest {

    @Test
    @DisplayName("should default to an expected post processing state")
    fun shouldDefaultToExpectedPostProcessingState() {
        val settings = PostProcessSettings()

        assertTrue(settings.bloomEnabled)
        assertEquals(0.05f, settings.bloomIntensity)
        assertEquals(1.0f, settings.bloomThreshold)

        assertTrue(settings.tonemappingEnabled)
        assertEquals(1.0f, settings.acesExposure)

        assertTrue(settings.gammaCorrectionEnabled)
        assertEquals(2.2f, settings.gammaValue)

        assertTrue(settings.fxaaEnabled)
        assertEquals(0.9f, settings.fxaaStrength)

        assertFalse(settings.ssaoEnabled)
    }

    @Test
    @DisplayName("should return enabled effects in canonical pipeline order")
    fun shouldReturnEnabledEffectsInCanonicalPipelineOrder() {
        val settings = PostProcessSettings()

        assertEquals(
            listOf(
                PostEffect.BLOOM,
                PostEffect.TONEMAPPING,
                PostEffect.GAMMA_CORRECTION,
                PostEffect.FXAA,
            ),
            settings.enabledEffects()
        )
    }

    @Test
    @DisplayName("should disable and enable effects through toggle")
    fun shouldDisableAndEnableEffectsThroughToggle() {
        val settings = PostProcessSettings()

        assertFalse(settings.toggle(PostEffect.BLOOM))
        assertFalse(settings.bloomEnabled)

        assertTrue(settings.toggle(PostEffect.BLOOM))
        assertTrue(settings.bloomEnabled)
    }

    @Test
    @DisplayName("should expose the full canonical pipeline when all effects are enabled")
    fun shouldExposeFullCanonicalPipelineWhenAllEffectsAreEnabled() {
        val settings = PostProcessSettings()
        PostEffect.DEFAULT_PIPELINE.forEach { settings.setEnabled(it, true) }

        assertEquals(PostEffect.DEFAULT_PIPELINE, settings.enabledEffects())
        assertTrue(settings.isAnyEnabled())
        assertEquals(PostEffect.DEFAULT_PIPELINE.size, settings.enabledCount())
    }

    @Test
    @DisplayName("should return an empty effect list when all effects are disabled")
    fun shouldReturnEmptyEffectListWhenAllEffectsAreDisabled() {
        val settings = PostProcessSettings()
        PostEffect.DEFAULT_PIPELINE.forEach { settings.setEnabled(it, false) }

        assertTrue(settings.enabledEffects().isEmpty())
        assertFalse(settings.isAnyEnabled())
        assertEquals(0, settings.enabledCount())
    }

    @Test
    @DisplayName("should clamp effect parameters when enabling effects")
    fun shouldClampEffectParametersWhenEnablingEffects() {
        val settings = PostProcessSettings()
        settings.bloomIntensity = 42f
        settings.setEnabled(PostEffect.BLOOM, true)

        assertEquals(5f, settings.bloomIntensity)
    }

    @Test
    @DisplayName("should reset all effects and parameters to defaults")
    fun shouldResetAllEffectsAndParametersToDefaults() {
        val settings = PostProcessSettings()
        settings.toggle(PostEffect.BLOOM)
        settings.toggle(PostEffect.FXAA)
        settings.acesExposure = 4f
        settings.gammaValue = 5f
        settings.reset()

        assertTrue(settings.bloomEnabled)
        assertTrue(settings.fxaaEnabled)
        assertEquals(1.0f, settings.acesExposure)
        assertEquals(2.2f, settings.gammaValue)
    }

    @Test
    @DisplayName("should return an independent copy of the settings")
    fun shouldReturnAnIndependentCopyOfTheSettings() {
        val settings = PostProcessSettings()
        settings.toggle(PostEffect.BLOOM)
        settings.acesExposure = 2.5f

        val copy = settings.copy()

        assertFalse(copy.bloomEnabled)
        assertEquals(2.5f, copy.acesExposure)
        assertFalse(settings.bloomEnabled)

        copy.toggle(PostEffect.BLOOM)
        assertTrue(copy.bloomEnabled)
        assertFalse(settings.bloomEnabled)
    }
}
