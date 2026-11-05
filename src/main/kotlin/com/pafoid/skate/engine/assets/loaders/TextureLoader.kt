package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import org.lwjgl.stb.STBImage.stbi_failure_reason
import com.pafoid.skate.engine.assets.data.Texture
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBImage.stbi_image_free
import org.lwjgl.stb.STBImage.stbi_load
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load
import org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load_thread
import org.lwjgl.system.MemoryStack
import java.nio.ByteBuffer

/** CPU-side decoded pixels (owned by stb); hand to [TextureLoader.upload] or [TextureLoader.discard]. */
class TextureData(val width: Int, val height: Int, val pixels: ByteBuffer, val flip: Boolean, val filePath: String?)

class TextureLoader(private val device: () -> RhiDevice = RhiContext::require) {

    /** Thread-safe: touches no GL state. */
    fun decodeFromFile(filePath: String, flipOnLoad: Boolean = false): TextureData = MemoryStack.stackPush().use { stack ->
        val w = stack.mallocInt(1); val h = stack.mallocInt(1); val c = stack.mallocInt(1)
        stbi_set_flip_vertically_on_load_thread(if (flipOnLoad) 1 else 0)
        val image = stbi_load(filePath, w, h, c, 4)
            ?: throw IllegalStateException("Could not decode texture $filePath")
        TextureData(w.get(0), h.get(0), image, flipOnLoad, filePath)
    }

    /** Must be called on the GL thread. Frees the decoded pixels. */
    fun upload(data: TextureData): Texture {
        val texture = Texture(data.width, data.height, 1, 4, data.flip, data.filePath)
        uploadToGPU(texture, data.pixels)
        return texture
    }

    fun discard(data: TextureData) = stbi_image_free(data.pixels)

    /** Hot reload: swaps the GPU payload of [texture] for [data] while keeping the same object. GL thread only. */
    fun replace(texture: Texture, data: TextureData) {
        delete(texture)
        texture.width = data.width
        texture.height = data.height
        texture.channels = 4
        texture.flip = data.flip
        uploadToGPU(texture, data.pixels)
    }

    fun delete(texture: Texture) {
        texture.rhi?.close()
        texture.rhi = null
        texture.texId = -1
    }

    fun loadFromFile(filePath: String, flipOnLoad: Boolean = false): Texture {
        val width = BufferUtils.createIntBuffer(1)
        val height = BufferUtils.createIntBuffer(1)
        val channels = BufferUtils.createIntBuffer(1)
        stbi_set_flip_vertically_on_load(flipOnLoad)
        val image = stbi_load(filePath, width, height, channels, 4) // Force 4 channels
            ?: throw IllegalStateException("Could not decode texture $filePath: ${stbi_failure_reason()}")
        val texture = Texture(width.get(0), height.get(0), 1, 4, flipOnLoad, filePath)
        uploadToGPU(texture, image)

        return texture
    }

    fun loadFromBuffer(buffer: ByteBuffer, flipOnLoad: Boolean = false): Texture {
        val width = BufferUtils.createIntBuffer(1)
        val height = BufferUtils.createIntBuffer(1)
        val channels = BufferUtils.createIntBuffer(1)
        stbi_set_flip_vertically_on_load(flipOnLoad)
        val image = stbi_load_from_memory(buffer, width, height, channels, 4) // Force 4 channels
            ?: throw IllegalStateException("Could not decode texture data: ${stbi_failure_reason()}")
        val texture = Texture(width.get(0), height.get(0), 1, 4, flipOnLoad)
        uploadToGPU(texture, image)

        return texture
    }

    private fun uploadToGPU(texture: Texture, pixels: ByteBuffer) { // Must be called on the GL thread
        try {
            texture.rhi?.close()
            val rhi = device().createTexture(
                TextureDesc(
                    TextureType.TEXTURE_2D, PixelFormat.RGBA8, texture.width, texture.height,
                    mipLevels = TextureDesc.fullMipChain(texture.width, texture.height), sampler = SAMPLER,
                ),
            )
            rhi.upload(0, 0, pixels)
            rhi.generateMips()
            texture.rhi = rhi
            texture.texId = rhi.nativeHandle.toInt()
        } finally {
            stbi_image_free(pixels)
        }
    }

    private companion object {
        /** Mipmapped, repeating: what every loaded image texture uses (TextureQualitySettings may change it). */
        val SAMPLER = SamplerDesc(minFilter = Filter.LINEAR_MIPMAP_LINEAR, magFilter = Filter.LINEAR, wrap = Wrap.REPEAT)
    }
}