package com.pafoid.skate.engine.assets.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30.GL_LINEAR
import org.lwjgl.opengl.GL30.GL_LINEAR_MIPMAP_LINEAR
import org.lwjgl.opengl.GL30.GL_REPEAT
import org.lwjgl.opengl.GL30.GL_RGBA
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D
import org.lwjgl.opengl.GL30.GL_TEXTURE_3D
import org.lwjgl.opengl.GL30.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL30.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_R
import org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL30.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL30.glBindTexture
import org.lwjgl.opengl.GL30.glDeleteTextures
import org.lwjgl.opengl.GL30.glGenTextures
import org.lwjgl.opengl.GL30.glGenerateMipmap
import org.lwjgl.opengl.GL30.glTexImage2D
import org.lwjgl.opengl.GL30.glTexImage3D
import org.lwjgl.opengl.GL30.glTexParameteri
import org.lwjgl.stb.STBImage.stbi_image_free
import org.lwjgl.stb.STBImage.stbi_load
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load
import java.nio.ByteBuffer
import java.util.*

@Serializable
data class Texture(
    var width: Int = 0,
    var height: Int = 0,
    var depth: Int = 1,
    var channels: Int = 0,
    var flip: Boolean = false,
    var filePath: String? = null,
    @Transient var pixels: ByteBuffer? = null
) {

    @Transient var texId: Int = -1

    private var target: Int = GL_TEXTURE_2D

    fun uploadToGPU() {
        this.texId = glGenTextures()

        glBindTexture(target, texId)

        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        // Always use GL_RGBA if we forced 4 channels on load
        val format = GL_RGBA
        glTexImage2D(target, 0, format, width, height, 0, format, GL_UNSIGNED_BYTE, pixels)
        glGenerateMipmap(target)
    }

    fun init(width: Int, height: Int):Texture {
        this.texId = glGenTextures()
        this.width = width
        this.height = height
        this.depth = 1
        this.filePath = "Generated::$texId"

        glBindTexture(target, texId)

        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_REPEAT)

        glTexImage2D(target, 0, GL_RGBA, width, height,
            0, GL_RGBA, GL_UNSIGNED_BYTE, null as ByteBuffer?)

        return this
    }

    fun init3D(width: Int, height: Int, depth: Int, data: ByteBuffer? = null): Texture {
        this.texId = glGenTextures()
        this.width = width
        this.height = height
        this.depth = depth
        this.target = GL_TEXTURE_3D
        this.filePath = "Generated3D::$texId"

        glBindTexture(target, texId)

        // Noise textures for clouds usually want linear interpolation and repeating
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_WRAP_R, GL_REPEAT)

        glTexImage3D(target, 0, GL_RGBA, width, height, depth, 0, GL_RGBA, GL_UNSIGNED_BYTE, data)

        return this
    }

    fun bind() {
        glBindTexture(target, texId)
    }

    fun unbind() {
        glBindTexture(target, 0)
    }

    fun destroy() {
        glDeleteTextures(texId)
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            null -> false
            !is Texture -> false
            else -> {
                other.width == width && other.height == height && other.texId == texId && other.filePath == filePath
            }
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(texId, width, height, filePath)
    }

    fun free() {
        pixels?.let {
            stbi_image_free(it)
        }
    }

    companion object {

        fun fromFile(filePath: String, flipOnLoad: Boolean = false): Texture {
            val width = BufferUtils.createIntBuffer(1)
            val height = BufferUtils.createIntBuffer(1)
            val channels = BufferUtils.createIntBuffer(1)
            stbi_set_flip_vertically_on_load(flipOnLoad)
            val image = stbi_load(filePath, width, height, channels, 4) // Force 4 channels
            return Texture(width.get(0), height.get(0), 1, 4, flipOnLoad, filePath, image)
        }

        fun fromBuffer(buffer: ByteBuffer, flipOnLoad: Boolean = false): Texture {
            val width = BufferUtils.createIntBuffer(1)
            val height = BufferUtils.createIntBuffer(1)
            val channels = BufferUtils.createIntBuffer(1)
            stbi_set_flip_vertically_on_load(flipOnLoad)
            val image = stbi_load_from_memory(buffer, width, height, channels, 4) // Force 4 channels
            return Texture(width.get(0), height.get(0), 1, 4, flipOnLoad, pixels = image)
        }
    }

}
