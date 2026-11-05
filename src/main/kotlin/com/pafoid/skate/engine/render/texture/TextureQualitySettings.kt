package com.pafoid.skate.engine.render.texture

import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.assets.data.Texture

/**
 * Global texture quality. [apply] sets sampler state on already-uploaded textures when the setting changes;
 * [applyTo] updates one texture. Main thread only.
 */
object TextureQualitySettings {
    var level: TextureQualityLevel = TextureQualityLevel.HIGH
        private set

    fun sampling(): TextureSampling = TextureSampling.forQuality(level, RhiContext.require().caps.maxAnisotropy)

    fun apply(newLevel: TextureQualityLevel, textures: Collection<Texture>) {
        level = newLevel
        textures.forEach { texture -> texture.rhi?.let(::applyTo) }
    }

    /** Applies the current sampling (minification filter and anisotropy) to [texture]. */
    fun applyTo(texture: RhiTexture) {
        val s = sampling()
        val filter = when (s.minFilter) {
            MinFilter.LINEAR -> Filter.LINEAR
            MinFilter.NEAREST_MIPMAP_LINEAR -> Filter.NEAREST_MIPMAP_LINEAR
            MinFilter.LINEAR_MIPMAP_LINEAR -> Filter.LINEAR_MIPMAP_LINEAR
        }
        texture.setSampler(texture.sampler.copy(minFilter = filter, anisotropy = s.maxAnisotropy))
    }
}
