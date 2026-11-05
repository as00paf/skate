package com.pafoid.skate.engine.assets.data

import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBImage.stbi_image_free
import org.lwjgl.stb.STBImage.stbi_load
import org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load
import java.nio.ByteBuffer

/** Six-face sky texture (+X, -X, +Y, -Y, +Z, -Z) on the RHI device. */
class CubeMap(private val device: () -> RhiDevice = RhiContext::require) : AutoCloseable {
    var texture: RhiTexture? = null
        private set

    private class Face(val width: Int, val height: Int, val channels: Int, val pixels: ByteBuffer)

    fun init(filePaths: Array<String>): CubeMap {
        texture?.close()
        texture = null
        val faces = filePaths.map { path ->
            val width = BufferUtils.createIntBuffer(1)
            val height = BufferUtils.createIntBuffer(1)
            val channels = BufferUtils.createIntBuffer(1)
            stbi_set_flip_vertically_on_load(false)
            val image = stbi_load(path, width, height, channels, 0)
            assert(image != null) { "Error: (CubeMap) Unable to load image: $path" }
            image?.let { Face(width.get(0), height.get(0), channels.get(0), it) }
        }
        try {
            val first = faces.firstOrNull { it != null } ?: return this
            // Faces with 4 channels are RGBA, anything else is uploaded as RGB (as before REN-12)
            val format = if (first.channels == 4) PixelFormat.RGBA8 else PixelFormat.RGB8
            require(faces.all { it == null || (it.channels == 4) == (format == PixelFormat.RGBA8) }) {
                "CubeMap faces must all have the same channel count: ${filePaths.joinToString()}"
            }
            val cube = device().createTexture(TextureDesc(TextureType.CUBE, format, first.width, first.height, layers = 6, sampler = SAMPLER))
            faces.forEachIndexed { i, face -> if (face != null) cube.upload(0, i, face.pixels) }
            texture = cube
        } finally {
            faces.forEach { face -> face?.let { stbi_image_free(it.pixels) } }
        }
        return this
    }

    /** Backend handle of the cube texture, or -1 before [init]. */
    fun getId(): Int = texture?.nativeHandle?.toInt() ?: -1

    override fun close() {
        texture?.close()
        texture = null
    }

    private companion object {
        val SAMPLER = SamplerDesc(minFilter = Filter.LINEAR, magFilter = Filter.LINEAR, wrap = Wrap.CLAMP_TO_EDGE)
    }
}
