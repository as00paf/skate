package com.pafoid.skate.engine.render.texture

import com.pafoid.skate.engine.assets.data.Texture
import org.lwjgl.opengl.EXTTextureFilterAnisotropic
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR
import org.lwjgl.opengl.GL11.GL_NEAREST_MIPMAP_LINEAR
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glGetFloat
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL30.glGenerateMipmap
import org.lwjgl.opengl.GL11.glTexParameterf
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.GL

/**
 * Global texture quality. [apply] sets sampler state on already-uploaded textures when the setting changes;
 * the texture loader calls [applyTo] for each new upload. Must be called on the GL thread.
 */
object TextureQualitySettings {
    var level: TextureQualityLevel = TextureQualityLevel.HIGH
        private set

    private var hardwareAnisotropy = -1f

    fun sampling(): TextureSampling = TextureSampling.forQuality(level, hardwareMaxAnisotropy())

    fun apply(newLevel: TextureQualityLevel, textures: Collection<Texture>) {
        level = newLevel
        textures.filter { it.texId > 0 }.forEach { applyTo(it.texId) }
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    /** Applies the current sampling to [texId] (leaves it bound to GL_TEXTURE_2D). */
    fun applyTo(texId: Int) {
        val s = sampling()
        glBindTexture(GL_TEXTURE_2D, texId)
        val filter = when (s.minFilter) {
            MinFilter.LINEAR -> GL_LINEAR
            MinFilter.NEAREST_MIPMAP_LINEAR -> GL_NEAREST_MIPMAP_LINEAR
            MinFilter.LINEAR_MIPMAP_LINEAR -> GL_LINEAR_MIPMAP_LINEAR
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter)
        if (hardwareMaxAnisotropy() > 1f) {
            glTexParameterf(GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, s.maxAnisotropy)
        }
    }

    private fun hardwareMaxAnisotropy(): Float {
        if (hardwareAnisotropy < 0f) {
            val caps: GLCapabilities = GL.getCapabilities()
            hardwareAnisotropy = if (caps.GL_EXT_texture_filter_anisotropic) {
                glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
            } else 1f
        }
        return hardwareAnisotropy
    }
}
