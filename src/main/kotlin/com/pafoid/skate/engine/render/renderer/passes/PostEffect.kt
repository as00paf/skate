package com.pafoid.skate.engine.render.renderer.passes

/**
 * Post-processing effects available in the render post-processing stack.
 *
 * Each effect is a member of the canonical [PostEffect.DEFAULT_PIPELINE] and
 * can be enabled/disabled individually through [PostProcessSettings].
 */
enum class PostEffect(
    val displayName: String,
    val description: String,
) {
    BLOOM("Bloom", "Adds glow to bright areas of the rendered image"),
    TONEMAPPING("Tonemapping", "Applies ACES filmic tonemapping"),
    GAMMA_CORRECTION("Gamma Correction", "Applies linear-to-sRGB gamma correction"),
    FXAA("FXAA", "Applies fast approximate anti-aliasing"),
    SSAO("SSAO", "Applies screen-space ambient occlusion");

    companion object {

        /**
         * Canonical execution order of the post-processing stack.
         *
         * Effects run in this order whenever more than one effect is enabled:
         * ambient occlusion first, then bloom, tonemapping, gamma correction and
         * finally anti-aliasing. The list is the single source of truth for both
         * the execution order and the enable/disable behaviour.
         */
        val DEFAULT_PIPELINE: List<PostEffect> = listOf(
            PostEffect.SSAO,
            PostEffect.BLOOM,
            PostEffect.TONEMAPPING,
            PostEffect.GAMMA_CORRECTION,
            PostEffect.FXAA,
        )
    }
}
