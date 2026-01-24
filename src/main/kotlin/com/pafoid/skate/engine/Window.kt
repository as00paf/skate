package com.pafoid.skate.engine

import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.controls.MouseListener
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil.NULL

class Window(
    val width: Int = 1920,
    val height: Int = 1080,
    val initCallback: (imguiLayer: ImGuiLayer) -> Unit,
    val drawCallback: (dt: Float, imguiLayer: ImGuiLayer) -> Unit,
    val destroyCallback: () -> Unit,
    val title: String
) {

    companion object {
        private var instance: Window? = null
        fun getImGuiLayer(): ImGuiLayer = instance!!.imGuiLayer
        fun getFrameBuffer(): com.pafoid.skate.engine.render.FrameBuffer = instance!!.frameBuffer
    }

    var currentWidth = width
    var currentHeight = height

    private var glfwWindow: Long = -1L
    private val imGuiLayer = ImGuiLayer()
    private lateinit var frameBuffer: com.pafoid.skate.engine.render.FrameBuffer

    init {
        instance = this
    }

    fun run() {
        init()
        loop()
    }

    private fun init() {
        // Error callback
        GLFWErrorCallback.createPrint(System.err).set()

        if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW.")

        // Configure GLFW
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE)
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE) // Remove borders for windowed fullscreen
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)

        // Get the primary monitor and its video mode
        val primaryMonitor = glfwGetPrimaryMonitor()
        val videoMode = glfwGetVideoMode(primaryMonitor)
        
        val winWidth = videoMode?.width() ?: width
        val winHeight = videoMode?.height() ?: height

        // Create the window (passing NULL for monitor makes it windowed)
        glfwWindow = glfwCreateWindow(winWidth, winHeight, title, NULL, NULL)
        if (glfwWindow == NULL) throw IllegalStateException("Unable to create the GLFW window.")
        
        // Center/Position at 0,0
        glfwSetWindowPos(glfwWindow, 0, 0)
        
        currentWidth = winWidth
        currentHeight = winHeight

        // Make the OpenGL context current
        glfwMakeContextCurrent(glfwWindow)

        // Enable v-sync
        glfwSwapInterval(1)

        // Make window visible
        glfwShowWindow(glfwWindow)

        // This is needed for OpenGL
        GL.createCapabilities()

        glEnable(GL_BLEND)
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

        frameBuffer = com.pafoid.skate.engine.render.FrameBuffer(1920, 1080)
        glViewport(0, 0, width, height)

        installCallbacks()
        com.pafoid.skate.engine.controls.JoystickListener.init()

        imGuiLayer.init(glfwWindow)
        initCallback(imGuiLayer)
    }

    private fun installCallbacks() {
        glfwSetWindowSizeCallback(glfwWindow) { w: Long, newWidth: Int, newHeight: Int ->
            currentWidth = newWidth
            currentHeight = newHeight
            glViewport(0, 0, newWidth, newHeight)
        }
    }

    private fun loop() {
        var beginTime = Time.getTime()
        var endTime: Float
        var dt = -1.0f

        while (!glfwWindowShouldClose(glfwWindow)) {
            glfwPollEvents()

            drawCallback(dt, imGuiLayer)

            glfwSwapBuffers(glfwWindow)

            endTime = Time.getTime()
            dt = endTime - beginTime
            beginTime = endTime
        }

        destroy()
    }

    private fun destroy() {
        imGuiLayer.destroy()
        destroyCallback()

        // Free memory
        glfwFreeCallbacks(glfwWindow)
        glfwDestroyWindow(glfwWindow)

        // Terminate GLFW and free the error callback
        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }
}