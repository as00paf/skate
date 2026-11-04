package com.pafoid.skate.engine.render.renderer.passes

/**
 * Runtime state of the post-processing stack.
 *
 * Holds the enable flag and the tunable parameters of every [PostEffect] and
 * resolves which effects have to run for the current frame, in the canonical
 * [PostEffect.DEFAULT_PIPELINE] order. All logic here is pure and unit-testable
 * without a GL context.
 *
 * @param bloomEnabled Whether the bloom effect is enabled.
 * @param bloomIntensity Bloom strength multiplier.
 * @param bloomThreshold Bright threshold the bloom effect reacts to.
 * @param tonemappingEnabled Whether ACES tonemapping is enabled.
 * @param acesExposure Exposure applied during ACES tonemapping.
 * @param gammaCorrectionEnabled Whether gamma correction is enabled.
 * @param gammaValue Linear-to-sRGB gamma exponent.
 * @param fxaaEnabled Whether FXAA anti-aliasing is enabled.
 * @param fxaaStrength Mix factor between the original image and the FXAA result.
 * @param ssaoEnabled Whether screen-space ambient occlusion is enabled.
 * @param ssaoRadius Ambient occlusion radius in depth samples.
 * @param ssaoIntensity Ambient occlusion strength.
 */
class PostProcessSettings {

    var bloomEnabled: Boolean = true
    var bloomIntensity: Float = 0.05f
    var bloomThreshold: Float = 1.0f

    var tonemappingEnabled: Boolean = true
    var acesExposure: Float = 1.0f

    var gammaCorrectionEnabled: Boolean = true
    var gammaValue: Float = 2.2f

    var fxaaEnabled: Boolean = true
    var fxaaStrength: Float = 0.9f

    var ssaoEnabled: Boolean = false
    var ssaoRadius: Float = 1.5f
    var ssaoIntensity: Float = 0.8f

    /** The canonical post-processing pipeline order. */
    val pipelineOrder: List<PostEffect>
        get() = PostEffect.DEFAULT_PIPELINE

    /** Returns true when the given effect is currently enabled. */
    fun isEnabled(effect: PostEffect): Boolean = when (effect) {
        PostEffect.SSAO -> ssaoEnabled
        PostEffect.BLOOM -> bloomEnabled
        PostEffect.TONEMAPPING -> tonemappingEnabled
        PostEffect.GAMMA_CORRECTION -> gammaCorrectionEnabled
        PostEffect.FXAA -> fxaaEnabled
    }

    /** Enables or disables the given effect and clamps its parameters. */
    fun setEnabled(effect: PostEffect, enabled: Boolean) {
        when (effect) {
            PostEffect.SSAO -> {
                ssaoEnabled = enabled
                if (enabled) ssaoIntensity = ssaoIntensity.coerceIn(0f, 1f)
            }
            PostEffect.BLOOM -> {
                bloomEnabled = enabled
                if (enabled) bloomIntensity = bloomIntensity.coerceIn(0f, 5f)
            }
            PostEffect.TONEMAPPING -> tonemappingEnabled = enabled
            PostEffect.GAMMA_CORRECTION -> gammaCorrectionEnabled = enabled
            PostEffect.FXAA -> {
                fxaaEnabled = enabled
                if (enabled) fxaaStrength = fxaaStrength.coerceIn(0f, 1f)
            }
        }
    }

    /** Toggles the given effect and returns the new enabled state. */
    fun toggle(effect: PostEffect): Boolean {
        val next = !isEnabled(effect)
        setEnabled(effect, next)
        return next
    }

    /** Returns the enabled effects in canonical pipeline order. */
    fun enabledEffects(): List<PostEffect> = pipelineOrder.filter { isEnabled(it) }

    /** Returns true when at least one post-processing effect is enabled. */
    fun isAnyEnabled(): Boolean = enabledEffects().isNotEmpty()

    /** Returns the number of enabled effects. */
    fun enabledCount(): Int = enabledEffects().size

    /** Restores all effects and parameters to their defaults. */
    fun reset() {
        bloomEnabled = true
        bloomIntensity = 0.05f
        bloomThreshold = 1.0f

        tonemappingEnabled = true
        acesExposure = 1.0f

        gammaCorrectionEnabled = true
        gammaValue = 2.2f

        fxaaEnabled = true
        fxaaStrength = 0.9f

        ssaoEnabled = false
        ssaoRadius = 1.5f
        ssaoIntensity = 0.8f
    }

    /** Returns a copy so callers cannot mutate shared state unintentionally. */
    fun copy(): PostProcessSettings {
        val copy = PostProcessSettings()
        copy.bloomEnabled = bloomEnabled
        copy.bloomIntensity = bloomIntensity
        copy.bloomThreshold = bloomThreshold
        copy.tonemappingEnabled = tonemappingEnabled
        copy.acesExposure = acesExposure
        copy.gammaCorrectionEnabled = gammaCorrectionEnabled
        copy.gammaValue = gammaValue
        copy.fxaaEnabled = fxaaEnabled
        copy.fxaaStrength = fxaaStrength
        copy.ssaoEnabled = ssaoEnabled
        copy.ssaoRadius = ssaoRadius
        copy.ssaoIntensity = ssaoIntensity
        return copy
    }

    companion object {
        /** Default post-processing state used when none is configured. */
        val DEFAULT: PostProcessSettings = PostProcessSettings()
    }
}
