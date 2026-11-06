package com.pafoid.skate.engine.core

import org.lwjgl.BufferUtils
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.GLFW_DECORATED
import org.lwjgl.glfw.GLFW.GLFW_DONT_CARE
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_DEBUG_CONTEXT
import org.lwjgl.glfw.GLFW.GLFW_RESIZABLE
import org.lwjgl.glfw.GLFW.GLFW_SAMPLES
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDefaultWindowHints
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwFocusWindow
import org.lwjgl.glfw.GLFW.glfwGetMonitorPos
import org.lwjgl.glfw.GLFW.glfwGetMonitors
import org.lwjgl.glfw.GLFW.glfwGetVideoMode
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwPollEvents
import org.lwjgl.glfw.GLFW.glfwRequestWindowAttention
import org.lwjgl.glfw.GLFW.glfwSetErrorCallback
import org.lwjgl.glfw.GLFW.glfwSetWindowIcon
import org.lwjgl.glfw.GLFW.glfwSetWindowPos
import org.lwjgl.glfw.GLFW.glfwShowWindow
import org.lwjgl.glfw.GLFW.glfwSwapBuffers
import org.lwjgl.glfw.GLFW.glfwSwapInterval
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.glfw.GLFW.GLFW_CLIENT_API
import org.lwjgl.glfw.GLFW.GLFW_NO_API
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.engine.render.rhi.vulkan.VulkanDevice
import org.lwjgl.glfw.GLFW.glfwWindowShouldClose
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.stb.STBImage
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer

class Window(
    initialWidth: Int = 1920,
    initialHeight: Int = 1080,
    val title: String,
    val iconPath: String? = null,
    val icon: ByteArray? = null,
    /**
     * Graphics API of the window (GPL-19). [GraphicsBackend.VULKAN] creates a window without a GL context and a
     * [VulkanDevice] presenting to it, and throws (after destroying the window) when Vulkan cannot start, so the caller
     * can fall back to OpenGL. The editor always uses OpenGL.
     */
    val backend: GraphicsBackend = GraphicsBackend.OPENGL,
) {
    var glfwWindow: Long = -1L
    var width: Int = 1920
    var height: Int = 1080

    var windowController: WindowController
        private set

    private val openGLDebug = GLFW_FALSE

    /** The RHI device of this window's context, created once the context is current. */
    lateinit var device: RhiDevice
        private set

    init {
        // Error callback
        GLFWErrorCallback.createPrint(System.err).set()

        if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW.")

        // Configure GLFW
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)

        if (backend == GraphicsBackend.VULKAN) {
            if (!glfwVulkanSupported()) {
                glfwTerminate()
                throw IllegalStateException("No Vulkan loader or driver (glfwVulkanSupported is false)")
            }
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        } else {
            // MSAA Support
            glfwWindowHint(GLFW_SAMPLES, 4)

            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, openGLDebug)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        }

        // Monitor and Video Mode discovery
        val monitors = glfwGetMonitors() ?: throw IllegalStateException("No monitors found")
        val monitor = monitors.get(0)
        val videoMode = glfwGetVideoMode(monitor)

        // Store window dimensions
        width = initialWidth
        height = initialHeight

        // Create the window
        glfwWindow = glfwCreateWindow(width, height, title, NULL, NULL)
        if (glfwWindow == NULL) throw IllegalStateException("Unable to create the GLFW window.")

        // Enforce Minimum Window Size Constraints
        GLFW.glfwSetWindowSizeLimits(glfwWindow, 1024, 768, GLFW_DONT_CARE, GLFW_DONT_CARE)

        // Center on monitor
        val xPos = IntArray(1)
        val yPos = IntArray(1)
        glfwGetMonitorPos(monitor, xPos, yPos)
        val monitorWidth = videoMode?.width() ?: 1920
        val monitorHeight = videoMode?.height() ?: 1080
        glfwSetWindowPos(glfwWindow, xPos[0] + (monitorWidth - width) / 2, yPos[0] + (monitorHeight - height) / 2)

        windowController = WindowController(glfwWindow)

        // Maximize window on startup
        windowController.maximize()

        // Set the window icon
        iconPath?.let { setWindowIcon(it) }
        icon?.let { setWindowIcon(it) }

        device = if (backend == GraphicsBackend.VULKAN) {
            // Surface and swapchain on this no-API window (VK-01); presents in endFrame.
            try {
                VulkanDevice.create(glfwWindow)
            } catch (e: Throwable) {
                destroy()
                throw e
            }
        } else {
            // Make OpenGL context current
            glfwMakeContextCurrent(glfwWindow)

            // Disable v-sync
            glfwSwapInterval(0)

            // Graphics device of this context (REN-12): loads GL and sets the initial blend, cube map and MSAA state
            OpenGlDevice.create(debugOutput = if (openGLDebug == GLFW_TRUE) System.err else null)
        }

        GLFW.glfwSetWindowMaximizeCallback(glfwWindow) { _, maximized ->
            if (windowController.isFixingMaximize) return@glfwSetWindowMaximizeCallback

            windowController.setLogicallyMaximized(maximized)
            if (maximized) {
                GLFW.glfwSetWindowAttrib(glfwWindow, GLFW_DECORATED, GLFW_FALSE)
                windowController.isFixingMaximize = true
            } else {
                GLFW.glfwSetWindowAttrib(glfwWindow, GLFW_DECORATED, GLFW_TRUE)
                val backupWindowPtr = GLFW.glfwGetCurrentContext()
                glfwMakeContextCurrent(backupWindowPtr)
            }
        }
    }

    private fun setWindowIcon(iconPath: String) {
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            val comp = stack.mallocInt(1)

            // Ensure flip vertical is disabled for UI images
            STBImage.stbi_set_flip_vertically_on_load(false)
            val pixels: ByteBuffer? = STBImage.stbi_load(iconPath, w, h, comp, 4) // Force 4 channels (RGBA)
            STBImage.stbi_set_flip_vertically_on_load(true) // Re-enable for other textures

            if (pixels == null) {
                throw IllegalStateException("Failed to load image at path: $iconPath")
            }

            val icon = GLFWImage.malloc(stack)
            icon.width(w.get(0))
            icon.height(h.get(0))
            icon.pixels(pixels)

            val icons = GLFWImage.malloc(1, stack)
            icons.put(0, icon)

            glfwSetWindowIcon(glfwWindow, icons)

            STBImage.stbi_image_free(pixels) // Free the image data
        }
    }

    fun setWindowIcon(icon: ByteArray) {
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            val channels = stack.mallocInt(1)

            val data = BufferUtils.createByteBuffer(1024 * 1024 * 4)
            data.put(icon).position(0)

            // Ensure flip vertical is disabled for UI images
            STBImage.stbi_set_flip_vertically_on_load(false)
            val pixels: ByteBuffer? = stbi_load_from_memory(data, w, h, channels, 4)
            STBImage.stbi_set_flip_vertically_on_load(true) // Re-enable for other textures

            if (pixels == null) {
                throw IllegalStateException("Failed to load image at path: $iconPath")
            }

            val icon = GLFWImage.malloc(stack)
            icon.width(w.get(0))
            icon.height(h.get(0))
            icon.pixels(pixels)

            val icons = GLFWImage.malloc(1, stack)
            icons.put(0, icon)

            glfwSetWindowIcon(glfwWindow, icons)

            STBImage.stbi_image_free(pixels) // Free the image data
        }
    }

    private fun loop(updateCallback: (Float) -> Unit, onClose: () -> Unit) {
        var beginTime = Time.getTime()
        var endTime: Float
        var dt = 0.0f

        while (!glfwWindowShouldClose(glfwWindow)) {
            glfwPollEvents()

            if (windowController.isFixingMaximize) {
                windowController.fixMaximizeBounds()
                windowController.isFixingMaximize = false
            }

            updateCallback(dt)
            // Vulkan submits and presents the frame here; OpenGL's endFrame is a no-op and the swap presents.
            device.endFrame()
            if (backend == GraphicsBackend.OPENGL) glfwSwapBuffers(glfwWindow)

            endTime = Time.getTime()
            dt = endTime - beginTime
            beginTime = endTime
        }

        // Release what uses the device (the engine) before the device and its window go away.
        onClose()
        destroy()
    }

    /** Runs [updateCallback] every frame until the window closes; [onClose] runs while the device is still alive. */
    fun show(onClose: () -> Unit = {}, updateCallback: (Float) -> Unit) {
        glfwShowWindow(glfwWindow)
        glfwFocusWindow(glfwWindow)
        glfwRequestWindowAttention(glfwWindow)
        loop(updateCallback, onClose)
    }

    private fun destroy() {
        // The Vulkan surface belongs to the window: release the device first.
        if (backend == GraphicsBackend.VULKAN && this::device.isInitialized) runCatching { device.close() }
        glfwFreeCallbacks(glfwWindow)
        glfwDestroyWindow(glfwWindow)

        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }
}
