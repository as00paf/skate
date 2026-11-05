package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT
import org.lwjgl.opengl.GL11.GL_NONE
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameterf
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R
import org.lwjgl.opengl.GL12.glTexImage3D
import org.lwjgl.opengl.GL12.glTexSubImage3D
import org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X
import org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_FUNC
import org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE
import org.lwjgl.opengl.GL30.GL_COMPARE_REF_TO_TEXTURE
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.GL30.glGenerateMipmap
import java.nio.ByteBuffer

/**
 * A GL texture. Every call binds it on the active unit with raw GL, then invalidates [GLBindings] so the binding
 * cache never skips a later bind of what was there before.
 *
 * @param anisotropyAvailable Whether the context has anisotropic filtering (otherwise anisotropy is ignored)
 */
internal class GlTexture(
    override val desc: TextureDesc,
    private val anisotropyAvailable: Boolean,
) : RhiTexture {
    val id: Int = glGenTextures()
    private val target = GlFormats.textureTarget(desc.type)
    private val layersAllocated = BooleanArray(desc.mipLevels)
    private var closed = false

    override var sampler: SamplerDesc = desc.sampler
        private set

    override val nativeHandle: Long get() = id.toLong()

    init {
        withBound { applySampler(desc.sampler, previous = null) }
    }

    private inline fun withBound(block: () -> Unit) {
        glBindTexture(target, id)
        block()
        GLBindings.cache.invalidate()
    }

    override fun upload(level: Int, layer: Int, data: ByteBuffer?) {
        require(level in 0 until desc.mipLevels) { "Level $level outside 0..${desc.mipLevels - 1}" }
        require(layer in 0 until desc.layers) { "Layer $layer outside 0..${desc.layers - 1}" }
        val w = maxOf(1, desc.width shr level)
        val h = maxOf(1, desc.height shr level)
        val (internal, format, type) = GlFormats.texture(desc.format)
        withBound {
            when (desc.type) {
                TextureType.TEXTURE_2D -> glTexImage2D(GL_TEXTURE_2D, level, internal, w, h, 0, format, type, data)
                TextureType.CUBE -> glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + layer, level, internal, w, h, 0, format, type, data)
                TextureType.TEXTURE_2D_ARRAY -> {
                    if (!layersAllocated[level]) {
                        glTexImage3D(GL_TEXTURE_2D_ARRAY, level, internal, w, h, desc.layers, 0, format, type, null as ByteBuffer?)
                        layersAllocated[level] = true
                    }
                    if (data != null) glTexSubImage3D(GL_TEXTURE_2D_ARRAY, level, 0, 0, layer, w, h, 1, format, type, data)
                }
            }
        }
    }

    override fun generateMips() = withBound { glGenerateMipmap(target) }

    override fun setSampler(sampler: SamplerDesc) {
        val previous = this.sampler
        if (previous == sampler) return
        withBound { applySampler(sampler, previous) }
    }

    /** Sets the parameters that differ from [previous] (all of them, except a default anisotropy, when null). */
    private fun applySampler(s: SamplerDesc, previous: SamplerDesc?) {
        if (previous?.wrap != s.wrap) {
            val wrap = GlFormats.wrap(s.wrap)
            glTexParameteri(target, GL_TEXTURE_WRAP_S, wrap)
            if (desc.type != TextureType.TEXTURE_2D) glTexParameteri(target, GL_TEXTURE_WRAP_R, wrap)
        }
        if (previous?.wrapV != s.wrapV) glTexParameteri(target, GL_TEXTURE_WRAP_T, GlFormats.wrap(s.wrapV))
        if (previous?.minFilter != s.minFilter) glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GlFormats.filter(s.minFilter))
        if (previous?.magFilter != s.magFilter) glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GlFormats.filter(s.magFilter))
        if (anisotropyAvailable && (previous?.anisotropy ?: 1f) != s.anisotropy) {
            glTexParameterf(target, GL_TEXTURE_MAX_ANISOTROPY_EXT, s.anisotropy)
        }
        if (previous?.compare != s.compare) {
            val compare = s.compare
            if (compare != null) {
                glTexParameteri(target, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE)
                glTexParameteri(target, GL_TEXTURE_COMPARE_FUNC, GlFormats.compare(compare))
            } else if (previous != null) {
                glTexParameteri(target, GL_TEXTURE_COMPARE_MODE, GL_NONE)
            }
        }
        this.sampler = s
    }

    override fun close() {
        if (closed) return
        closed = true
        glDeleteTextures(id)
    }
}
