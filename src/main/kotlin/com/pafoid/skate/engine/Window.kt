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

        val currentWidth: Int
            get() = instance?.currentWidth ?: 1920
        val currentHeight: Int
            get() = instance?.currentHeight ?: 1080
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
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE)
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
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

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
        glfwSetCursorPosCallback(glfwWindow, com.pafoid.skate.engine.controls.MouseListener::mousePosCallback)
        glfwSetMouseButtonCallback(glfwWindow, com.pafoid.skate.engine.controls.MouseListener::mouseButtonCallback)
        glfwSetScrollCallback(glfwWindow, com.pafoid.skate.engine.controls.MouseListener::mouseScrollCallback)
        glfwSetKeyCallback(glfwWindow, com.pafoid.skate.engine.controls.KeyListener::keyCallback)
    }

    private fun loop() {
        var beginTime = Time.getTime()
        var endTime: Float
        var dt = -1.0f

        while (!glfwWindowShouldClose(glfwWindow)) {
            glfwPollEvents()
            
            // Record high-frequency input
            com.pafoid.skate.engine.controls.InputBuffer.push(
                Time.getTime(),
                org.joml.Vector2f(MouseListener.getX(), MouseListener.getY()),
                com.pafoid.skate.engine.controls.JoystickListener.getAxes(GLFW_JOYSTICK_1)
            )

            drawCallback(dt, imGuiLayer)

            glfwSwapBuffers(glfwWindow)

            KeyListener.endFrame()
            MouseListener.endFrame()

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