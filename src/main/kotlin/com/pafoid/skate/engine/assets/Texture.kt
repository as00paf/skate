package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.scenes.components.Component
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30.*
import org.lwjgl.stb.STBImage.*
import java.util.*

class TextureData(
    val width: Int,
    val height: Int,
    val channels: Int,
    val pixels: java.nio.ByteBuffer,
    val flip: Boolean = false
) {
    fun free() {
        org.lwjgl.stb.STBImage.stbi_image_free(pixels)
    }
}

class Texture: Component() {

    var texId: Int = -1

    var width: Int = 0
    var height: Int = 0
    private var depth: Int = 0
    private var target: Int = GL_TEXTURE_2D
    var filePath: String? = null

    fun uploadToGPU(data: TextureData) {
        this.texId = glGenTextures()
        this.target = GL_TEXTURE_2D
        this.width = data.width
        this.height = data.height
        this.depth = 1

        glBindTexture(target, texId)

        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        val format = if (data.channels == 3) GL_RGB else GL_RGBA
        glTexImage2D(target, 0, format, width, height, 0, format, GL_UNSIGNED_BYTE, data.pixels)
        glGenerateMipmap(target)
    }

    fun init(width: Int, height: Int):Texture {
        this.texId = glGenTextures()
        this.width = width
        this.height = height
        this.depth = 1
        this.target = GL_TEXTURE_2D
        this.filePath = "Generated::$texId"

        glBindTexture(target, texId)

        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_REPEAT)

        glTexImage2D(target, 0, GL_RGB, width, height,
            0, GL_RGB, GL_UNSIGNED_BYTE, 0)

        return this
    }

    fun init3D(width: Int, height: Int, depth: Int, data: java.nio.ByteBuffer? = null): Texture {
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

    companion object {
        const val WHITE = "assets/textures/white.png"
        const val ASPHALT = "assets/textures/asphalt.png"
        const val CONCRETE_SIMPLE = "assets/textures/concrete_simple.png"

        fun loadData(filePath: String, flipOnLoad: Boolean = false): TextureData? {
            val width = BufferUtils.createIntBuffer(1)
            val height = BufferUtils.createIntBuffer(1)
            val channels = BufferUtils.createIntBuffer(1)
            stbi_set_flip_vertically_on_load(flipOnLoad)
            val image = stbi_load(filePath, width, height, channels, 0)
            return if (image != null) {
                TextureData(width.get(0), height.get(0), channels.get(0), image, flipOnLoad)
            } else {
                null
            }
        }

        fun loadData(buffer: java.nio.ByteBuffer, flipOnLoad: Boolean = false): TextureData? {
            val width = BufferUtils.createIntBuffer(1)
            val height = BufferUtils.createIntBuffer(1)
            val channels = BufferUtils.createIntBuffer(1)
            stbi_set_flip_vertically_on_load(flipOnLoad)
            val image = stbi_load_from_memory(buffer, width, height, channels, 0)
            return if (image != null) {
                TextureData(width.get(0), height.get(0), channels.get(0), image, flipOnLoad)
            } else {
                null
            }
        }
    }

}