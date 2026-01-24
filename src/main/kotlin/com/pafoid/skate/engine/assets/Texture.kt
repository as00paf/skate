package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.scenes.components.Component
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.stb.STBImage.*
import java.util.*

class Texture: Component() {

    @Transient private var id: Int = -1

    private var width: Int = 0
    private var height: Int = 0
    private var filePath: String? = null

    fun init(width: Int, height: Int):Texture {
        this.id = glGenTextures()
        this.width = width
        this.height = height
        this.filePath = "Generated::$id"

        glBindTexture(GL_TEXTURE_2D, id)

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height,
            0, GL_RGB, GL_UNSIGNED_BYTE, 0)

        return this
    }

    fun init(filePath: String, flipOnLoad:Boolean = false):Texture {
        this.filePath = filePath
        this.id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)

        // Set params
        // Repeat in both directions
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
        // Pixelate on stretch
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        // Pixelate on shrink
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)

        val width = BufferUtils.createIntBuffer(1)
        val height = BufferUtils.createIntBuffer(1)
        val channels = BufferUtils.createIntBuffer(1)
        stbi_set_flip_vertically_on_load(flipOnLoad)
        val image = stbi_load(filePath, width, height, channels, 0)

        if (image != null) {
            this.width = width.get(0)
            this.height = height.get(0)
            if(channels.get(0) == 3) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width.get(0), height.get(0), 0, GL_RGB, GL_UNSIGNED_BYTE, image)
            }else if(channels.get(0) == 4) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image)
            }else{
                assert(false) {"Error: (Texture) Unknown number of channels : '${channels.get(0)}'"}
            }
            stbi_image_free(image)
        } else {
            assert(false) { "Error: (Texture) Unable to load image : $filePath" }
        }

        return this
    }

    fun bind() {
        glBindTexture(GL_TEXTURE_2D, id)
    }

    fun unbind() {
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    fun getWidth() = width
    fun getHeight() = height
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
    }
}