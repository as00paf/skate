package com.pafoid.skate.engine.utils.testing

import org.lwjgl.opengl.GL11.*
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer

object FrameCapture {

    /**
     * Captures the currently bound framebuffer and saves it to a PNG file.
     * @param width The width of the viewport.
     * @param height The height of the viewport.
     * @param filePath The path to save the PNG file.
     */
    fun capture(width: Int, height: Int, filePath: String) {
        val components = 4 // RGBA
        val buffer: ByteBuffer = MemoryUtil.memAlloc(width * height * components)
        
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
        
        // Flip the image vertically because OpenGL's origin is at the bottom-left
        val flippedBuffer: ByteBuffer = MemoryUtil.memAlloc(width * height * components)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcPos = (y * width + x) * components
                val destPos = ((height - 1 - y) * width + x) * components
                
                flippedBuffer.put(destPos, buffer.get(srcPos))
                flippedBuffer.put(destPos + 1, buffer.get(srcPos + 1))
                flippedBuffer.put(destPos + 2, buffer.get(srcPos + 2))
                flippedBuffer.put(destPos + 3, buffer.get(srcPos + 3))
            }
        }

        val parentDir = File(filePath).parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        stbi_write_png(filePath, width, height, components, flippedBuffer, width * components)
        
        MemoryUtil.memFree(buffer)
        MemoryUtil.memFree(flippedBuffer)
    }
}
