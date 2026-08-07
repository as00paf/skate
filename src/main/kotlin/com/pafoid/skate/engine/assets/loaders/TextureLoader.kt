package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.assets.data.Texture
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR
import org.lwjgl.opengl.GL11.GL_REPEAT
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL30.glGenerateMipmap
import org.lwjgl.stb.STBImage.stbi_image_free
import org.lwjgl.stb.STBImage.stbi_load
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load
import java.nio.ByteBuffer

class TextureLoader() {

    fun loadFromFile(filePath: String, flipOnLoad: Boolean = false): Texture {
        val width = BufferUtils.createIntBuffer(1)
        val height = BufferUtils.createIntBuffer(1)
        val channels = BufferUtils.createIntBuffer(1)
        stbi_set_flip_vertically_on_load(flipOnLoad)
        val image = stbi_load(filePath, width, height, channels, 4) // Force 4 channels
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
        val texture = Texture(width.get(0), height.get(0), 1, 4, flipOnLoad)
        uploadToGPU(texture, image)

        return texture
    }

    private fun uploadToGPU(texture: Texture, pixels: ByteBuffer?) {// Must be called on GL thread
        texture.texId = glGenTextures()

        glBindTexture(GL_TEXTURE_2D, texture.texId)

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            texture.width,
            texture.height,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            pixels
        )
        glGenerateMipmap(GL_TEXTURE_2D)
        pixels?.let { stbi_image_free(it) }
    }
}