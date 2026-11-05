package com.pafoid.skate.engine.utils.testing

import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer

object FrameCapture {

    /**
     * Captures color attachment 0 of [target] (null: the window's back buffer) and saves it to a PNG file.
     * @param width The width of the captured area.
     * @param height The height of the captured area.
     * @param filePath The path to save the PNG file.
     */
    fun capture(width: Int, height: Int, filePath: String, target: RhiRenderTarget? = null, device: RhiDevice = RhiContext.require()) {
        val components = 4 // RGBA
        val buffer: ByteBuffer = MemoryUtil.memAlloc(width * height * components)
        
        device.readPixels(target, 0, IntRect(0, 0, width, height), PixelFormat.RGBA8, buffer)
        
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
