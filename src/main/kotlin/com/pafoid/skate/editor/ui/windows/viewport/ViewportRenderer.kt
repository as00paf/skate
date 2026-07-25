package com.pafoid.skate.editor.ui.windows.viewport

import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.utils.ScreenshotUtils
import imgui.ImGui
import imgui.ImVec2

/**
 * Renders the game viewport image and manages framebuffer synchronization.
 *
 */
class ViewportRenderer(
    private val engine: Engine,
) {
    var imageScreenPosX = 0f
    var imageScreenPosY = 0f
    var imageSizeX = 0f
    var imageSizeY = 0f
    
    /**
     * Renders the framebuffer texture as an ImGui image.
     * 
     * Also captures the screen position and size for input handling.
     * 
     * @param windowSize The available window size for the viewport
     */
    fun render(windowSize: ImVec2) {
        val tempScreenPos = ImVec2()
        ImGui.getCursorScreenPos(tempScreenPos)
        imageScreenPosX = tempScreenPos.x
        imageScreenPosY = tempScreenPos.y
        imageSizeX = windowSize.x
        imageSizeY = windowSize.y

        val texId = engine.renderer.frameBuffer.getTextureId()
        ImGui.image(texId.toLong(), imageSizeX, imageSizeY, 0f, 1f, 1f, 0f)
    }

    fun updateFramebuffer() {
        val fbWidth = imageSizeX.toInt()
        val fbHeight = imageSizeY.toInt()
        
        // Skip if dimensions are invalid or unchanged
        if (fbWidth <= 0 || fbHeight <= 0) return

        val currentFb = engine.renderer.frameBuffer
        if (currentFb.width != fbWidth || currentFb.height != fbHeight) {
            engine.resizeFrameBuffer(fbWidth, fbHeight)
        }
    }

    fun captureScreenshot() {
        runCatching {
            val frameBuffer = engine.renderer.frameBuffer
            if (frameBuffer.width <= 0 || frameBuffer.height <= 0) return
            ScreenshotUtils.takeScreenshot(frameBuffer.width, frameBuffer.height, frameBuffer.fboId)
        }
    }
}
