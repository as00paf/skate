package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.scenes.components.Component
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL30.*
import org.lwjgl.stb.STBImage.*
import java.util.*

class Texture: Component() {

    @Transient private var id: Int = -1

    private var width: Int = 0
    private var height: Int = 0
    private var depth: Int = 0
    private var target: Int = GL_TEXTURE_2D
    private var filePath: String? = null

    fun init(width: Int, height: Int):Texture {
        this.id = glGenTextures()
        this.width = width
        this.height = height
        this.depth = 1
        this.target = GL_TEXTURE_2D
        this.filePath = "Generated::$id"

        glBindTexture(target, id)

        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        glTexImage2D(target, 0, GL_RGB, width, height,
            0, GL_RGB, GL_UNSIGNED_BYTE, 0)

        return this
    }

    fun init3D(width: Int, height: Int, depth: Int, data: java.nio.ByteBuffer? = null): Texture {
        this.id = glGenTextures()
        this.width = width
        this.height = height
        this.depth = depth
        this.target = GL_TEXTURE_3D
        this.filePath = "Generated3D::$id"

        glBindTexture(target, id)

        // Noise textures for clouds usually want linear interpolation and clamping
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(target, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)

        glTexImage3D(target, 0, GL_RGBA, width, height, depth, 0, GL_RGBA, GL_UNSIGNED_BYTE, data)

        return this
    }

    fun init(buffer: java.nio.ByteBuffer): Texture {
        this.filePath = "Buffer::" + System.identityHashCode(buffer)
        this.id = glGenTextures()
        this.target = GL_TEXTURE_2D
        glBindTexture(target, id)

        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        val width = BufferUtils.createIntBuffer(1)
        val height = BufferUtils.createIntBuffer(1)
        val channels = BufferUtils.createIntBuffer(1)
        stbi_set_flip_vertically_on_load(false) // GLB/glTF usually don't need flip if aiProcess_FlipUVs is used
        val image = stbi_load_from_memory(buffer, width, height, channels, 0)

        if (image != null) {
            this.width = width.get(0)
            this.height = height.get(0)
            this.depth = 1
            val format = if (channels.get(0) == 3) GL_RGB else GL_RGBA
            glTexImage2D(target, 0, format, this.width, this.height, 0, format, GL_UNSIGNED_BYTE, image)
            glGenerateMipmap(target)
            stbi_image_free(image)
        } else {
            val reason = stbi_failure_reason()
            println("Error: (Texture) Unable to load image from memory: $reason")
            // Initialize with a 1x1 white texture as fallback if desired, 
            // but for now we'll just log the error.
        }
        return this
    }

    fun init(filePath: String, flipOnLoad:Boolean = false):Texture {
        this.filePath = filePath
        this.id = glGenTextures()
        this.target = GL_TEXTURE_2D
        glBindTexture(target, id)

        // Set params
        // Repeat in both directions
        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_REPEAT)
        // Linear filtering with mipmaps
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        val width = BufferUtils.createIntBuffer(1)
        val height = BufferUtils.createIntBuffer(1)
        val channels = BufferUtils.createIntBuffer(1)
        stbi_set_flip_vertically_on_load(flipOnLoad)
        val image = stbi_load(filePath, width, height, channels, 0)

        if (image != null) {
            this.width = width.get(0)
            this.height = height.get(0)
            this.depth = 1
            val format = if(channels.get(0) == 3) GL_RGB else GL_RGBA
            glTexImage2D(target, 0, format, this.width, this.height, 0, format, GL_UNSIGNED_BYTE, image)
            glGenerateMipmap(target)
            stbi_image_free(image)
        } else {
            assert(false) { "Error: (Texture) Unable to load image : $filePath" }
        }

        return this
    }

    fun bind() {
        glBindTexture(target, id)
    }

    fun unbind() {
        glBindTexture(target, 0)
    }

    fun getWidth() = width
    fun getHeight() = height
    fun getDepth() = depth
    fun getTarget() = target
    fun getFilePath() = filePath
    fun getId() = id

    override fun equals(other: Any?): Boolean {
        return when (other) {
            null -> false
            !is Texture -> false
            else -> {
                other.width == width && other.height == height && other.id == id && other.filePath == filePath
            }
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(id, width, height, filePath)
    }

    companion object {
        const val WHITE = "assets/textures/white.png"
        const val ASPHALT = "assets/textures/asphalt.png"
    }
}