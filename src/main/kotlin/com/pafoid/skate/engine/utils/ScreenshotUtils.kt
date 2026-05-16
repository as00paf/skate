package com.pafoid.skate.engine.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date

object ScreenshotUtils {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun takeScreenshot(width: Int, height: Int, fboId: Int) {
        val screenshotsDir = "screenshots"
        File(screenshotsDir).mkdirs()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
        val fileName = "screenshot_${dateFormat.format(Date())}.png"
        val filePath = "$screenshotsDir/$fileName"

        val pixels = BufferUtils.createByteBuffer(width * height * 4)
        
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)

        // Offload the heavy lifting to a background thread
        scope.launch {
            // Flip the image vertically (OpenGL reads from bottom-left)
            val flippedPixels = BufferUtils.createByteBuffer(width * height * 4)
            for (y in 0 until height) {
                val originalRowIndex = y * width * 4
                val flippedRowIndex = (height - 1 - y) * width * 4
                for (x in 0 until width * 4) {
                    flippedPixels.put(flippedRowIndex + x, pixels.get(originalRowIndex + x))
                }
            }

            if (stbi_write_png(filePath, width, height, 4, flippedPixels, width * 4)) {
                // UI interaction must be carefully handled if TinyFileDialogs blocks or needs specific thread
                // TinyFileDialogs is usually thread-safe and creates its own window.
                showScreenshotPopup(filePath)
            } else {
                TinyFileDialogs.tinyfd_messageBox("Screenshot Failed", "Failed to save screenshot.", "ok", "error", true)
            }
        }
    }

    private fun showScreenshotPopup(filePath: String) {
        TinyFileDialogs.tinyfd_messageBox(
            "Screenshot Saved",
            "Screenshot saved to:\n$filePath",
            "ok",
            "info",
            true
        )
    }
}
