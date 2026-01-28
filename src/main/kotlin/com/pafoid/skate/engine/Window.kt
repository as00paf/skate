package com.pafoid.skate.engine

import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.utils.JobSystem.runOnMain
import com.pafoid.skate.engine.utils.SettingsManager
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import org.lwjgl.opengl.GLUtil
import org.lwjgl.system.MemoryUtil.NULL

class Window(
    val width: Int = 1920,
    val height: Int = 1080,
    val initCallback: suspend (imguiLayer: ImGuiLayer) -> Unit,
    val drawCallback: (dt: Float, imguiLayer: ImGuiLayer) -> Unit,
    val destroyCallback: () -> Unit,
    val title: String
) {

    companion object {
        private var instance: Window? = null
        fun getImGuiLayer(): ImGuiLayer = instance!!.imGuiLayer
        // Corrected getFrameBuffer signature to be nullable
        fun getFrameBuffer(): com.pafoid.skate.engine.render.FrameBuffer? = instance!!.frameBuffer

        val currentWidth: Int
            get() = instance?.currentWidth ?: 1920
        val currentHeight: Int
            get() = instance?.currentHeight ?: 1080

        fun show() {
            instance?.let {
                glfwShowWindow(it.glfwWindow)
                glfwFocusWindow(it.glfwWindow)
                glfwRequestWindowAttention(it.glfwWindow)
            }
        }

        fun setFullscreen(enabled: Boolean) {
            instance?.let { win ->
                val monitor = glfwGetPrimaryMonitor()
                val vidMode = glfwGetVideoMode(monitor) ?: return
                if (enabled) {
                    glfwSetWindowMonitor(win.glfwWindow, monitor, 0, 0, vidMode.width(), vidMode.height(), vidMode.refreshRate())
                } else {
                    glfwSetWindowMonitor(win.glfwWindow, NULL, 100, 100, win.width, win.height, GLFW_DONT_CARE)
                }
            }
        }

        fun setVSync(enabled: Boolean) {
            glfwSwapInterval(if (enabled) 1 else 0)
            // Corrected call to use instance and ensure initCallback runs on main thread
            runOnMain {
                instance?.let { win ->
                    win.initCallback(win.imGuiLayer)
                }
            }
        }
    }

    var currentWidth = width
    var currentHeight = height

    private var glfwWindow: Long = -1L
    private val imGuiLayer by lazy { ImGuiLayer() }
    private var frameBuffer: com.pafoid.skate.engine.render.FrameBuffer? = null
    private var initCallbackToRun: (suspend (ImGuiLayer) -> Unit)? = null
    private var initCallbackExecuted = false
    private var isFirstDraw = true
    
    private val headless = System.getProperty("skate.headless") == "true"

    private val openGLDebug = GLFW_FALSE

    init {
        instance = this
        initCallbackToRun = initCallback
    }

    fun run() {
        init()
        loop()
    }

    private fun init() {
        SettingsManager.load()
        val settings = SettingsManager.settings

        // Error callback
        GLFWErrorCallback.createPrint(System.err).set()

        if (!glfwInit()) throw IllegalStateException("Unable to initialize GLFW.")

        // Configure GLFW
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE)
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE)
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, openGLDebug)
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
        glfwSetWindowPos(glfwWindow, 0, 0) // Corrected ypos to integer 0
        
        currentWidth = winWidth
        currentHeight = winHeight

        // Make OpenGL context current
        glfwMakeContextCurrent(glfwWindow)

        // Enable v-sync
        glfwSwapInterval(if (settings.vsync) 1 else 0)

        // This is needed for OpenGL
        GL.createCapabilities()
        if(openGLDebug == GLFW_TRUE) {
            GLUtil.setupDebugMessageCallback(System.err)
        }

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)


        installCallbacks()
        com.pafoid.skate.engine.controls.JoystickListener.init()

        imGuiLayer.init(glfwWindow)
        
        
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
            com.pafoid.skate.engine.utils.JobSystem.update()
            
            // Record high-frequency input
            com.pafoid.skate.engine.controls.InputBuffer.push(
                Time.getTime(),
                org.joml.Vector2f(MouseListener.getX(), MouseListener.getY()),
                com.pafoid.skate.engine.controls.JoystickListener.getAxes(GLFW_JOYSTICK_1)
            )
            
            // --- Defered Initialization Logic ---
            if (isFirstDraw) {
                // Create FrameBuffer and set viewport if not already initialized
                if (frameBuffer == null) {
                    frameBuffer = com.pafoid.skate.engine.render.FrameBuffer(currentWidth, currentHeight)
                    glViewport(0, 0, currentWidth, currentHeight)
                }
                // Execute the deferred initCallback once
                initCallbackToRun?.let { callback ->
                    if (!initCallbackExecuted) {
                        // Use JobSystem.runOnMain to ensure it runs on the main dispatcher
                        // which is processed by JobSystem.update() in the loop.
                        // Explicitly capture imGuiLayer from the enclosing Window instance.
                        val currentImGuiLayer = this.imGuiLayer
                        runOnMain {
                            callback(currentImGuiLayer) 
                        }
                        initCallbackExecuted = true
                    }
                }
                isFirstDraw = false
            }
            // --- End Defered Initialization Logic ---

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