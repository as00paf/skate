package com.pafoid.skate.engine.core

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.input.IInputBuffer
import com.pafoid.skate.engine.input.listeners.GamepadListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.utils.JobSystem.runOnMain
import com.pafoid.skate.engine.utils.Time
import org.joml.Vector2f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.GLFW_DECORATED
import org.lwjgl.glfw.GLFW.GLFW_DONT_CARE
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
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
import org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor
import org.lwjgl.glfw.GLFW.glfwGetVideoMode
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwPollEvents
import org.lwjgl.glfw.GLFW.glfwRequestWindowAttention
import org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback
import org.lwjgl.glfw.GLFW.glfwSetErrorCallback
import org.lwjgl.glfw.GLFW.glfwSetKeyCallback
import org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback
import org.lwjgl.glfw.GLFW.glfwSetScrollCallback
import org.lwjgl.glfw.GLFW.glfwSetWindowIcon
import org.lwjgl.glfw.GLFW.glfwSetWindowMonitor
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
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import org.lwjgl.opengl.GLUtil
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer

class Window(
    val width: Int = 1920,
    val height: Int = 1080,
    val title: String
): KoinComponent {

    private val bootManager: BootManager by inject()
    private val inputBuffer: IInputBuffer by inject()
    private val joystickListener: GamepadListener by inject()
    private val keyListener: KeyListener by inject()
    private val mouseListener: MouseListener by inject()
    private val settingsManager: SettingsManager by inject()
    private val engine: Engine by inject()
    private val imGuiLayer: ImGuiLayer by inject()
    private val logger: LoggerService by inject()

    private var glfwWindow: Long = -1L
    private var isFirstDraw = true
    private var windowWidth: Int = 1920
    private var windowHeight: Int = 1080

    lateinit var windowController: WindowController
        private set

    private val openGLDebug = GLFW_FALSE
    private var isFixingMaximize = false

    fun run() {
        init()
        loop()
    }

    private fun init() {
        settingsManager.load()

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

        GLFW.glfwSetWindowMaximizeCallback(glfwWindow) { _, maximized ->
            if (isFixingMaximize) return@glfwSetWindowMaximizeCallback

            windowController.setLogicallyMaximized(maximized)
            if (maximized) {
                GLFW.glfwSetWindowAttrib(glfwWindow, GLFW_DECORATED, GLFW_FALSE)
                isFixingMaximize = true
            } else {
                GLFW.glfwSetWindowAttrib(glfwWindow, GLFW_DECORATED, GLFW_TRUE)
                imGuiLayer.onWindowDecorationChanged()
            }
        }

        // Get the maximized framebuffer size
        val fbSizeWidth = IntArray(1)
        val fbSizeHeight = IntArray(1)
        glfwGetFramebufferSize(glfwWindow, fbSizeWidth, fbSizeHeight)
        windowWidth = fbSizeWidth[0]
        windowHeight = fbSizeHeight[0]

        // Set the window icon
        setWindowIcon(Assets.Textures.APP_ICON)

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
        glEnable(org.lwjgl.opengl.GL13.GL_MULTISAMPLE)

        // Set initial viewport for maximized window
        glViewport(0, 0, windowWidth, windowHeight)

        installCallbacks()
        joystickListener.init()
        imGuiLayer.init(glfwWindow, windowController, ::setFullscreen, ::setVSync)
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
                logger.logEngine("Failed to load image at path: $iconPath", LogLevel.ERROR)
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
        glfwSetCursorPosCallback(glfwWindow, mouseListener::mousePosCallback)
        glfwSetMouseButtonCallback(glfwWindow, mouseListener::mouseButtonCallback)
        glfwSetScrollCallback(glfwWindow, mouseListener::mouseScrollCallback)
        glfwSetKeyCallback(glfwWindow, keyListener::keyCallback)
    }

    private fun loop() {
        var beginTime = Time.getTime()
        var endTime: Float
        var dt = 0.0f

        while (!glfwWindowShouldClose(glfwWindow)) {
            glfwPollEvents()

            if (isFixingMaximize) {
                windowController.fixMaximizeBounds()
                isFixingMaximize = false
            }

            joystickListener.update()
            JobSystem.update()

            // Record high-frequency input
            inputBuffer.push(
                Time.getTime(),
                Vector2f(mouseListener.getX(), mouseListener.getY()),
                joystickListener.getAxes(GLFW_JOYSTICK_1)
            )

            if (isFirstDraw) {
                // Set viewport if not already initialized
                val fbWidth = IntArray(1)
                val fbHeight = IntArray(1)
                glfwGetFramebufferSize(glfwWindow, fbWidth, fbHeight)
                windowWidth = fbWidth[0]
                windowHeight = fbHeight[0]
                glViewport(0, 0, windowWidth, windowHeight)
                runOnMain {
                    show()
                    bootManager.boot(engine.engineState)
                }
                isFirstDraw = false
            }

            engine.update(dt, imGuiLayer)
            glfwSwapBuffers(glfwWindow)

            keyListener.endFrame()
            mouseListener.endFrame()

            endTime = Time.getTime()
            dt = endTime - beginTime
            beginTime = endTime
        }

        destroy()
    }

    private fun show() {
        glfwShowWindow(glfwWindow)
        glfwFocusWindow(glfwWindow)
        glfwRequestWindowAttention(glfwWindow)
    }

    fun setFullscreen(enabled: Boolean) {
        val monitor = glfwGetPrimaryMonitor()
        val vidMode = glfwGetVideoMode(monitor) ?: return
        if (enabled) {
            glfwSetWindowMonitor(glfwWindow, monitor, 0, 0, vidMode.width(), vidMode.height(), vidMode.refreshRate())
        } else {
            glfwSetWindowMonitor(glfwWindow, NULL, 100, 100, width, height, GLFW_DONT_CARE)
        }
    }

    fun setVSync(enabled: Boolean) {
        glfwSwapInterval(if (enabled) 1 else 0)
    }

    private fun destroy() {
        imGuiLayer.destroy()
        engine.destroy()

        glfwFreeCallbacks(glfwWindow)
        glfwDestroyWindow(glfwWindow)

        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }
}
