package com.pafoid.skate.engine.assets

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R
import org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP
import org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X
import org.lwjgl.stb.STBImage.*

class Cubemap {
    private var id: Int = -1

    fun init(filePaths: Array<String>): Cubemap {
        id = glGenTextures()
        glBindTexture(GL_TEXTURE_CUBE_MAP, id)

        for (i in filePaths.indices) {
            val width = BufferUtils.createIntBuffer(1)
            val height = BufferUtils.createIntBuffer(1)
            val channels = BufferUtils.createIntBuffer(1)
            stbi_set_flip_vertically_on_load(false)
            val image = stbi_load(filePaths[i], width, height, channels, 0)

            if (image != null) {
                val format = if (channels.get(0) == 4) GL_RGBA else GL_RGB
                glTexImage2D(
                    GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                    0, format, width.get(0), height.get(0), 0, format, GL_UNSIGNED_BYTE, image
                )
                stbi_image_free(image)
            } else {
                assert(false) { "Error: (Cubemap) Unable to load image: ${filePaths[i]}" }
            }
        }

        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE)

        return this
    }

    fun bind() {
        glBindTexture(GL_TEXTURE_CUBE_MAP, id)
    }

    fun unbind() {
        glBindTexture(GL_TEXTURE_CUBE_MAP, 0)
    }

    fun getId() = id
}