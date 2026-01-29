package com.pafoid.skate.engine

import com.pafoid.skate.engine.controls.input.InputBuffer
import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.controls.listeners.JoystickListener
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.utils.JobSystem.runOnMain
import com.pafoid.skate.engine.utils.SettingsManager
import com.pafoid.skate.engine.utils.Time
import org.joml.Vector2f
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import org.lwjgl.opengl.GLUtil
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer

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

        // Set the window icon
        setWindowIcon("assets/textures/app_icon.png")

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
        JoystickListener.init()

        imGuiLayer.init(glfwWindow)
        
        
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
                System.err.println("Failed to load image at path: $iconPath")
                return
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

    private fun installCallbacks() {
        glfwSetWindowSizeCallback(glfwWindow) { w: Long, newWidth: Int, newHeight: Int ->
            currentWidth = newWidth
            currentHeight = newHeight
            glViewport(0, 0, newWidth, newHeight)
        }
        glfwSetCursorPosCallback(glfwWindow, MouseListener::mousePosCallback)
        glfwSetMouseButtonCallback(glfwWindow, MouseListener::mouseButtonCallback)
        glfwSetScrollCallback(glfwWindow, MouseListener::mouseScrollCallback)
        glfwSetKeyCallback(glfwWindow, KeyListener::keyCallback)
    }

    private fun loop() {
        var beginTime = Time.getTime()
        var endTime: Float
        var dt = 0.0f

        while (!glfwWindowShouldClose(glfwWindow)) {
            glfwPollEvents()
            JoystickListener.update()
            JobSystem.update()
            
            // Record high-frequency input
            InputBuffer.push(
                Time.getTime(),
                Vector2f(MouseListener.getX(), MouseListener.getY()),
                JoystickListener.getAxes(GLFW_JOYSTICK_1)
            )
            
            // --- Defered Initialization Logic ---
            if (isFirstDraw) {
                // Create FrameBuffer and set viewport if not already initialized
                if (frameBuffer == null) {
                    frameBuffer = FrameBuffer(currentWidth, currentHeight)
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