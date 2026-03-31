package com.pafoid.skate.editor.ui.imgui.windows.components

import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.Renderer
import imgui.ImGui
import imgui.ImVec2

/**
 * Renders the game viewport image and manages framebuffer synchronization.
 * 
 * This component handles:
 * - Rendering the framebuffer texture to the ImGui window
 * - Tracking viewport screen position and size
 * - Synchronizing framebuffer and camera dimensions
 * 
 * @param renderer The renderer providing the framebuffer texture
 * @param sceneManager For accessing current scene and camera
 */
class ViewportRenderer(
    private val renderer: Renderer,
    private val sceneManager: SceneManager
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
        val screenPos = ImVec2()
        ImGui.getCursorScreenPos(screenPos)
        imageScreenPosX = screenPos.x
        imageScreenPosY = screenPos.y
        imageSizeX = windowSize.x
        imageSizeY = windowSize.y
        
        val texId = renderer.frameBuffer.getTextureId()
        ImGui.image(texId.toLong(), imageSizeX, imageSizeY, 0f, 1f, 1f, 0f)
    }
    
    /**
     * Updates the framebuffer and camera to match the current viewport dimensions.
     * 
     * Must be called every frame to handle window resizing.
     */
    fun updateFramebuffer() {
        val fbWidth = imageSizeX.toInt()
        val fbHeight = imageSizeY.toInt()
        
        // Skip if dimensions are invalid or unchanged
        if (fbWidth <= 0 || fbHeight <= 0) return
        
        val currentFb = renderer.frameBuffer
        if (currentFb.width != fbWidth || currentFb.height != fbHeight) {
            renderer.resize(fbWidth, fbHeight)
        }
        
        // Sync camera viewport dimensions for correct aspect ratio
        sceneManager.currentScene?.camera?.let { camera ->
            camera.viewportWidth = fbWidth
            camera.viewportHeight = fbHeight
        }
    }
    
    /**
     * Get the viewport screen position as ImVec2.
     */
    fun getScreenPos(): ImVec2 = ImVec2(imageScreenPosX, imageScreenPosY)
    
    /**
     * Get the viewport size as ImVec2.
     */
    fun getSize(): ImVec2 = ImVec2(imageSizeX, imageSizeY)
}
