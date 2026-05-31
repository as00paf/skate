package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.utils.Time
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
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
import org.lwjgl.glfw.GLFW.glfwGetFramebufferSize
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
import org.lwjgl.glfw.GLFW.glfwWindowShouldClose
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import org.lwjgl.opengl.GLUtil
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer

class Window(
    val width: Int = 1920,
    val height: Int = 1080,
    val title: String,
    val windowIcon: String? = null
) {
    var glfwWindow: Long = -1L
    private var isFirstDraw = true
    private var windowWidth: Int = 1920
    private var windowHeight: Int = 1080

    var windowController: WindowController
        private set

    private val openGLDebug = GLFW_FALSE

    init {
        // Error callback
        GLFWErrorCallback.createPrint(System.err).set()

        if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW.")

        // Configure GLFW
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)

        // MSAA Support
        glfwWindowHint(GLFW_SAMPLES, 4)

        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, openGLDebug)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)

        // Monitor and Video Mode discovery
        val monitors = glfwGetMonitors() ?: throw IllegalStateException("No monitors found")
        val monitor = monitors.get(0)
        val videoMode = glfwGetVideoMode(monitor)

        // Use settings or fallback to monitor defaults
        val winWidth = width
        val winHeight = height

        // Store window dimensions
        windowWidth = winWidth
        windowHeight = winHeight

        // Create the window
        glfwWindow = glfwCreateWindow(winWidth, winHeight, title, NULL, NULL)
        if (glfwWindow == NULL) throw IllegalStateException("Unable to create the GLFW window.")

        // Enforce Minimum Window Size Constraints
        GLFW.glfwSetWindowSizeLimits(glfwWindow, 1024, 768, GLFW_DONT_CARE, GLFW_DONT_CARE)

        // Center on monitor
        val xPos = IntArray(1)
        val yPos = IntArray(1)
        glfwGetMonitorPos(monitor, xPos, yPos)
        val monitorWidth = videoMode?.width() ?: 1920
        val monitorHeight = videoMode?.height() ?: 1080
        glfwSetWindowPos(glfwWindow, xPos[0] + (monitorWidth - winWidth) / 2, yPos[0] + (monitorHeight - winHeight) / 2)

        windowController = WindowController(glfwWindow)

        // Maximize window on startup
        windowController.maximize()

        // Get the maximized framebuffer size
        val fbSizeWidth = IntArray(1)
        val fbSizeHeight = IntArray(1)
        glfwGetFramebufferSize(glfwWindow, fbSizeWidth, fbSizeHeight)
        windowWidth = fbSizeWidth[0]
        windowHeight = fbSizeHeight[0]

        // Set the window icon
        windowIcon?.let { setWindowIcon(it) }

        // Make OpenGL context current
        glfwMakeContextCurrent(glfwWindow)

        // Disable v-sync
        glfwSwapInterval(0)

        // This is needed for OpenGL
        GL.createCapabilities()
        if(openGLDebug == GLFW_TRUE) {
            GLUtil.setupDebugMessageCallback(System.err)
        }

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)

        // Enable MSAA if configured
        glEnable(GL13.GL_MULTISAMPLE)

        // Set initial viewport for maximized window
        glViewport(0, 0, windowWidth, windowHeight)

        GLFW.glfwSetWindowMaximizeCallback(glfwWindow) { _, maximized ->
            if (windowController.isFixingMaximize) return@glfwSetWindowMaximizeCallback

            windowController.setLogicallyMaximized(maximized)
            if (maximized) {
                GLFW.glfwSetWindowAttrib(glfwWindow, GLFW_DECORATED, GLFW_FALSE)
                windowController.isFixingMaximize = true
            } else {
                GLFW.glfwSetWindowAttrib(glfwWindow, GLFW_DECORATED, GLFW_TRUE)
                val backupWindowPtr = GLFW.glfwGetCurrentContext()
                GLFW.glfwMakeContextCurrent(backupWindowPtr)
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

    private fun loop(updateCallback: (Float) -> Unit) {
        var beginTime = Time.getTime()
        var endTime: Float
        var dt = 0.0f

        while (!glfwWindowShouldClose(glfwWindow)) {
            glfwPollEvents()

            if (windowController.isFixingMaximize) {
                windowController.fixMaximizeBounds()
                windowController.isFixingMaximize = false
            }

            if (isFirstDraw) {
                // Set viewport if not already initialized
                val fbWidth = IntArray(1)
                val fbHeight = IntArray(1)
                glfwGetFramebufferSize(glfwWindow, fbWidth, fbHeight)
                windowWidth = fbWidth[0]
                windowHeight = fbHeight[0]
                glViewport(0, 0, windowWidth, windowHeight)
                isFirstDraw = false
            }

            updateCallback(dt)
            glfwSwapBuffers(glfwWindow)

            endTime = Time.getTime()
            dt = endTime - beginTime
            beginTime = endTime
        }

        destroy()
    }

    fun show(updateCallback: (Float) -> Unit) {
        glfwShowWindow(glfwWindow)
        glfwFocusWindow(glfwWindow)
        glfwRequestWindowAttention(glfwWindow)
        loop(updateCallback)
    }

    private fun destroy() {
        glfwFreeCallbacks(glfwWindow)
        glfwDestroyWindow(glfwWindow)

        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }
}
