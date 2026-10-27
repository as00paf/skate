package com.pafoid.skate.engine.render.texture

/** Engine-side texture quality tiers (the editor maps its `TextureQuality` setting onto these). */
enum class TextureQualityLevel { LOW, MEDIUM, HIGH }

enum class MinFilter { LINEAR, NEAREST_MIPMAP_LINEAR, LINEAR_MIPMAP_LINEAR }

/** Resolved sampler parameters for one quality tier. */
data class TextureSampling(val minFilter: MinFilter, val maxAnisotropy: Float, val generateMipmaps: Boolean) {
    companion object {
        /**
         * LOW: bilinear with nearest mip, no anisotropy; MEDIUM: trilinear, up to 4x; HIGH: trilinear, up to 16x.
         * Anisotropy is clamped to what the GPU supports ([hardwareMaxAnisotropy] <= 1 means unsupported).
         */
        fun forQuality(level: TextureQualityLevel, hardwareMaxAnisotropy: Float): TextureSampling {
            val wanted = when (level) {
                TextureQualityLevel.LOW -> 1f
                TextureQualityLevel.MEDIUM -> 4f
                TextureQualityLevel.HIGH -> 16f
            }
            val aniso = wanted.coerceAtMost(maxOf(1f, hardwareMaxAnisotropy))
            val filter = if (level == TextureQualityLevel.LOW) MinFilter.NEAREST_MIPMAP_LINEAR else MinFilter.LINEAR_MIPMAP_LINEAR
            return TextureSampling(filter, aniso, generateMipmaps = true)
        }
    }
}
